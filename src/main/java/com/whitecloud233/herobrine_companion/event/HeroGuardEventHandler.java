package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * NeoForge 1.21.1 适配版
 * 处理 Herobrine 守卫状态下的物理规则干涉 (绝对防爆、绝对防盗、绝对防破坏)
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroGuardEventHandler {

    /**
     * 神之领域 - 绝对防爆
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;

        // ================= [机制 1：免疫专属落雷伤害] =================
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof RealmBreakerLightningEntity lightningEntity) {
            event.getAffectedEntities().removeIf(entity -> entity instanceof HeroEntity);
            Entity owner = lightningEntity.getOwner();
            if (owner != null) {
                event.getAffectedEntities().remove(owner);
            }
        }

        // ================= [机制 2：神之领域 - 守卫防爆] =================
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (affectedBlocks.isEmpty() && event.getAffectedEntities().isEmpty()) return;

        Vec3 explosionPos = event.getExplosion().center();
        double ex = explosionPos.x;
        double ey = explosionPos.y;
        double ez = explosionPos.z;

        AABB searchBox = new AABB(ex - 32, ey - 32, ez - 32, ex + 32, ey + 32, ez + 32);
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 【新增】如果守卫的箱子/方块已经消失（变为空气），则自动失效，不产生粒子也不保护
                if (event.getLevel().getBlockState(guardedPos).isAir()) continue;

                double distSqrToExplosion = guardedPos.distToCenterSqr(explosionPos);

                if (distSqrToExplosion < 400.0) {
                    affectedBlocks.removeIf(pos -> pos.distSqr(guardedPos) < 100);
                    event.getAffectedEntities().removeIf(ent -> ent.distanceToSqr(guardedPos.getX(), guardedPos.getY(), guardedPos.getZ()) < 100);

                    if (event.getLevel() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                guardedPos.getX() + 0.5, guardedPos.getY() + 1.0, guardedPos.getZ() + 0.5,
                                20, 0.5, 0.5, 0.5, 0.1);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 神之领域 - 绝对禁锢 (拦截右键交互)
     */
    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;

        BlockPos clickedPos = event.getPos();

        double searchRadius = 32.0;
        AABB searchBox = new AABB(clickedPos).inflate(searchRadius);
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 【新增】如果守卫的箱子已经消失，不做保护处理
                if (event.getLevel().getBlockState(guardedPos).isAir()) continue;

                if (clickedPos.equals(guardedPos)) {
                    if (!player.getUUID().equals(hero.getOwnerUUID())) {

                        // 取消事件
                        event.setCanceled(true);

                        if (!event.getLevel().isClientSide()) {
                            ServerLevel serverLevel = (ServerLevel) event.getLevel();

                            serverLevel.playSound(null, clickedPos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.5F);
                            serverLevel.playSound(null, clickedPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.5F, 0.5F);

                            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                                    clickedPos.getX() + 0.5, clickedPos.getY() + 1.0, clickedPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * 神之领域 - 绝对壁垒 (拦截左键破坏)
     * 【新增功能】如果破坏的是 Herobrine 正在守卫的方块，且玩家不是被认可的主人，直接取消破坏行为。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isSpectator()) return;

        BlockPos brokenPos = event.getPos();
        LevelAccessor levelAccessor = event.getLevel();

        // 将 LevelAccessor 转换为 Level 进行实体查询
        if (!(levelAccessor instanceof Level level)) return;

        double searchRadius = 32.0;
        AABB searchBox = new AABB(brokenPos).inflate(searchRadius);
        List<HeroEntity> heroes = level.getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 【新增】如果守卫的箱子已经消失，不做保护处理
                if (level.getBlockState(guardedPos).isAir()) continue;

                // 判定：有玩家正在挖掘被守卫的方块
                if (brokenPos.equals(guardedPos)) {
                    if (!player.getUUID().equals(hero.getOwnerUUID())) {

                        // 抹除破坏事件
                        event.setCanceled(true);

                        // 给予和右键乱动箱子一样的神罚反馈
                        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                            serverLevel.playSound(null, brokenPos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.5F);

                            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                                    brokenPos.getX() + 0.5, brokenPos.getY() + 1.0, brokenPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
                        }
                        return;
                    }
                }
            }
        }
    }
}