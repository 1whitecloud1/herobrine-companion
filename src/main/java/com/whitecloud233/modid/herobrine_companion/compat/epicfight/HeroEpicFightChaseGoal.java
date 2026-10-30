package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.combat.HeroCombatDebugLog;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPursuit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 史诗战斗路径下的追击回退目标（替代 epicfight 的 {@code TargetChasingGoal}）。
 *
 * <p>epicfight 的 {@code TargetChasingGoal} 继承原版 {@code MeleeAttackGoal}，其
 * {@code canUse()} 会先调 {@code navigation.createPath(target, 0)}：寻路失败（Hero 用的是
 * FlyingPathNavigation，地面追击时常出不来路径）就整个目标不启动，导致目标在连段射程外时
 * Hero 原地发呆、既不追也不打。本目标不要求寻路成功即可启动，tick 里优先导航、导航失败或
 * 路径走完仍未进攻击范围时回退到 {@code moveControl.setWantedPosition} 直线逼近（与
 * {@link HeroBattleStanceGoal#moveTowardsTarget(double)} 的策略一致）。</p>
 *
 * <p>本版本在原有回退逻辑上叠加两层优化：</p>
 * <ol>
 *     <li><b>远距冲刺</b>：离目标超过 {@code HeroCombatPursuit} 的冲刺阈值时提高导航速度并
 *     触发冲刺标记，让 Epic Fight 播放 RUN 动作、快速拉近距离。</li>
 *     <li><b>飞行追击</b>：目标在高处、地面寻路连续失败或卡墙时，切换到浮空直线逼近目标
 *     （越过障碍 / 攀上高处），接近后若能落到可攻击的地面则回落进入近战；目标若持续悬空
 *     则保持悬停追击。</li>
 * </ol>
 */
public class HeroEpicFightChaseGoal extends Goal {
    private static final double MAX_CHASE_RANGE = 64.0D;
    private static final int PATH_RECALC_INTERVAL = 10;
    private static final int STUCK_TICKS_LIMIT = 10;
    private static final int LAND_COOLDOWN_TICKS = 60;
    private static final int GROUND_STALL_TICKS = 30;

    private final HeroEntity hero;
    private final double attackRadiusSqr;
    private int pathRecalcCooldown;
    private int groundPathFailures;
    private int stuckTicks;
    private boolean flyingPursuit;
    private int flightCooldownUntilTick;
    private int lastGroundProgressTick;
    private Vec3 lastGroundProgressPosition;

    public HeroEpicFightChaseGoal(HeroEntity hero, double speedModifier, double attackRadius) {
        this.hero = hero;
        double clampedRadius = Math.max(0.01D, attackRadius);
        this.attackRadiusSqr = clampedRadius * clampedRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.canChaseTarget() && !HeroEpicFightCompat.isHeroMidAttack(this.hero) && this.isOutOfAttackRange();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.hero.getTarget();
        if (!this.canChaseTarget() || target == null) {
            return false;
        }
        // Keep controlling the approach until a safe melee landing is available.
        // Attack root motion is left to AnimatedAttackGoal in tick().
        if (this.flyingPursuit) {
            return this.hero.isFloating();
        }
        // 留一点滞后量，避免在攻击范围边缘来回抖动
        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        return !HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius * 0.75D)
                || !this.hero.hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.pathRecalcCooldown = 0;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        this.flyingPursuit = this.hero.isFloating();
        this.lastGroundProgressTick = this.hero.tickCount;
        this.lastGroundProgressPosition = this.hero.position();
        this.hero.setSprinting(false);
    }

    @Override
    public void stop() {
        this.hero.getNavigation().stop();
        if (this.hero.getMoveControl() instanceof HeroMoveControl moveControl) {
            moveControl.stopMoving();
        }
        this.hero.setSprinting(false);
        if (this.flyingPursuit && !this.hero.isFloating()) {
            this.flightCooldownUntilTick = this.hero.tickCount + LAND_COOLDOWN_TICKS;
        }
        if ((this.flyingPursuit || this.hero.isFloating()) && !HeroEpicFightCompat.isHeroMidAttack(this.hero)) {
            // 目标仍高位且落地够不着 → 保持浮空，把攻击交给 AnimatedAttackGoal 的空中连段；
            // 否则落回地面。这是"飞上去立刻掉下来"的关键修复。
            if (!this.shouldKeepHovering()) {
                HeroCombatPursuit.land(this.hero);
            }
        }
        this.flyingPursuit = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.hero.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        this.hero.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // AnimatedAttackGoal also runs while chasing. Never overwrite its root
        // motion or turn an authored jump into persistent pursuit flight.
        if (HeroEpicFightCompat.isHeroMidAttack(this.hero)) {
            this.hero.getNavigation().stop();
            if (this.hero.getMoveControl() instanceof HeroMoveControl moveControl) {
                moveControl.stopMoving();
            }
            this.lastGroundProgressTick = this.hero.tickCount;
            this.lastGroundProgressPosition = this.hero.position();
            return;
        }

        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        if (this.flyingPursuit) {
            this.tickFlyingPursuit(target, attackRadius);
            return;
        }

        if (HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius) && this.hero.hasLineOfSight(target)) {
            // 已进入贴脸近战范围：停手交给 epicfight 的 AnimatedAttackGoal 结算。
            // 高处目标不满足 isWithinAttackReach（垂直差大），不会在这里提前停手，
            // 而是继续走飞行追击逼近到悬停点。
            this.hero.getNavigation().stop();
            return;
        }

        this.tickGroundPursuit(target, attackRadius);
    }

    // ==================== 地面追击 ====================

    private void tickGroundPursuit(LivingEntity target, double attackRadius) {
        Vec3 movement = this.hero.position().subtract(this.lastGroundProgressPosition);
        if (movement.horizontalDistanceSqr() > 0.25D) {
            this.lastGroundProgressPosition = this.hero.position();
            this.lastGroundProgressTick = this.hero.tickCount;
            this.groundPathFailures = 0;
            this.stuckTicks = 0;
        }
        if (this.shouldEnterFlight(target, attackRadius)) {
            this.enterFlight(target);
            return;
        }

        double speed = HeroCombatPursuit.chaseSpeed(this.hero, target);
        boolean pathActive = !this.hero.getNavigation().isDone();

        if (--this.pathRecalcCooldown <= 0) {
            this.pathRecalcCooldown = PATH_RECALC_INTERVAL + this.hero.getRandom().nextInt(6);
            if (this.hero.getNavigation().moveTo(target, speed)) {
                this.groundPathFailures = 0;
            } else {
                this.groundPathFailures++;
            }
        } else if (this.hero.horizontalCollision && pathActive) {
            // 有路径但一直被墙挡住：累计卡墙，达到阈值触发飞行越障
            this.stuckTicks++;
            if (this.stuckTicks >= STUCK_TICKS_LIMIT) {
                this.groundPathFailures = Math.max(this.groundPathFailures, HeroCombatPursuit.GROUND_PATH_FAIL_LIMIT);
            }
        } else {
            this.stuckTicks = 0;
        }

        // 导航失败（没出路径）或路径已走完仍未进范围 → 直线逼近，防止原地发呆
        if (this.hero.getNavigation().isDone()) {
            this.hero.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        }
    }

    private boolean shouldEnterFlight(LivingEntity target, double attackRadius) {
        boolean targetHigh = HeroCombatPursuit.isTargetHighAbove(this.hero, target);
        boolean inFluid = HeroCombatPursuit.isHeroInFluid(this.hero);
        // A failed path is not a stalled chase: direct movement may still be
        // closing the gap. Give grounded combos priority near the target.
        return HeroCombatPursuit.shouldTakeFlight(this.hero, target, this.groundPathFailures)
                && (targetHigh || inFluid || (this.hero.tickCount >= this.flightCooldownUntilTick
                && this.hero.tickCount - this.lastGroundProgressTick >= GROUND_STALL_TICKS
                && !(this.hero.hasLineOfSight(target)
                && HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius + 1.5D))));
    }

    private void enterFlight(LivingEntity target) {
        this.flyingPursuit = true;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        HeroCombatPursuit.startFlight(this.hero);
        HeroCombatDebugLog.transition(this.hero, "HeroEpicFightChaseGoal.enterFlight",
                "flying=true|" + HeroCombatDebugLog.heroCoreState(this.hero),
                "target=" + target.getDisplayName().getString() + ",dist=" + String.format("%.1f", this.hero.distanceTo(target)));
    }

    // ==================== 飞行追击 ====================

    private void tickFlyingPursuit(LivingEntity target, double attackRadius) {
        if (HeroCombatPursuit.shouldLand(this.hero, target, attackRadius)) {
            this.landNearTarget();
            return;
        }

        if (!this.hero.isFloating()) {
            // 浮空被外部重置（如入水、传送），回落到地面追击状态
            this.flyingPursuit = false;
            return;
        }

        Vec3 flyTarget = HeroCombatPursuit.flightTarget(this.hero, target, attackRadius);
        HeroCombatPursuit.flyTo(this.hero, flyTarget, HeroCombatPursuit.FLY_SPEED);
    }

    private void landNearTarget() {
        HeroCombatPursuit.land(this.hero);
        this.flyingPursuit = false;
        this.flightCooldownUntilTick = this.hero.tickCount + LAND_COOLDOWN_TICKS;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        HeroCombatDebugLog.transition(this.hero, "HeroEpicFightChaseGoal.landNearTarget",
                "flying=false|" + HeroCombatDebugLog.heroCoreState(this.hero),
                "cooldown=" + LAND_COOLDOWN_TICKS);
    }

    /**
     * stop 时是否保持浮空：目标仍存活且此刻落地会够不着（shouldLand 拒绝落地）时保留浮空，
     * 交给 AnimatedAttackGoal 打空中连段；目标消失 / 已能贴地命中则落回地面。
     */
    private boolean shouldKeepHovering() {
        LivingEntity target = this.hero.getTarget();
        if (target == null || !target.isAlive() || !this.hero.isBattleModeActive() || !this.hero.isFloating()) {
            return false;
        }
        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        return !HeroCombatPursuit.shouldLand(this.hero, target, attackRadius);
    }

    // ==================== 公共判断 ====================

    private boolean canChaseTarget() {
        if (!this.hero.isBattleModeActive() || !this.hero.isAlive() || this.hero.isPassenger()) {
            return false;
        }
        LivingEntity target = this.hero.getTarget();
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private boolean isOutOfAttackRange() {
        LivingEntity target = this.hero.getTarget();
        if (target == null) {
            return false;
        }
        // 射程判定改水平距离 + 垂直带：高处目标即使 3D 距离很近也算"够不着"，
        // 交给飞行追击，避免 Hero 站在树下对着够不着的怪发呆 / 空挥
        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        return HeroCombatPursuit.horizontalDistanceSqr(this.hero, target) <= MAX_CHASE_RANGE * MAX_CHASE_RANGE
                && (!HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius) || !this.hero.hasLineOfSight(target));
    }
}
