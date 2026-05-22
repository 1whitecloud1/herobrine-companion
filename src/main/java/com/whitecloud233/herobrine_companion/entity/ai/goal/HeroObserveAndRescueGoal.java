package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.PlayerHealthCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.EnumSet;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroObserveAndRescueGoal extends Goal {
    private final HeroEntity hero;
    private Player targetPlayer;

    private static final int COMBAT_TIMEOUT = 100;
    public static final float RESCUE_HEALTH_THRESHOLD = 6.0F;
    private static final float RESCUE_HEALTH_RATIO = 0.30F;
    private static final float POST_RESCUE_HEALTH_RATIO = 0.35F;
    private static final String LAST_RESCUE_TIME_TAG = "LastRescueTime";

    // 10 分钟的冷却常量 (10分钟 * 60秒 * 20ticks)
    public static final int RESCUE_COOLDOWN = 12000;

    public HeroObserveAndRescueGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isOwner(Player player) {
        return this.hero.isCompanionMode() && this.hero.getOwnerUUID() != null && player.getUUID().equals(this.hero.getOwnerUUID());
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive()) {
            return false;
        }

        if (this.hero.level().getGameTime() - this.hero.getLastSummonedTime() < 40) {
            return false;
        }

        if (this.hero.getOwnerUUID() == null) return false;
        Player player = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        if (player == null || !player.isAlive()) return false;

        // 判定主人的战斗状态 (残血判定已移交至 CombatAndChallengeHandler 进行事前拦截)
        if (isInCombat(player)) {
            this.targetPlayer = player;
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.isBattleModeActive()) {
            return false;
        }
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) return false;

        boolean isCompanionOwner = isOwner(this.targetPlayer);

        if (!isCompanionOwner && this.hero.distanceToSqr(this.targetPlayer) > 576.0D) {
            return false;
        }

        return isInCombat(this.targetPlayer);
    }

    private boolean isInCombat(Player player) {
        int currentTick = player.tickCount;
        int lastHurtByMobTime = player.getLastHurtByMobTimestamp();
        int lastHurtMobTime = player.getLastHurtMobTimestamp();

        boolean recentlyHurt = lastHurtByMobTime > 0 && (currentTick - lastHurtByMobTime) < COMBAT_TIMEOUT;
        boolean recentlyAttacked = lastHurtMobTime > 0 && (currentTick - lastHurtMobTime) < COMBAT_TIMEOUT;

        if (!recentlyHurt && !recentlyAttacked) return false;

        LivingEntity attacker = player.getLastHurtByMob();
        LivingEntity target = player.getLastHurtMob();

        boolean hasValidAttacker = attacker != null && attacker.isAlive() && attacker.distanceToSqr(player) < 900.0D;
        boolean hasValidTarget = target != null && target.isAlive() && target.distanceToSqr(player) < 900.0D;

        return hasValidAttacker || hasValidTarget;
    }

    @Override
    public void start() {
        this.hero.getNavigation().stop();
        this.hero.setFloating(true);
        this.hero.setNoGravity(true);
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) return;
        boolean isCompanionOwner = isOwner(this.targetPlayer);

        // 1. 始终高冷注视，但移动中的头部不要和 MoveControl 抢朝向
        this.hero.lookAtEntityIfStable(this.targetPlayer, 10.0F, this.hero.getMaxHeadXRot());

        // 2. 【绝对实时的平滑排斥系统】
        double distanceSq = this.hero.distanceToSqr(this.targetPlayer);

        if (distanceSq < 144.0D) {
            Vec3 dir = this.hero.position().subtract(this.targetPlayer.position());

            if (dir.lengthSqr() < 0.001) {
                dir = new Vec3(this.hero.getRandom().nextDouble() - 0.5, 0, this.hero.getRandom().nextDouble() - 0.5);
            }
            dir = dir.normalize();

            double targetX = this.hero.getX() + dir.x * 4.0D;
            double targetZ = this.hero.getZ() + dir.z * 4.0D;
            double targetY = this.targetPlayer.getY() + 2.0D;

            double speed = 1.2D + ((144.0D - distanceSq) / 144.0D) * 1.5D;

            this.hero.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed);

        } else if (distanceSq > 400.0D && isCompanionOwner) {
            double targetY = this.targetPlayer.getY() + 2.0D;
            this.hero.getMoveControl().setWantedPosition(this.targetPlayer.getX(), targetY, this.targetPlayer.getZ(), 1.5D);

        } else {
            Vec3 currentMotion = this.hero.getDeltaMovement();
            this.hero.setDeltaMovement(currentMotion.x * 0.8, currentMotion.y * 0.8, currentMotion.z * 0.8);
        }
    }
    /**
     * 【核心修复】：将陪伴救援的伤害拦截直接收口到救援 Goal，
     * 并改为按玩家当前最大生命值计算救援线，兼容 Spice of Life 一类的加血上限模组。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        // 挑战/假死演出期间由专属逻辑接管，避免与陪伴救援互相抢事件。
        if (player.getPersistentData().getBoolean("IsChallengeActive")
                || player.getPersistentData().getBoolean("HeroFakeOutPhase")) {
            return;
        }

        PlayerHealthCompat.syncExternalMaxHealth(player);

        float finalHealth = Math.min(player.getHealth(), player.getMaxHealth()) - event.getAmount();
        if (finalHealth > getRescueTriggerHealth(player)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        long currentTime = level.getGameTime();
        HeroEntity rescuingHero = findRescuingHero(player, level, currentTime);
        if (rescuingHero == null) {
            return;
        }

        event.setCanceled(true);
        markRescueTime(rescuingHero, currentTime);
        performRescue(rescuingHero, player);
    }

    private static HeroEntity findRescuingHero(ServerPlayer player, ServerLevel level, long currentTime) {
        for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (hero.level() == level && hero.isAlive() && hero.isCompanionMode()
                    && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())
                    && canRescueNow(hero, currentTime)) {
                return hero;
            }
        }
        return null;
    }

    public static float getRescueTriggerHealth(Player player) {
        float rescueHealthPool = PlayerHealthCompat.getRescueHealthPool(player);
        float scaledThreshold = rescueHealthPool * RESCUE_HEALTH_RATIO;
        return Math.min(player.getMaxHealth(), Math.max(RESCUE_HEALTH_THRESHOLD, scaledThreshold));
    }

    private static float getPostRescueHealth(Player player) {
        float rescueHealthPool = PlayerHealthCompat.getRescueHealthPool(player);
        float scaledHealth = rescueHealthPool * POST_RESCUE_HEALTH_RATIO;
        float safeHealth = Math.max(RESCUE_HEALTH_THRESHOLD + 1.0F, scaledHealth);
        return Math.min(player.getMaxHealth(), safeHealth);
    }

    private static boolean canRescueNow(HeroEntity hero, long currentTime) {
        long lastRescue = hero.getPersistentData().getLong(LAST_RESCUE_TIME_TAG);
        return lastRescue == 0 || (currentTime - lastRescue) > RESCUE_COOLDOWN;
    }

    private static void markRescueTime(HeroEntity hero, long currentTime) {
        hero.getPersistentData().putLong(LAST_RESCUE_TIME_TAG, currentTime);
    }

    /**
     * 【核心独立救援逻辑】
     * 现在直接由 Event 拦截器调用，可以在玩家暴毙前瞬间发动
     */
    public static void performRescue(HeroEntity hero, Player targetPlayer) {
        LivingEntity attacker = targetPlayer.getLastHurtByMob();
        Vec3 dangerPos = attacker != null ? attacker.position() : targetPlayer.position();

        Vec3 safeDir = targetPlayer.position().subtract(dangerPos).normalize();
        if (safeDir.lengthSqr() == 0) {
            safeDir = new Vec3(hero.getRandom().nextDouble() - 0.5, 0, hero.getRandom().nextDouble() - 0.5).normalize();
        }

        double tpDistance = 24.0;
        double targetX = targetPlayer.getX() + safeDir.x * tpDistance;
        double targetZ = targetPlayer.getZ() + safeDir.z * tpDistance;

        BlockPos safePos = targetPlayer.level().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int)targetX, (int)targetPlayer.getY(), (int)targetZ)
        );

        targetPlayer.teleportTo(safePos.getX() + 0.5, safePos.getY() + 0.1, safePos.getZ() + 0.5);
        targetPlayer.level().playSound(null, targetPlayer.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        hero.teleportTo(safePos.getX() + 1.5, safePos.getY() + 0.1, safePos.getZ() + 1.5);
        hero.level().playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);

        targetPlayer.setLastHurtByMob(null);
        targetPlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        targetPlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));

        // 先同步 Spice of Life 一类模组的 MAX_HEALTH modifier，再基于“基础生命池”恢复到安全血线。
        PlayerHealthCompat.syncExternalMaxHealth(targetPlayer);
        targetPlayer.setHealth(getPostRescueHealth(targetPlayer));


        hero.getNavigation().stop();
    }
}
