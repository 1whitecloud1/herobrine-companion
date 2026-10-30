package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.TaskControl;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 任务执行 Goal（交接阶段 B）：让多步任务经 <b>GoalSelector 的 MOVE/LOOK 互斥</b>驱动移动，
 * 而不是各自往 {@code hero.getNavigation()} 里写目标。
 *
 * <p><b>为什么这样做</b>：跟随、送礼、巡视、战斗都是 Goal，它们和任务同抢一个导航时，
 * Minecraft 本身并不知道该听谁的。把任务也变成一个 Goal，仲裁就回到游戏本体：
 * 谁拿到 MOVE/LOOK，谁就能写导航；失去控制权的一方只会被 {@code stop()}，
 * 不会两边交替拉扯。</p>
 *
 * <p><b>优先级</b>：本 Goal 注册在 {@link #PRIORITY}（2），与跟随同级、低于战斗与
 * 挑战（1）。战斗需要时可以直接按优先级抢走控制权，战斗结束任务继续——
 * 而不是像旧实现那样在队列里"暂停等待战斗态"、战斗期间玩家指令全被吞掉。</p>
 *
 * <p><b>单一职责</b>：只把 {@link TaskControl} 的目标翻译成导航调用与到达判定；
 * 任务的步骤、重试、暂停由 {@code HeroTaskQueue} 决定。</p>
 */
public final class HeroTaskControlGoal extends Goal {

    /**
     * 优先级 = 1（与战斗 / 玩家显式动作同级，高于跟随的 2）。
     *
     * <p>必须高于跟随：否则同优先级下跟随 Goal 先注册、先被 GoalSelector 评估，
     * 任务永远拿不到 MOVE/LOOK，"到此为止"就会被"贴着你走"覆盖。
     * 战斗与挑战仍然优先——本 Goal 在 {@link #canUse()} 里主动让出。</p>
     */
    public static final int PRIORITY = 1;

    private final HeroEntity hero;

    public HeroTaskControlGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive() || this.hero.isChallengeActiveState()) {
            // 战斗 / 挑战优先：让出控制权，任务在队列里进入"暂停"，不会丢进度。
            return false;
        }
        return this.hero.isTaskMovementActive() && this.hero.taskControl().isHeld();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.hero.taskControl().setArrived(false);
    }

    @Override
    public void stop() {
        // 失去控制权：停导航但不释放凭据（目标与进度留给队列，稍后恢复）。
        this.hero.getNavigation().stop();
    }

    @Override
    public void tick() {
        TaskControl control = this.hero.taskControl();
        if (!control.isHeld()) {
            return;
        }
        double distanceSqr = resolveAndDrive(control);
        if (distanceSqr < 0.0D) {
            return;
        }
        control.recordDistance(distanceSqr, this.hero.level().getGameTime());
        double arrive = control.arriveDistance();
        if (distanceSqr <= arrive * arrive) {
            control.setArrived(true);
            this.hero.getNavigation().stop();
        }
    }

    /**
     * 解析当前目标并驱动导航。
     *
     * @return 到目标的距离平方；目标无法解析（实体消失 / 未加载）返回 -1
     */
    private double resolveAndDrive(TaskControl control) {
        if (control.followEntity() && control.targetEntityUuid() != null) {
            // "回到我身边"必须每次解析玩家当前位置，不能用接单时的旧坐标。
            var target = this.hero.level().getPlayerByUUID(control.targetEntityUuid());
            if (target == null) {
                return -1.0D;
            }
            double distanceSqr = this.hero.distanceToSqr(target);
            if (distanceSqr > 0.04D) {
                this.hero.getNavigation().moveTo(target, control.speed());
            }
            // 边走边看：跟随实体的任务需要朝向目标，避免背对着走。
            this.hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return distanceSqr;
        }
        var pos = control.targetPos();
        if (pos == null) {
            var vec = control.targetVec();
            if (vec == null) {
                return -1.0D;
            }
            double distanceSqr = this.hero.distanceToSqr(vec);
            this.hero.getNavigation().moveTo(vec.x, vec.y, vec.z, control.speed());
            return distanceSqr;
        }
        double distanceSqr = this.hero.blockPosition().distSqr(pos);
        if (!this.hero.getNavigation().isInProgress()) {
            this.hero.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, control.speed());
        }
        return distanceSqr;
    }
}
