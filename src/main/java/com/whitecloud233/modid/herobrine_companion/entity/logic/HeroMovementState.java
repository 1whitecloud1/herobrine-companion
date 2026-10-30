package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import static com.whitecloud233.modid.herobrine_companion.entity.HeroEntity.*;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPursuit;
import com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroGodlyCompanionGoal;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Owns navigation selection, companion physics and transient movement timers. */
public final class HeroMovementState {
    private final HeroEntity hero;
    private final FlyingPathNavigation flying;
    private final GroundPathNavigation ground;
    private int outOfWaterTimer, followHoldUntilTick;

    public HeroMovementState(HeroEntity hero, PathNavigation initial) {
        this.hero = hero;
        flying = initial instanceof FlyingPathNavigation navigation ? navigation : createFlyingNavigation(hero, hero.level());
        ground = new GroundPathNavigation(hero, hero.level());
        ground.setCanFloat(false);
        ground.setCanOpenDoors(true);
    }

    public static FlyingPathNavigation createFlyingNavigation(HeroEntity hero, Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(hero, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    public PathNavigation selectNavigation(PathNavigation current) {
        PathNavigation selected = hero.isFloating() ? flying : ground;
        if (current != selected) {
            if (current != null) current.stop();
            flying.stop();
            ground.stop();
        }
        return selected;
    }

    /** Combat owns its flight; companion landing must never undo an attack's takeoff. */
    public void afterAiStep() {
        // 陪伴 + 战斗同时开启、且当前没有敌人：按陪伴处理（贴地跟随），不能因为战斗模式就跳过。
        if (hero.isCompanionMode() && !hero.isChasingBattleEnemy()) {
            maintainCompanion();
        } else {
            if (hero.noPhysics) {
                hero.noPhysics = false;
            }
            boolean inIntro = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY && hero.getTags().contains("hero_intro_sequence");
            if (!inIntro) {
                if (hero.isInWater() || hero.isInLava()) {
                    outOfWaterTimer = 40;
                    if (!hero.isFloating()) hero.setFloating(true);
                    if (!hero.isNoGravity()) hero.setNoGravity(true);
                    if (hero.getDeltaMovement().y < 0.1) hero.setDeltaMovement(hero.getDeltaMovement().add(0, 0.05, 0));
                    // 【战斗模式飞行修复】身处流体时主动飞出液面悬停，而不是只靠浮力泡在水面。
                    // 有可追击目标时由 HeroCombatPursuit 飞行追击接管；无目标时兜底脱困。
                    if (hero.isBattleModeActive()) {
                        LivingEntity fluidTarget = hero.getTarget();
                        if (fluidTarget == null || !fluidTarget.isAlive() || fluidTarget.isRemoved()) {
                            HeroCombatPursuit.hoverAboveFluid(hero);
                        }
                    }
                } else if (outOfWaterTimer > 0) {
                    outOfWaterTimer--;
                    // Combat pursuit owns landing after leaving the fluid. The
                    // companion grace period must not keep cancelling it.
                    if (!hero.isBattleModeActive()) {
                        if (!hero.isFloating()) hero.setFloating(true);
                        if (!hero.isNoGravity()) hero.setNoGravity(true);
                    }
                }
            }
        }
    }

    public void syncGroundWalking() {
        if (!hero.level().isClientSide) {
            double movedX = hero.getX() - hero.xOld;
            double movedZ = hero.getZ() - hero.zOld;
            boolean groundWalking = !hero.isFloating()
                    && (movedX * movedX + movedZ * movedZ > 1.0E-5D
                        || hero.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5D
                        || !hero.getNavigation().isDone());
            if (groundWalking != hero.getEntityData().get(IS_GROUND_WALKING)) {
                hero.getEntityData().set(IS_GROUND_WALKING, groundWalking);
            }
        }

    }

    public void maintainCompanion() {
        // 拉磨期间位移归石磨，这里若去改重力/速度会和磨盘抢位置。
        if (hero.isMillstoneHarnessed()) {
            return;
        }
        if (hero.isFloating() && hero.getOwnerUUID() != null) {
            Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
            if (owner != null && owner.isAlive()
                    && !owner.getAbilities().flying
                    && !owner.isFallFlying()
                    && Math.abs(owner.getY() - hero.getY()) <= 3.0D
                    && HeroGodlyCompanionGoal.horizontalDistanceToOwnerSqr(hero, owner) <= 64.0D) {
                hero.noPhysics = false;
                hero.setFloating(false);
                hero.setNoGravity(false);
            }
        }
        if (!hero.isFloating()) {
            hero.noPhysics = false;
            if (hero.isNoGravity()) hero.setNoGravity(false);
            return;
        }
        if (!hero.isNoGravity()) hero.setNoGravity(true);
        hero.fallDistance = 0.0F;

        if (HeroGodlyCompanionGoal.isOwnerWithinStayStillRadius(hero)) {
            if (!hero.getNavigation().isDone()) {
                hero.getNavigation().stop();
            }
            if (hero.getMoveControl() instanceof HeroMoveControl heroMoveControl) {
                heroMoveControl.stopMoving();
            }
            if (hero.getDeltaMovement().lengthSqr() > 1.0E-6D) {
                hero.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }

        Vec3 movement = hero.getDeltaMovement();
        if (hero.noPhysics && movement.y < 0.0D && hero.getMoveControl() instanceof HeroMoveControl heroMoveControl) {
            Vec3 protectedMovement = heroMoveControl.protectCompanionFloor(movement);
            if (protectedMovement.y < 0.0D
                    && (hero.getOwnerUUID() == null || HeroGodlyCompanionGoal.isOwnerWithinStayStillRadius(hero))) {
                protectedMovement = new Vec3(protectedMovement.x, 0.0D, protectedMovement.z);
            }
            hero.setDeltaMovement(protectedMovement);
        }
    }

    public void setCompanionMode(boolean active) {
        boolean previous = hero.isCompanionMode();
        hero.getEntityData().set(IS_COMPANION_MODE, active);
        if (!previous && active && !hero.isBattleModeActive()) {
            hero.noPhysics = false;
            if (hero.isFloating()) hero.setFloating(false);
            if (hero.isNoGravity()) hero.setNoGravity(false);
        }
        if (previous && !active) {
            cleanupCompanion();
        }
    }

    private void cleanupCompanion() {
        hero.noPhysics = false;
        hero.getNavigation().stop();
        if (hero.getMoveControl() instanceof HeroMoveControl heroMoveControl) {
            heroMoveControl.stopMoving();
        }
        hero.setDeltaMovement(Vec3.ZERO);
        hero.fallDistance = 0.0F;
        if (hero.isFloating()) hero.setFloating(false);
        if (hero.isNoGravity()) hero.setNoGravity(false);
    }

    public void markTeleportFollowHold() {
        followHoldUntilTick = hero.tickCount + TP_FOLLOW_HOLD_TICKS;
    }
    public boolean isTeleportFollowHoldActive() {
        return hero.tickCount < followHoldUntilTick;
    }
}
