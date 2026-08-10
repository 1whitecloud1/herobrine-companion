package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 导航任务：沿寻路移动到目标方块。到达或超时结束；寻路失败（堵死）判定为失败。
 */
public final class NavigateTask extends PlannedTask {

    public static final String ID = "navigate";

    /** 到达判定：目标中心距离平方阈值（≈2.5 格）。 */
    private static final double ARRIVE_SQR = 6.25D;

    private BlockPos target;

    public NavigateTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.target = getBlockPos("pos");
        double speed = parseDouble(getString("speed"), 1.0D);
        if (this.target != null) {
            context.hero().getNavigation().moveTo(this.target.getX(), this.target.getY(), this.target.getZ(), speed);
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.target == null) {
            return TaskState.FAILED;
        }

        double distSqr = hero.blockPosition().distSqr(this.target);
        if (distSqr <= ARRIVE_SQR) {
            return TaskState.DONE;
        }

        long elapsed = hero.level().getGameTime() - startTick();
        int timeout = getInt("timeout_ticks", 200);
        if (elapsed > timeout) {
            return TaskState.FAILED;
        }

        // 寻路已结束但还没到（堵死 / 不可达），判定失败而不是空耗。
        if (elapsed > 40 && hero.getNavigation().isDone()) {
            return TaskState.FAILED;
        }

        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        return target == null ? "导航(无目标)" : "前往 " + posToString(target);
    }

    @Override
    public int maxAttempts() {
        // 不可达目标重试 2 次后再放弃（避免偶发寻路抖动）。
        return 2;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}