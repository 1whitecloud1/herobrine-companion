package com.whitecloud233.modid.herobrine_companion.entity.ai.combat;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 战斗模式追击辅助：统一处理「远距冲刺」与「飞行追击」的决策与飞行力学。
 *
 * <p>原版 {@code GroundPathNavigation} 在目标位于高处或被方块隔断时常常出不来路径，
 * 导致 Hero 贴墙 / 贴崖底原地发呆。此类把追击决策收口为单一职责：</p>
 *
 * <ul>
 *     <li><b>冲刺</b>：离目标足够远时把导航速度提高到 1.5 倍并触发冲刺标记
 *     （Epic Fight 的 {@code HumanoidMobPatch} 据此播放 RUN 动作）。</li>
 *     <li><b>飞行追击</b>：目标在高处、地面寻路连续失败或卡墙时切换浮空，
 *     直线逼近目标。巡航高度按路径上最高阻挡物抬升以越过地形，撞墙时
 *     {@code HeroMoveControl} 悬浮分支自带的抬升力也能翻墙；接近后若能落到
 *     可攻击的地面则回落到地面进入近战。</li>
 * </ul>
 */
public final class HeroCombatPursuit {
    // ---- 冲刺 ----
    private static final double SPRINT_TRIGGER_DISTANCE = 8.0D;
    private static final double BASE_CHASE_SPEED = 1.0D;
    private static final double SPRINT_CHASE_SPEED = 1.5D;

    // ---- 飞行追击 ----
    private static final double FLY_UP_THRESHOLD = 3.0D;
    private static final double FLY_HOVER_OFFSET = 2.0D;
    private static final double FLY_ATTACK_OFFSET = 0.5D;
    public static final double FLY_SPEED = 1.0D;
    /** 战斗飞行垂直推进：最大爬升 / 下降速度（MoveControl 悬浮分支独立使用，远距水平飞行时不再被稀释）。 */
    public static final double FLY_CLIMB_SPEED = 0.9D;
    public static final double FLY_DESCENT_SPEED = 0.6D;
    /** 距目标高度进入该垂直带后线性收尾，平滑悬停在目标高度。 */
    public static final double FLY_VERTICAL_EASE = 0.4D;
    private static final double LAND_HORIZONTAL_LEEWAY = 1.5D;
    private static final double LAND_DESCENT_DISTANCE = 6.0D;
    private static final double LAND_VERTICAL_BAND = 2.5D;
    /** 落地后目标脚部与本体脚部允许的最大高度差：再高就够不着，保持悬停等空中连段 */
    private static final double LAND_REACHABLE_HEIGHT = 2.0D;
    private static final double OBSTACLE_SAMPLE_STEP = 4.0D;
    public static final int GROUND_PATH_FAIL_LIMIT = 3;

    private HeroCombatPursuit() {
    }

    // ==================== 冲刺 ====================

    public static boolean shouldSprint(HeroEntity hero, LivingEntity target) {
        return horizontalDistanceSqr(hero, target) > SPRINT_TRIGGER_DISTANCE * SPRINT_TRIGGER_DISTANCE;
    }

    /** 根据距离返回追击速度，并同步冲刺标记（供 Epic Fight RUN 动作使用）。 */
    public static double chaseSpeed(HeroEntity hero, LivingEntity target) {
        boolean sprint = shouldSprint(hero, target);
        hero.setSprinting(sprint);
        return sprint ? SPRINT_CHASE_SPEED : BASE_CHASE_SPEED;
    }

    // ==================== 飞行决策 ====================

    public static boolean isTargetHighAbove(HeroEntity hero, LivingEntity target) {
        return target.getY() - hero.getY() > FLY_UP_THRESHOLD;
    }

    /** Hero 是否身处流体（水/岩浆）。战斗模式下遇流体应飞起越障，而非游泳。 */
    public static boolean isHeroInFluid(HeroEntity hero) {
        return hero != null && (hero.isInWater() || hero.isInLava());
    }

