package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.UUID;

public class HeroCombatHandler {
    private static final String PENDING_ATTACK_CONFIRM_PLAYER = "PendingAttackConfirmPlayer";
    private static final String PENDING_ATTACK_CONFIRM_UNTIL = "PendingAttackConfirmUntil";
    private static final long ATTACK_CONFIRM_WINDOW_TICKS = 100L;
    private static final String JUDGE_ATTACK_PLAYER_TAG = "JudgeAttackEscalationPlayer";
    private static final String JUDGE_ATTACK_COUNT_TAG = "JudgeAttackEscalationCount";
    private static final String JUDGE_ATTACK_UNTIL_TAG = "JudgeAttackEscalationUntil";
    private static final int JUDGE_ATTACK_THRESHOLD = 3;
    private static final long JUDGE_ATTACK_WINDOW_TICKS = 200L;

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        if (amount == Float.MAX_VALUE || amount >= 1.0E30F || Float.isInfinite(amount)) return false;

        // 【核心修复】：双重检查内存和硬盘数据
        boolean isChallenge = hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || hero.getPersistentData().getBoolean("IsChallengeActive");

        if (isChallenge) {
            // 如果处于挑战模式，确保内存标志是正确的 (防止同步延迟)
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
            }
            return false; // 交给原版 super.hurt 处理掉血
        }

        // 2. 玩家攻击判定
        Player player = resolvePlayerAttacker(source);
        if (!hero.level().isClientSide && player != null) {
            if (hero.isBattleModeActive()) {
                return false;
            }
            // 👇👇👇【核心修复：上次你漏掉了这里！】拦截非主人的攻击，防止夺舍漏洞
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null && !ownerUUID.equals(player.getUUID())) {
                player.sendSystemMessage(hero.createNotYourHeroMessage());
                return false;
            }
            // 👆👆👆
            if (!hasConfirmedAttackIntent(hero, player)) {
                rememberAccidentalHit(hero, player);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.accidental_attack_warning"));
                return false;
            }

            int intentionalAttackCount = registerJudgeAttack(hero, player);
            if (intentionalAttackCount >= JUDGE_ATTACK_THRESHOLD) {
                hero.getHeroBrain().input(player.getUUID(), "DIRECT_ATTACK", 0.2f);
                hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);
            }
            int currentTrust = hero.getTrustLevel();
            if (currentTrust > 0) {
                int penalty = 20;
                int newTrust = Math.max(0, currentTrust - penalty);
                hero.setTrustLevel(newTrust);
                HeroDataHandler.updateGlobalTrust(hero);

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_decrease", penalty, newTrust));

                if (hero.isCompanionMode() && newTrust < 50) {
                    hero.setCompanionMode(false);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_forced_quit"));
                }
            }


            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;

            if (!hero.isCompanionMode()) {
                if (isEndRing) {
                    HeroDimensionHandler.teleportRandomly(hero);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.end_ring_attack"));
                }
                else {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.attack_disappoint"));

                    // ============== [重构精简] 委托给状态管理器进行打包 ==============
                    if (player instanceof ServerPlayer serverPlayer) {
                        HeroStateManager.backupToPlayerNBT(hero, serverPlayer, "HeroCombatRespawnData");
                    }
                    // ==========================================================

                    HeroDimensionHandler.leaveWorld(hero, null);

                    // 延迟 5 秒后在玩家附近重新生成
                    if (hero.level() instanceof ServerLevel serverLevel) {
                        serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 100, () -> {
                            if (player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() && !serverPlayer.hasDisconnected()) {
                                HeroDimensionHandler.respawnNearPlayer(serverLevel, serverPlayer);
                            }
                        }));
                    }
                }
                return false;
            }

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_attack"));
            }

        return false;
    }

    private static boolean hasConfirmedAttackIntent(HeroEntity hero, Player player) {
        CompoundTag data = hero.getPersistentData();
        long currentGameTime = hero.level().getGameTime();
        long confirmUntil = data.getLong(PENDING_ATTACK_CONFIRM_UNTIL);
        if (confirmUntil < currentGameTime) {
            clearPendingAttackConfirmation(hero);
            return false;
        }

        return data.hasUUID(PENDING_ATTACK_CONFIRM_PLAYER)
                && player.getUUID().equals(data.getUUID(PENDING_ATTACK_CONFIRM_PLAYER));
    }

    private static void rememberAccidentalHit(HeroEntity hero, Player player) {
        CompoundTag data = hero.getPersistentData();
        data.putUUID(PENDING_ATTACK_CONFIRM_PLAYER, player.getUUID());
        data.putLong(PENDING_ATTACK_CONFIRM_UNTIL, hero.level().getGameTime() + ATTACK_CONFIRM_WINDOW_TICKS);
    }

    private static void clearPendingAttackConfirmation(HeroEntity hero) {
        CompoundTag data = hero.getPersistentData();
        data.remove(PENDING_ATTACK_CONFIRM_PLAYER);
        data.remove(PENDING_ATTACK_CONFIRM_UNTIL);
    }

    private static int registerJudgeAttack(HeroEntity hero, Player player) {
        CompoundTag data = hero.getPersistentData();
        long currentGameTime = hero.level().getGameTime();

        int attackCount = 0;
        if (data.getLong(JUDGE_ATTACK_UNTIL_TAG) >= currentGameTime
                && data.hasUUID(JUDGE_ATTACK_PLAYER_TAG)
                && player.getUUID().equals(data.getUUID(JUDGE_ATTACK_PLAYER_TAG))) {
            attackCount = data.getInt(JUDGE_ATTACK_COUNT_TAG);
        }

        attackCount = Math.min(JUDGE_ATTACK_THRESHOLD, attackCount + 1);
        data.putUUID(JUDGE_ATTACK_PLAYER_TAG, player.getUUID());
        data.putInt(JUDGE_ATTACK_COUNT_TAG, attackCount);
        data.putLong(JUDGE_ATTACK_UNTIL_TAG, currentGameTime + JUDGE_ATTACK_WINDOW_TICKS);
        return attackCount;
    }

    private static Player resolvePlayerAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }

        if (source.getEntity() instanceof Player player) {
            return player;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }

        return null;
    }
}
