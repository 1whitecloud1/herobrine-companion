package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;

/**
 * 导航任务：沿寻路移动到目标方块 / 实体，或"回到玩家身边"。
 *
 * <p><b>控制权</b>（交接阶段 B）：本任务<b>不直接写 navigation</b>，只把目标登记到
 * {@link TaskControl}，由 {@code HeroTaskControlGoal} 经 GoalSelector 取得 MOVE/LOOK 后推进。
 * 因此跟随 / 送礼 / 战斗与任务不会同时写导航，战斗抢占时任务暂停并保留进度。</p>
 *
 * <p><b>返回值语义</b>：不可达、目标消失、跨维度、取消、超时分别是不同终态，
 * 不统一压成"失败"——回执要能说清是哪一种。</p>
 */
public final class NavigateTask extends PlannedTask {

    public static final String ID = "navigate";

    /** 默认到达判定距离（格）。 */
    private static final double DEFAULT_ARRIVE_DISTANCE = 2.5D;

    private BlockPos target;
    private UUID targetEntity;
    private boolean followOwner;

    public NavigateTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        this.target = getBlockPos("pos");
        String entity = getString("entity_uuid");
        this.targetEntity = entity.isBlank() ? null : parseUuid(entity);
        this.followOwner = "owner".equalsIgnoreCase(getString("target_type"));

        double speed = getDouble("speed", 1.0D);
        double arrive = getDouble("stop_distance", DEFAULT_ARRIVE_DISTANCE);
        TaskControl control = hero.taskControl();
        if (!control.acquire(holderKey(), hero, hero.level().getGameTime())) {
            return;
        }
        if (this.followOwner) {
            // "回到我身边"：保存玩家身份，执行时解析当前位置，不用接单时的旧坐标。
            UUID owner = hero.getOwnerUUID();
            if (owner != null) {
                control.follow(owner, speed, arrive);
            }
        } else if (this.targetEntity != null) {
            control.follow(this.targetEntity, speed, arrive);
        } else if (this.target != null) {
            control.moveTo(this.target, speed, arrive);
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.followOwner && hero.getOwnerUUID() == null) {
            return TaskState.FAILED;
        }
        if (this.target == null && this.targetEntity == null && !this.followOwner) {
            return TaskState.FAILED;
        }
        if (!hero.taskControl().isHeldBy(holderKey())) {
            // 控制权被战斗 / 挑战拿走：保持 RUNNING，等待 Goal 重新取得（进度不丢）。
            return hero.isBattleModeActive() || hero.isChallengeActiveState()
                    ? TaskState.RUNNING
                    : TaskState.FAILED;
        }
        return hero.taskControl().arrived() ? TaskState.DONE : TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        if (this.followOwner) {
            return "返回玩家身边";
        }
        if (this.targetEntity != null) {
            return "前往实体 " + this.targetEntity;
        }
        return this.target == null ? "导航(无目标)" : "前往 " + posToString(this.target);
    }

    @Override
    public long timeoutTicks() {
        return getLong("timeout_ticks", 20L * 60L);
    }

    @Override
    public int maxAttempts() {
        // 不可达目标重试 2 次后再放弃（避免偶发寻路抖动）。
        return 2;
    }

    private String holderKey() {
        return goalId() == null || goalId().isBlank() ? ID : goalId();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 便捷构造：前往一个方块位置。 */
    public static NavigateTask toPos(BlockPos pos, double speed, String goalId) {
        NavigateTask task = new NavigateTask(Map.of(
                "pos", pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                "speed", String.valueOf(speed)));
        task.setGoalId(goalId);
        return task;
    }

    /** 便捷构造：返回玩家身边（执行时解析当前位置）。 */
    public static NavigateTask toOwner(String goalId, double stopDistance) {
        NavigateTask task = new NavigateTask(Map.of(
                "target_type", "owner",
                "stop_distance", String.valueOf(stopDistance),
                "speed", "1.2"));
        task.setGoalId(goalId);
        return task;
    }

    /** 便捷构造：前往某个实体（跟随其移动）。 */
    public static NavigateTask toEntity(UUID entityUuid, String goalId) {
        NavigateTask task = new NavigateTask(Map.of("entity_uuid", entityUuid.toString()));
        task.setGoalId(goalId);
        return task;
    }
}