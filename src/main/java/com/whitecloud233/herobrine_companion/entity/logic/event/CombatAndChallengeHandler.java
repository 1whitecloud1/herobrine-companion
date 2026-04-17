package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroObserveAndRescueGoal;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// 注意：NeoForge 1.21+ 的注解是 @EventBusSubscriber
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatAndChallengeHandler {

    @SubscribeEvent
    // 【NeoForge 1.21 API 变更】：使用 LivingDamageEvent.Pre
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            // 【NeoForge 1.21 API 变更】：使用 getNewDamage()
            float finalHealth = player.getHealth() - event.getNewDamage();

            if (finalHealth <= HeroObserveAndRescueGoal.RESCUE_HEALTH_THRESHOLD) {
                ServerLevel level = (ServerLevel) player.level();
                long currentTime = level.getGameTime();

                for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                    if (hero.level() == level && hero.isAlive() && hero.isCompanionMode()
                            && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {

                        long lastRescue = hero.getPersistentData().getLong("LastRescueTime");

                        if (lastRescue == 0 || (currentTime - lastRescue) > HeroObserveAndRescueGoal.RESCUE_COOLDOWN) {


                            // 【NeoForge 1.21 API 变更】：使用 setNewDamage()
                            event.setNewDamage(0);

                            hero.getPersistentData().putLong("LastRescueTime", currentTime);
                            HeroObserveAndRescueGoal.performRescue(hero, player);

                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 玩家击杀怪物
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Monster) {
            ServerLevel level = (ServerLevel) player.level();

            for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == level && hero.isAlive() && hero.isCompanionMode()
                        && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {

                    if (hero.distanceToSqr(player) < 400) {
                        HeroDialogueHandler.onKillMonster(hero, player);
                    }
                    break;
                }
            }
        }

        // 玩家死亡（挑战失败逻辑）
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            if (deadPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager.failChallenge(deadPlayer);
            }
        }
    }
}