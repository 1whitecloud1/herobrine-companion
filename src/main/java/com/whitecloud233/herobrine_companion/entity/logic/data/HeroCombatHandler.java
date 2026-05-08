package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class HeroCombatHandler {

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
        if (!hero.level().isClientSide && source.getEntity() instanceof Player player) {
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
            // [新增] 神经网络输入：直接攻击 Herobrine
            hero.getHeroBrain().input(player.getUUID(), "DIRECT_ATTACK", 0.2f);
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);

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
}