package com.whitecloud233.modid.herobrine_companion.client.fight.event;

import com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "herobrine_companion")
public class ChallengeEventHandler {
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                // 如果玩家在挑战期间强行退出（拔网线），立刻触发失败逻辑！
                // 这会自动清理 Boss 实体、恢复 End Ring 场地，并释放全局锁
                HeroChallengeManager.failChallenge(player);
            }
        }
    }
    // 【修复核心】：使用 LivingDeathEvent 拦截致死一击，100% 准确
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsChallengeActive")
                    && !hero.getPersistentData().getBoolean("IsFakeOutPhase")) {

                event.setCanceled(true); // 取消死亡
                hero.setHealth(1.0f);    // 锁在1滴血

                // 1. 触发第一阶段演出（黑屏发包，Boss悬浮）
                HeroChallengeManager.triggerFakeOutPhase(hero);

                // 【注意】：之前的 server.tell 延时代码已经被彻底删除了！
                // 我们把倒计时交给了 HeroChallengeState 来处理
            }
        }
    }

    // 依然保留 DamageEvent，但只用来处理假死阶段的无敌和玩家锁血
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // 1. 【核心救命补丁】：防止玩家在假死演出期间“鞭尸”把 Herobrine 打死！
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsFakeOutPhase")) {
                event.setCanceled(true); // 绝对无敌
                return;
            }
        }

        // 2. 玩家在“虚晃一枪”期间受到的伤害锁血与无敌
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

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || !player.isAlive()) {
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