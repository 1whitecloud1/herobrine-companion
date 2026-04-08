package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroObserveAndRescueGoal;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatAndChallengeHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // 【核心修复】：事前伤害拦截。在伤害结算前计算最终血量，直接掐灭秒杀和客户端 UI 不同步的源头！
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            float finalHealth = player.getHealth() - event.getAmount();

            // 如果这一击会致死，或者血量被压低到了救援线（默认 6.0F / 3颗心）及以下
            if (finalHealth <= HeroObserveAndRescueGoal.RESCUE_HEALTH_THRESHOLD) {
                ServerLevel level = (ServerLevel) player.level();
                long currentTime = level.getGameTime();

                for (HeroEntity hero : com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                    if (hero.level() == level && hero.isAlive() && hero.isCompanionMode()
                            && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {

                        // 取出记录在实体 NBT 里的上一次救援时间
                        long lastRescue = hero.getPersistentData().getLong("LastRescueTime");

                        // 判定 10 分钟冷却。增加 lastRescue == 0 防止新建存档时前 10 分钟不生效
                        if (lastRescue == 0 || (currentTime - lastRescue) > HeroObserveAndRescueGoal.RESCUE_COOLDOWN) {

                            // 1. 彻底取消本次致命伤害！
                            event.setCanceled(true);
                            event.setAmount(0);

                            // 2. 写入最新冷却时间戳
                            hero.getPersistentData().putLong("LastRescueTime", currentTime);

                            // 3. 呼叫 Hero 强行干涉
                            HeroObserveAndRescueGoal.performRescue(hero, player);

                            // 救援成功，终止本次事件循环
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

            for (HeroEntity hero : com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                // 确保在同一维度，且存活，并且是当前玩家的伴侣
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
                com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager.failChallenge(deadPlayer);
            }
        }
    }
}