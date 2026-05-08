package com.whitecloud233.herobrine_companion.client.fight.event;

import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent; // 👇 引入正确的 NeoForge 玩家事件包
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;

@EventBusSubscriber(modid = "herobrine_companion")
public class ChallengeEventHandler {

    @SubscribeEvent
    // 👇 修复点：修改为 NeoForge 的 PlayerLoggedOutEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                // 如果玩家在挑战期间强行退出（拔网线），立刻触发失败逻辑！
                // 这会自动清理 Boss 实体、恢复 End Ring 场地，并释放全局锁
                HeroChallengeManager.failChallenge(player);
            }
        }
    }

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
            boolean isFakeOutPhase = player.getPersistentData().getBoolean("HeroFakeOutPhase");
            boolean isChallengeActive = player.getPersistentData().getBoolean("IsChallengeActive");

            // 如果假死标记残留到了日常/陪伴模式，立刻清掉，避免后续每次受伤都被锁成半颗心。
            if (isFakeOutPhase && !isChallengeActive) {
                player.getPersistentData().remove("HeroFakeOutPhase");
                return;
            }

            if (isFakeOutPhase) {
                // 玩家进入绝对的剧情无敌状态，防虚空伤害
                event.setCanceled(true);
                player.setHealth(1.0f);
            }
        }
    }

    // ==========================================
    // 【终极防脱轨安全网】
    // ==========================================

    @SubscribeEvent
    public static void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }

        if (player.level().dimension() != ModStructures.END_RING_DIMENSION_KEY
                || !player.getPersistentData().getBoolean("HeroFakeOutPhase")) {
            return;
        }

        player.fallDistance = 0;

        MobEffectInstance slowFalling = player.getEffect(MobEffects.SLOW_FALLING);
        if (slowFalling == null || slowFalling.getDuration() < 10) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
        }

        // 【终极防脱轨安全网】：如果演出过程中出现意外坠落，立刻拉回擂台上空，保证后续假崩溃页与胜利回传能继续执行。
        if (player.getY() < 75.0D) {
            player.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
            player.setDeltaMovement(0, -0.08D, 0);
            player.fallDistance = 0;
        }
    }
}