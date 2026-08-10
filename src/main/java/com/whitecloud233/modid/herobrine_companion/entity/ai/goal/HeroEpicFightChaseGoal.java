package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightDebugLog;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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
    /** 悬停贴近后等待空中攻击的窗口；超时仍无招式则落回地面进入近战。 */
    private static final int HOVER_GRACE_TICKS = 15;

    private final HeroEntity hero;
    private final double attackRadiusSqr;
    private int pathRecalcCooldown;
    private int groundPathFailures;
    private int stuckTicks;
    private boolean flyingPursuit;
    private int landCooldown;
    private int hoverGraceTicks;

    public HeroEpicFightChaseGoal(HeroEntity hero, double speedModifier, double attackRadius) {
        this.hero = hero;
        double clampedRadius = Math.max(0.01D, attackRadius);
        this.attackRadiusSqr = clampedRadius * clampedRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.canChaseTarget() && this.isOutOfAttackRange();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.hero.getTarget();
        if (!this.canChaseTarget() || target == null) {
            return false;
        }
        // 飞行追击中：悬停贴身时继续占用 Goal，由 tickFlyingPursuit/tickHoveringNearTarget
        // 管理悬停/落地，直到浮空被外部重置（入水/传送/已落回地面）才结束。
        // 否则 AnimatedAttackGoal 一进射程就把本 Goal 抢占并落地，飞上去立刻掉下来。
        if (this.flyingPursuit) {
            return this.hero.isFloating();
        }
        // 留一点滞后量，避免在攻击范围边缘来回抖动
        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        return !HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius * 0.75D);
    }

    @Override
    public void start() {
        this.pathRecalcCooldown = 0;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        this.flyingPursuit = false;
        this.landCooldown = 0;
        this.hoverGraceTicks = 0;
        this.hero.setSprinting(false);
    }

    @Override
    public void stop() {
        this.hero.getNavigation().stop();
        this.hero.setSprinting(false);
        if (this.flyingPursuit || this.hero.isFloating()) {
            // 目标仍高位且落地够不着 → 保持浮空，把攻击交给 AnimatedAttackGoal 的空中连段；
            // 否则落回地面。这是"飞上去立刻掉下来"的关键修复。
            if (!this.shouldKeepHovering()) {
                HeroCombatPursuit.land(this.hero);
            }
        }
        this.flyingPursuit = false;
        this.hoverGraceTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.hero.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        this.hero.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        if (this.flyingPursuit) {
            this.tickFlyingPursuit(target, attackRadius);
            return;
        }

        if (HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius)) {
            // 已进入贴脸近战范围：停手交给 epicfight 的 AnimatedAttackGoal 结算。
            // 高处目标不满足 isWithinAttackReach（垂直差大），不会在这里提前停手，
            // 而是继续走飞行追击逼近到悬停点。
            this.hero.getNavigation().stop();
            return;
        }

        if (this.landCooldown > 0) {
            this.landCooldown--;
        }

        this.tickGroundPursuit(target, attackRadius);
    }

    // ==================== 地面追击 ====================

    private void tickGroundPursuit(LivingEntity target, double attackRadius) {
        if (this.shouldEnterFlight(target)) {
            this.enterFlight(target);
            return;
        }

        double speed = HeroCombatPursuit.chaseSpeed(this.hero, target);
        boolean pathActive = !this.hero.getNavigation().isDone();

        if (--this.pathRecalcCooldown <= 0) {
            this.pathRecalcCooldown = PATH_RECALC_INTERVAL + this.hero.getRandom().nextInt(6);
            if (this.hero.getNavigation().moveTo(target, speed)) {
                this.groundPathFailures = 0;
                this.stuckTicks = 0;
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

    private boolean shouldEnterFlight(LivingEntity target) {
        boolean targetHigh = HeroCombatPursuit.isTargetHighAbove(this.hero, target);
        boolean inFluid = HeroCombatPursuit.isHeroInFluid(this.hero);
        // 目标高位或身处流体立即起飞；地面寻路失败 / 卡墙则尊重落地冷却，避免反复横跳
        return HeroCombatPursuit.shouldTakeFlight(this.hero, target, this.groundPathFailures)
                && (targetHigh || inFluid || this.landCooldown <= 0);
    }

    private void enterFlight(LivingEntity target) {
        this.flyingPursuit = true;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        HeroCombatPursuit.startFlight(this.hero);
        HeroEpicFightDebugLog.transition(this.hero, "HeroEpicFightChaseGoal.enterFlight",
                "flying=true|" + HeroEpicFightDebugLog.heroCoreState(this.hero),
                "target=" + target.getDisplayName().getString() + ",dist=" + String.format("%.1f", this.hero.distanceTo(target)));
    }

    // ==================== 飞行追击 ====================

    private void tickFlyingPursuit(LivingEntity target, double attackRadius) {
        if (HeroCombatPursuit.shouldLand(this.hero, target, attackRadius)) {
            this.tickHoveringNearTarget();
            return;
        }

        this.hoverGraceTicks = 0;
        if (!this.hero.isFloating()) {
            // 浮空被外部重置（如入水、传送），回落到地面追击状态
            this.flyingPursuit = false;
            return;
        }

        Vec3 flyTarget = HeroCombatPursuit.flightTarget(this.hero, target, attackRadius);
        HeroCombatPursuit.flyTo(this.hero, flyTarget, HeroCombatPursuit.FLY_SPEED);
    }

    /**
     * 悬停贴近目标：正播放空中攻击则让出时间给连段（衔接空中攻击），
     * 招式收尾后开始悬停计时，超时仍无招式且下方可落地则落回地面进入近战。
     * 不可落地（如悬停于深渊/水面）则保持悬停，交给 AnimatedAttackGoal 空中攻击。
     */
    private void tickHoveringNearTarget() {
        LivingEntity target = this.hero.getTarget();
        double attackRadius = Math.sqrt(this.attackRadiusSqr);
        if (target == null || !HeroCombatPursuit.shouldLand(this.hero, target, attackRadius)) {
            this.hoverGraceTicks = 0;
            return;
        }

        if (HeroEpicFightCompat.isHeroMidAttack(this.hero)) {
            this.hoverGraceTicks = 0;
            this.hero.getNavigation().stop();
            return;
        }
        this.hoverGraceTicks++;
        if (this.hoverGraceTicks >= HOVER_GRACE_TICKS) {
            this.landNearTarget();
        } else {
            this.hero.getNavigation().stop();
        }
    }

    private void landNearTarget() {
        HeroCombatPursuit.land(this.hero);
        this.flyingPursuit = false;
        this.landCooldown = LAND_COOLDOWN_TICKS;
        this.groundPathFailures = 0;
        this.stuckTicks = 0;
        this.hoverGraceTicks = 0;
        HeroEpicFightDebugLog.transition(this.hero, "HeroEpicFightChaseGoal.landNearTarget",
                "flying=false|" + HeroEpicFightDebugLog.heroCoreState(this.hero),
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
                && !HeroCombatPursuit.isWithinAttackReach(this.hero, target, attackRadius);
    }
}