    /**
     * 是否应进入飞行追击：身处流体、已经浮空、目标高出 Hero 较多、或地面寻路连续失败（含卡墙）。
     *
     * @param groundPathFailures 地面寻路连续失败的次数，达到 {@link #GROUND_PATH_FAIL_LIMIT} 即起飞
     */
    public static boolean shouldTakeFlight(HeroEntity hero, LivingEntity target, int groundPathFailures) {
        if (hero == null || target == null || hero.isPassenger()) {
            return false;
        }
        // 遇流体（水/岩浆）立即起飞越障，而不是泡在水里游泳
        if (isHeroInFluid(hero)) {
            return true;
        }
        if (hero.isFloating()) {
            return true;
        }
        if (isTargetHighAbove(hero, target)) {
            return true;
        }
        return groundPathFailures >= GROUND_PATH_FAIL_LIMIT;
    }

    // ==================== 飞行力学 ====================

    /** 进入浮空：切飞行导航、停掉地面路径、清横向动量。 */
    public static void startFlight(HeroEntity hero) {
        if (!hero.isFloating()) {
            hero.setFloating(true);
        }
        hero.getNavigation().stop();
        hero.setDeltaMovement(0.0D, hero.getDeltaMovement().y, 0.0D);
        hero.setSprinting(false);
    }

    /**
     * 计算飞行目标点：远距时沿路径抬升巡航高度以越过阻挡物，近距时下探到目标身侧准备攻击。
     */
    public static Vec3 flightTarget(HeroEntity hero, LivingEntity target, double attackRadius) {
        boolean horizontallyClose = horizontalDistanceSqr(hero, target) <= square(attackRadius + LAND_HORIZONTAL_LEEWAY);
        if (horizontallyClose) {
            return new Vec3(target.getX(), target.getY() + FLY_ATTACK_OFFSET, target.getZ());
        }
        double clearY = maxGroundObstacleY(hero, target) + FLY_HOVER_OFFSET;
        double y = Math.max(target.getY() + FLY_HOVER_OFFSET, clearY);
        return new Vec3(target.getX(), y, target.getZ());
    }

    /** 走 MoveControl 悬浮分支飞往指定点（导航会与 moveControl 打架，飞行中必须走 moveControl）。 */
    public static void flyTo(HeroEntity hero, Vec3 target, double speed) {
        hero.getNavigation().stop();
        hero.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
    }

    /**
     * 战斗模式身处流体（水/岩浆）时的脱困悬停：无论有无目标都主动飞出液面，
     * 悬停在液面之上 {@link #FLY_HOVER_OFFSET} 格，避免只靠浮力泡在水面被 FloatGoal 弹跳。
     * 有可追击目标时由飞行追击（flyTo）覆盖 wanted position 去追目标，此处仅在无目标时兜底。
     */
    public static void hoverAboveFluid(HeroEntity hero) {
        if (hero == null || !hero.isBattleModeActive() || !isHeroInFluid(hero)) {
            return;
        }
        if (!hero.isFloating()) {
            startFlight(hero);
        }
        Level level = hero.level();
        BlockPos pos = hero.blockPosition();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        double hoverY = surfaceY + FLY_HOVER_OFFSET;
        if (Math.abs(hero.getY() - hoverY) > 0.5D) {
            flyTo(hero, new Vec3(hero.getX(), hoverY, hero.getZ()), 1.0D);
        }
    }

    /**
     * 是否可在当前位置降落：水平已贴近目标、与目标高度差不大、下方若干格内有可站立地面，
     * 且落地后的高度差仍在攻击半径内（避免落到崖底却够不着目标）。
     */
    public static boolean shouldLand(HeroEntity hero, LivingEntity target, double attackRadius) {
        // 遇流体（水/岩浆）保持飞行越障，不在流体里落地
        if (isHeroInFluid(hero)) {
            return false;
        }
        if (horizontalDistanceSqr(hero, target) > square(attackRadius + LAND_HORIZONTAL_LEEWAY)) {
            return false;
        }
        if (Math.abs(target.getY() - hero.getY()) > LAND_VERTICAL_BAND) {
            return false;
        }
        double groundY = solidGroundY(hero, LAND_DESCENT_DISTANCE);
        if (groundY < 0.0D) {
            return false;
        }
        // 落点若是流体（浅水/岩浆）也拒绝落地，保持悬停
        Level level = hero.level();
        BlockPos standPos = BlockPos.containing(hero.getX(), groundY + 1.0D, hero.getZ());
        if (!level.getBlockState(standPos).getFluidState().isEmpty()) {
            return false;
        }
        double verticalDiffAfterLand = Math.abs(target.getY() - (groundY + 1.0D));
        // 收紧高度带：只允许落到"站定后仍够得着目标"的地面。树顶/崖顶这类落地就够不着的
        // 场景拒绝落地，避免飞上去又掉下来反复弹跳（空中连段交给悬停态）
        return verticalDiffAfterLand <= LAND_REACHABLE_HEIGHT;
    }

