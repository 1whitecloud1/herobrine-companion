package com.whitecloud233.modid.herobrine_companion.entity.ai.combat;

import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemHeroCombo;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroScytheComboLibrary;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Native Poem combat: the synchronized move owns pose, contacts, travel and aerial follow-ups. */
public final class HeroPoemCombat {
    private final HeroEntity hero;
    private boolean active, launched;
    private int mode, contact, dashCooldown, aerialTicks, pathFailures;

    public HeroPoemCombat(HeroEntity hero) { this.hero = hero; }

    public void stop() {
        if (!active) return;
        active = false;
        dashCooldown = aerialTicks = pathFailures = 0;
        stopNavigation();
        if (hero.isFloating()) HeroCombatPursuit.land(hero);
        hero.resetBattleActionTimeline();
        hero.setSprinting(false);
    }

    public void tick(LivingEntity target, double reachSqr) {
        active = true;
        if (dashCooldown > 0) dashCooldown--;
        if (aerialTicks > 0) aerialTicks--;
        int currentMode = HeroScytheComboLibrary.battleMode(hero);
        if (hero.isBattleTapAction() && hero.getBattlePoemMove() >= 0 && mode == currentMode) {
            tickAttack(target, reachSqr);
            return;
        }
        settleFlight(target, reachSqr);
        if (canOpen(target, reachSqr)) begin(target, reachSqr);
        else chase(target, reachSqr);
    }

    private boolean dashOpportunity(LivingEntity target, double reachSqr) {
        double distance = HeroCombatPursuit.horizontalDistanceSqr(hero, target);
        return dashCooldown == 0 && hero.onGround() && !hero.isFloating()
                && !hero.horizontalCollision && hero.hasLineOfSight(target)
                && Math.abs(target.getY() - hero.getY()) < 1.5
                && distance > reachSqr * .8 && distance <= 25;
    }

    private boolean canOpen(LivingEntity target, double reachSqr) {
        return HeroCombatPlanner.canStartMeleeAttack(hero, target, reachSqr, 2)
                || dashOpportunity(target, reachSqr);
    }

    private void begin(LivingEntity target, double reachSqr) {
        mode = HeroScytheComboLibrary.battleMode(hero);
        int move = PoemHeroCombo.selectMove(hero.getBattleActionSerial() + 1,
                hero.isFloating() || !hero.onGround(), dashOpportunity(target, reachSqr));
        stopNavigation();
        int action = hero.getBattleActionState() == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1;
        hero.beginBattleAction(action);
        hero.setBattlePoemMove(move);
        hero.setBattleComboStep(move + 1);
        hero.setSprinting(move == PoemMotionLibrary.DASH);
        hero.swing(InteractionHand.MAIN_HAND);
        if (move == PoemMotionLibrary.DASH) dashCooldown = 35;
        contact = 0;
        launched = false;
        face(target);
    }

    private void tickAttack(LivingEntity target, double reachSqr) {
        int move = hero.getBattlePoemMove();
        var timing = PoemMotionLibrary.get().timing(mode, move);
        int ticks = hero.getBattleActionTicks() + 1;
        hero.setBattleActionTicks(ticks);
        stopNavigation();
        face(target);
        float age = ticks / 20f;

        // The uppercut starts a bounded aerial sequence even against ground targets.
        if (!launched && move == PoemMotionLibrary.extraMove(1, false)
                && age >= timing.contacts().get(0).start()) {
            launched = true;
            if (!hero.isPassenger() && hero.level().noCollision(hero, hero.getBoundingBox().move(0, 1, 0))) {
                HeroCombatPursuit.startFlight(hero);
                hero.setNoGravity(true);
                aerialTicks = 60;
            }
        }
        if (age <= timing.recovery()) {
            boolean moving = move == PoemMotionLibrary.DASH || age >= Math.max(0, timing.contacts().get(0).start() - .15f);
            if (moving || hero.isFloating()) {
                Vec3 offset = target.position().subtract(hero.position());
                hero.setDeltaMovement(attackVelocity(hero.getDeltaMovement(), offset, move,
                        hero.isFloating(), moving && !hero.horizontalCollision));
                hero.hasImpulse = true;
            }
        }
        // Flurry and aerial clips have multiple authored contacts, not a single generic hit frame.
        while (contact < timing.contacts().size() && ticks >= PoemHeroCombo.contactTick(timing.contacts().get(contact))) {
            contact++;
            if (HeroCombatTargeting.canHeroAttackTarget(hero, target)
                    && HeroCombatPlanner.canStartMeleeAttack(hero, target, reachSqr, 0)) {
                hero.doHurtTarget(target);
            }
        }
        if (ticks >= PoemHeroCombo.chainForMove(mode, move)) {
            settleFlight(target, reachSqr);
            if (HeroCombatTargeting.canHeroAttackTarget(hero, target) && canOpen(target, reachSqr)) {
                begin(target, reachSqr);
                return;
            }
        }
        if (ticks >= PoemHeroCombo.durationForMove(mode, move)) {
            hero.resetBattleActionTimeline();
            hero.setSprinting(false);
        }
    }

    /** Real velocity, bounded to stop short of the target; vanilla collision still owns travel. */
    public static Vec3 attackVelocity(Vec3 current, Vec3 targetOffset, int move, boolean floating, boolean advance) {
        double distance = targetOffset.horizontalDistance();
        double speed = advance ? Math.min(move == PoemMotionLibrary.DASH ? .65 : .28,
                Math.max(0, distance - 1.15)) : 0;
        double y = floating ? Mth.clamp((targetOffset.y + .9) * .3, -.22, .28) : current.y;
        return distance < .0001 ? new Vec3(0, y, 0)
                : new Vec3(targetOffset.x / distance * speed, y, targetOffset.z / distance * speed);
    }

    private void settleFlight(LivingEntity target, double reachSqr) {
        if (hero.isFloating() && aerialTicks == 0 && HeroCombatPursuit.shouldLand(hero, target, Math.sqrt(reachSqr))) {
            HeroCombatPursuit.land(hero);
        }
    }

    private void chase(LivingEntity target, double reachSqr) {
        hero.setBattleActionState(HeroEntity.BATTLE_ACTION_APPROACH);
        hero.setBattleActionTicks(0);
        hero.setBattlePoemMove(-1);
        if (HeroCombatPursuit.shouldTakeFlight(hero, target, pathFailures)) {
            if (!hero.isFloating()) HeroCombatPursuit.startFlight(hero);
            hero.setNoGravity(true);
            HeroCombatPursuit.flyTo(hero, HeroCombatPursuit.flightTarget(hero, target, Math.sqrt(reachSqr)), 1);
        } else {
            double speed = HeroCombatPursuit.chaseSpeed(hero, target);
            if (hero.getNavigation().moveTo(target, speed)) pathFailures = 0;
            else {
                pathFailures++;
                hero.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
            }
        }
    }

    private void face(LivingEntity target) {
        Vec3 offset = target.position().subtract(hero.position());
        if (offset.horizontalDistanceSqr() > .0001) {
            hero.setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
            hero.yBodyRot = hero.getYRot();
        }
    }

    private void stopNavigation() {
        hero.getNavigation().stop();
        if (hero.getMoveControl() instanceof HeroMoveControl control) control.stopMoving();
    }
}
