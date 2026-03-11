package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

public class HeroCombatHandler {

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        // 1. 虚空免疫 & 数值溢出保护
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        if (amount == Float.MAX_VALUE || amount >= 1.0E30F || Float.isInfinite(amount)) return false;

        // 2. 玩家攻击判定
        if (!hero.level().isClientSide && source.getEntity() instanceof Player player) {
            
            // [修改] 传入 playerUUID
            hero.getHeroBrain().input(player.getUUID(), "DIRECT_ATTACK", 0.2f);
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f); 

            // --- A. 信任度惩罚 (针对该玩家) ---
            // [修改] 使用 HeroWorldData 获取该玩家的信任度
            if (hero.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);
                int currentTrust = data.getTrust(player.getUUID());
                
                if (currentTrust > 0) {
                    int penalty = 20;
                    int newTrust = Math.max(0, currentTrust - penalty);
                    data.setTrust(player.getUUID(), newTrust);
                    
                    // 如果当前攻击者是 Owner，同步到实体以便显示
                    if (player.getUUID().equals(hero.getOwnerUUID())) {
                        hero.setTrustLevel(newTrust);
                    }

                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_decrease", penalty, newTrust));
                    
                    // 检查是否需要强制退出陪伴模式 (仅针对 Owner)
                    if (player.getUUID().equals(hero.getOwnerUUID()) && hero.isCompanionMode() && newTrust < 50) {
                        hero.setCompanionMode(false);
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_forced_quit"));
                    }
                }
            }

            // --- B. 离开逻辑判定 ---

            boolean isCompanion = hero.isCompanionMode();
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;

            if (!isCompanion) {
                if (isEndRing) {
                    HeroDimensionHandler.teleportRandomly(hero);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.end_ring_attack"));
                } 
                else {
                    // [新增] 保存数据到玩家身上，以便 respawnNearPlayer 恢复
                    CompoundTag data = player.getPersistentData();
                    CompoundTag heroData = new CompoundTag();
                    
                    // [关键修复] 使用新的皮肤变体和自定义名称，而不是旧的 boolean
                    heroData.putInt("SkinVariant", hero.getSkinVariant());
                    if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                        heroData.putString("CustomSkinName", hero.getCustomSkinName());
                    }
                    
                    // ============== [修复] 打包所有装备 ==============
                    heroData.put("ArmorItems", hero.getArmorItemsTag());
                    heroData.put("HandItems", hero.getHandItemsTag());
                    heroData.put("CuriosBackItem", hero.getCuriosBackItemTag()); // [新增]
                    data.put("HeroCombatRespawnData", heroData);

                    // [新增] 强制更新一次全局数据，作为双重保险
                    if (hero.level() instanceof ServerLevel serverLevel) {
                        HeroWorldData worldData = HeroWorldData.get(serverLevel);
                        worldData.setGlobalSkinVariant(hero.getSkinVariant());
                        if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                            worldData.setGlobalCustomSkinName(hero.getCustomSkinName());
                        }
                        
                        worldData.setEquipment(player.getUUID(), hero.getArmorItemsTag(), hero.getHandItemsTag());
                        worldData.setCuriosBackItem(player.getUUID(), hero.getCuriosBackItemTag()); // [新增]
                    }

                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.attack_disappoint"));
                    HeroDimensionHandler.leaveWorld(hero, null);
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

            // --- C. 陪伴模式 ---
            if (isCompanion) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_attack"));
            }
        }
        
        return false; // 锁血
    }
}