    /**
     * 是否已进入"贴脸可近战"的范围：水平贴近 + 垂直差小。
     * 高处目标（垂直差大）即使 3D 距离很近也不算在射程内，留给飞行追击逼近到悬停点，
     * 避免 Hero 站在树下把够不着的怪当成在射程内发呆 / 空挥。
     */
    public static boolean isWithinAttackReach(HeroEntity hero, LivingEntity target, double attackRadius) {
        if (horizontalDistanceSqr(hero, target) > square(attackRadius)) {
            return false;
        }
        return Math.abs(target.getY() - hero.getY()) <= LAND_VERTICAL_BAND;
    }

    /** 落地：关闭浮空让重力接管，停路径、清动量、清冲刺。 */
    public static void land(HeroEntity hero) {
        if (hero.isFloating()) {
            hero.setFloating(false);
        }
        if (hero.isNoGravity()) {
            hero.setNoGravity(false);
        }
        hero.getNavigation().stop();
        hero.setDeltaMovement(hero.getDeltaMovement().x * 0.2D, -0.12D, hero.getDeltaMovement().z * 0.2D);
        hero.setSprinting(false);
    }

    /**
     * 战斗飞行收尾：目标已消失 / 已能贴地命中时，让悬停的 Hero 落回地面。
     * 由 {@code HeroEpicFightPatch.serverTick} 每 server tick 调用，
     * 防止飞行追击被 AnimatedAttackGoal 抢占后 Hero 永久悬空。
     *
     * @param midAttack 是否正在播放攻击动作（悬空连段中不强行落地打断）
     */
    public static void landHeroIfCombatIdle(HeroEntity hero, LivingEntity target, double attackRadius, boolean midAttack) {
        if (hero == null || !hero.isFloating() || !hero.isBattleModeActive() || midAttack) {
            return;
        }
        boolean targetGone = target == null || !target.isAlive() || target.isRemoved();
        boolean reachableFromGround = !targetGone && shouldLand(hero, target, attackRadius);
        if (targetGone || reachableFromGround) {
            land(hero);
        }
    }

    // ==================== 内部工具 ====================

    public static double horizontalDistanceSqr(HeroEntity hero, LivingEntity target) {
        double dx = target.getX() - hero.getX();
        double dz = target.getZ() - hero.getZ();
        return dx * dx + dz * dz;
    }

    /** 采样 Hero 到目标这条水平线段上最高阻挡物高度，用于巡航高度越障。 */
    private static double maxGroundObstacleY(HeroEntity hero, LivingEntity target) {
        Level level = hero.level();
        double maxY = Math.max(hero.getY(), target.getY());
        double dx = target.getX() - hero.getX();
        double dz = target.getZ() - hero.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, (int) (dist / OBSTACLE_SAMPLE_STEP));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            // WORLD_SURFACE 含流体表面（水面/岩浆面）：越过河流/岩浆湖时巡航高度抬到液面之上，
            // 避免 Hero 飞到液面以下变成游泳
            int groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE,
                    Mth.floor(hero.getX() + dx * t), Mth.floor(hero.getZ() + dz * t));
            maxY = Math.max(maxY, groundY);
        }
        return maxY;
    }

    /** 向下扫描最近的站立地面（地面实心 + 头顶留空），返回地面方块 Y，找不到返回 -1。 */
    private static double solidGroundY(HeroEntity hero, double maxDescent) {
        Level level = hero.level();
        BlockPos feet = BlockPos.containing(hero.getX(), hero.getY() - 0.1D, hero.getZ());
        int maxSteps = Mth.floor(maxDescent);
        for (int i = 1; i <= maxSteps; i++) {
            BlockPos pos = feet.below(i);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                BlockState standSpace = level.getBlockState(pos.above());
                if (standSpace.getCollisionShape(level, pos.above()).isEmpty()) {
                    return pos.getY();
                }
            }
        }
        return -1.0D;
    }

    private static double square(double value) {
        return value * value;
    }
}
