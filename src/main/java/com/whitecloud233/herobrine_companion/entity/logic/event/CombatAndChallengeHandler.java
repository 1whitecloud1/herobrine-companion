package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.fml.common.EventBusSubscriber;


@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatAndChallengeHandler {

    // [新增] 怪物死亡事件 (用于触发战斗评论)
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Monster) {
            ServerLevel level = (ServerLevel) player.level();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isCompanionMode() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    // 只有距离比较近才说话
                    if (hero.distanceToSqr(player) < 400) {
                        HeroDialogueHandler.onKillMonster(hero, player);
                    }
                    break;
                }
            }
        }
        // 👇 [新增核心逻辑：玩家在试炼中死亡]
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            if (deadPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager.failChallenge(deadPlayer);
            }
        }
    }
}