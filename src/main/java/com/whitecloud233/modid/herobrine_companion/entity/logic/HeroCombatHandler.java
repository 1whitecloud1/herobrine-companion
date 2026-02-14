package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

public class HeroCombatHandler {

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        if (amount == Float.MAX_VALUE || amount >= 1.0E30F || Float.isInfinite(amount)) return false;

        // 2. 玩家攻击判定
        if (!hero.level().isClientSide && source.getEntity() instanceof Player player) {

            // [新增] 神经网络输入：直接攻击 Herobrine
            // 使用新的输入类型 "DIRECT_ATTACK"，避免增加怪物同情值
            hero.getHeroBrain().input(player.getUUID(), "DIRECT_ATTACK", 0.2f);

            // 攻击 Herobrine 是一种极大的负面反馈，抑制他当前的任何行为
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f); // 玩家攻击他，说明他之前的行为可能让玩家不爽，或者玩家本身很暴力

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

            boolean isCompanion = hero.isCompanionMode();
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;

            if (!isCompanion) {
                if (isEndRing) {
                    HeroDimensionHandler.teleportRandomly(hero);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.end_ring_attack"));
                } 
                else {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.attack_disappoint"));
                    
                    // [修复] 保存皮肤状态到玩家数据，以便重生时恢复
                    if (player instanceof ServerPlayer serverPlayer) {
                        CompoundTag data = serverPlayer.getPersistentData();
                        CompoundTag heroData = new CompoundTag();
                        heroData.putBoolean("UseHerobrineSkin", hero.shouldUseHerobrineSkin());
                        data.put("HeroCombatRespawnData", heroData);
                    }

                    HeroDimensionHandler.leaveWorld(hero, null);
                    
                    // [新增] 延迟 5 秒后在玩家附近重新生成
                    if (hero.level() instanceof ServerLevel serverLevel) {
                        serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 100, () -> {
                            // 检查玩家是否还在
                            if (player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() && !serverPlayer.hasDisconnected()) {
                                HeroDimensionHandler.respawnNearPlayer(serverLevel, serverPlayer);
                            }
                        }));
                    }
                }
                return false;
            }

            if (isCompanion) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_attack"));
            }
        }
        
        return false;
    }
}
