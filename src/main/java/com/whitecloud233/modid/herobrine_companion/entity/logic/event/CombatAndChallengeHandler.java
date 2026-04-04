package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatAndChallengeHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 玩家击杀怪物
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Monster) {
            ServerLevel level = (ServerLevel) player.level();

            // 👇 优化：不再遍历 level.getAllEntities()
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

        // ... 下方的玩家死亡挑战逻辑保持不变 ...

        // 玩家死亡（挑战失败逻辑）
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            if (deadPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager.failChallenge(deadPlayer);
            }
        }
    }
}