package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.DamageSources;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.AttackConfirmation;
import com.whitecloud233.modid.herobrine_companion.fight.ChallengeSession;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroProtectionEvents {

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            event.setCanceled(true);
        }
        if (newTarget instanceof HeroEntity hero && !canHeroBeAttacked(hero)) {
            event.setCanceled(true);
        }
        // 战斗模式不认领原版生物/觉醒怪物：拦在赋值入口，比事后清理更早一步。
        if (event.getEntity() instanceof HeroEntity hero && hero.shouldPreventProtectedBattleHit(newTarget)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            if (event.getSource().getEntity() != null) {
                event.setCanceled(true);
            }
        }

        if (event.getEntity() instanceof HeroEntity hero) {
            PlayerHitDecision playerHitDecision = resolvePlayerHitDecision(hero, event.getSource(), true);
            if (playerHitDecision == PlayerHitDecision.CANCEL) {
                event.setCanceled(true);
                return;
            }
            if (playerHitDecision == PlayerHitDecision.ALLOW) {
                return;
            }
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

            if (!canHeroBeAttacked(hero)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            if (event.getSource().getEntity() != null) {
                event.setCanceled(true);
            }
        }

        if (event.getEntity() instanceof HeroEntity hero) {
            PlayerHitDecision playerHitDecision = resolvePlayerHitDecision(hero, event.getSource(), false);
            if (playerHitDecision == PlayerHitDecision.CANCEL) {
                event.setCanceled(true);
                return;
            }
            if (playerHitDecision == PlayerHitDecision.ALLOW) {
                return;
            }
        }

        HeroEntity attackingHero = getHeroAttacker(event.getSource());
        if (attackingHero != null && attackingHero.shouldPreventFriendlyFire(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        // 同上：伤害结算在 LivingHurtEvent 也拦一次，避免只取消攻击事件、伤害仍然落地。
        if (attackingHero != null && attackingHero.shouldPreventProtectedBattleHit(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.isBattleModeActive() && isPlayerSource(event.getSource())) {
                event.setCanceled(true);
                return;
            }

            if (!canHeroBeAttacked(hero)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (event.getEntity() instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
                mob.setTarget(null);
                if (mob instanceof Warden warden) {
                    warden.clearAnger(player);
                }
            }
            if (target instanceof HeroEntity hero && !canHeroBeAttacked(hero)) {
                mob.setTarget(null);
                if (mob instanceof Warden warden) {
                    warden.clearAnger(target);
                }
            }
            if (mob instanceof WitherBoss wither
                    && wither.getTarget() instanceof Player player
                    && player.getTags().contains("herobrine_companion_peaceful")) {
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
