package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroProtectionEvents {

    // 1. 仇恨锁定保护
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            event.setCanceled(true);
        }
        if (newTarget instanceof HeroEntity hero) {
            // 挑战/战斗模式下允许被敌人锁定，避免 AI 一边追踪一边被事件清空目标造成状态循环
            if (!canHeroBeAttacked(hero)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            if (event.getSource().getEntity() != null) event.setCanceled(true);
        }

        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.isBattleModeActive() && isPlayerSource(event.getSource())) {
                event.setCanceled(true);
                return;
            }

            // 只有日常非战斗模式取消伤害事件；战斗/挑战模式放行
            if (!canHeroBeAttacked(hero)) {
                event.setCanceled(true);
            }
        }
    }


    // 3. 实体 Tick 逻辑 (替代 LivingEvent.LivingTickEvent)
    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
                mob.setTarget(null);
                if (mob instanceof Warden warden) warden.clearAnger(player);
            }
            if (target instanceof HeroEntity hero) {
                // 日常模式下清除怪物仇恨，战斗/挑战模式允许混战
                if (!canHeroBeAttacked(hero)) {
                    mob.setTarget(null);
                    if (mob instanceof Warden warden) warden.clearAnger(target);
                }
            }
            if (mob instanceof WitherBoss wither && wither.getTarget() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
                wither.setTarget(null);
            }
        }
    }
    private static boolean canHeroBeAttacked(HeroEntity hero) {
        return hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || hero.getPersistentData().getBoolean("IsChallengeActive")
                || hero.isBattleModeActive();
    }

    private static boolean isPlayerSource(net.minecraft.world.damagesource.DamageSource source) {
        return source != null && source.getEntity() instanceof Player;
    }
}