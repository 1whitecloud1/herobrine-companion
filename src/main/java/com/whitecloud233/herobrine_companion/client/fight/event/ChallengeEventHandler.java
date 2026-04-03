package com.whitecloud233.herobrine_companion.client.fight.event;

import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "herobrine_companion")
public class ChallengeEventHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsChallengeActive")
                    && !hero.getPersistentData().getBoolean("IsFakeOutPhase")) {

                event.setCanceled(true);
                hero.setHealth(1.0f);

                HeroChallengeManager.triggerFakeOutPhase(hero);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsFakeOutPhase")) {
                event.setCanceled(true);
                return;
            }
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("HeroFakeOutPhase")) {
                event.setCanceled(true);
                player.setHealth(1.0f);
            }
        }
    }

    // ==========================================
    // 【终极防脱轨安全网】
    // ==========================================
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 增加维度判断：只有在试炼维度才生效！
            if (player.level().dimension() == com.whitecloud233.herobrine_companion.world.structure.ModStructures.END_RING_DIMENSION_KEY
                    && player.getPersistentData().getBoolean("HeroFakeOutPhase")) {

                // 如果出现意外导致缓慢下落失效，绝对不允许玩家掉到 Y=75 以下！
                if (player.getY() < 75) {
                    player.teleportTo(player.getX(), 90.0, player.getZ());
                    player.setDeltaMovement(0, 0, 0);
                    player.hurtMarked = true;
                }
            }
        }
    }
}