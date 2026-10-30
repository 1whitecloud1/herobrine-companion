package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.entity.logic.data.DamageSources;

import com.whitecloud233.herobrine_companion.entity.logic.data.AttackConfirmation;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
            return;
        }
        if (event.getEntity() instanceof HeroEntity hero
                && hero.shouldPreventFriendlyFire(newTarget)) {
            event.setCanceled(true);
            return;
        }
        // 战斗模式不认领原版生物/觉醒怪物：拦在赋值入口，比事后清理更早一步。
        if (event.getEntity() instanceof HeroEntity hero
                && hero.shouldPreventProtectedBattleHit(newTarget)) {
            event.setCanceled(true);
            return;
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

        if (event.getEntity() instanceof HeroEntity hero
                && hero.hasCompanionFriendlyFireProtection()
                        && isPlayerSource(event.getSource())) {
        event.setCanceled(true);
        return;
    }

    HeroEntity attackingHero = getHeroAttacker(event.getSource());
        if (attackingHero != null && attackingHero.shouldPreventFriendlyFire(event.getEntity())) {
        event.setCanceled(true);
        return;
    }
        // 战斗模式只讨伐模组生物：技能特效是直接 hurt() 的，绕过了 canAttack，在这里统一兜住。
        if (attackingHero != null && attackingHero.shouldPreventProtectedBattleHit(event.getEntity())) {
        event.setCanceled(true);
        return;
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
        return ChallengeSession.isHeroInChallenge(hero)
                || hero.isBattleModeActive();
    }

    private static boolean isPlayerSource(DamageSource source) {
        return getPlayerAttacker(source) != null;
    }

    private static HeroEntity getHeroAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof HeroEntity hero) {
            return hero;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof HeroEntity hero) {
            return hero;
        }

        return null;
    }

    private static PlayerHitDecision resolvePlayerHitDecision(HeroEntity hero, DamageSource source, boolean sendFeedback) {
        Player player = getPlayerAttacker(source);
        if (player == null) {
            return PlayerHitDecision.PASS;
        }
        if (hero.isBattleModeActive()) {
            return PlayerHitDecision.CANCEL;
        }
        if (canHeroBeAttacked(hero)) {
            return PlayerHitDecision.PASS;
        }

        if (hero.getOwnerUUID() != null && !hero.getOwnerUUID().equals(player.getUUID())) {
            if (sendFeedback && !player.level().isClientSide) {
                player.sendSystemMessage(hero.createNotYourHeroMessage());
            }
            return PlayerHitDecision.CANCEL;
        }

        if (hero.getOwnerUUID() == null || !hero.getOwnerUUID().equals(player.getUUID())) {
            return PlayerHitDecision.CANCEL;
        }

        if (hasConfirmedAttackIntent(hero, player)) {
            return PlayerHitDecision.ALLOW;
        }

        if (sendFeedback && !player.level().isClientSide) {
            rememberAccidentalHit(hero, player);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.accidental_attack_warning"));
        }
        return PlayerHitDecision.CANCEL;
    }

    private static boolean hasConfirmedAttackIntent(HeroEntity hero, Player player) {
        return AttackConfirmation.isConfirmed(hero, player);
    }

    private static void rememberAccidentalHit(HeroEntity hero, Player player) {
        AttackConfirmation.remember(hero, player);
    }

    private static void clearPendingAttackConfirmation(HeroEntity hero) {
        AttackConfirmation.clear(hero);
    }

    private static Player getPlayerAttacker(DamageSource source) {
        return DamageSources.playerAttacker(source);
    }

    private enum PlayerHitDecision {
        PASS,
        CANCEL,
        ALLOW
    }
}
