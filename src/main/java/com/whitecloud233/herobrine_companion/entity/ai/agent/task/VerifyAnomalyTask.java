package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 复查异常是否已被处理（交接阶段 C 第 5 步）。
 *
 * <p><b>为什么单独成步</b>：净化是异步的（{@link HeroAnomalySupport#performAreaCleanse}
 * 会在 5 tick 后清理方块）。把"复查"拆成独立步骤，回执就能区分
 * "我净化了" 与 "复查确认已经处理"；如果期间被别人处理掉，也据实完成该步，
 * 而不是永远等待或谎报成功。</p>
 *
 * <p><b>单一职责</b>：只读判定 + 有限等待，不改世界。</p>
 */
public final class VerifyAnomalyTask extends PlannedTask {

    public static final String ID = "verify_anomaly";

    /** 净化是延迟执行的（5 tick），给它一个有限的复查窗口。 */
    private static final long VERIFY_WINDOW_TICKS = 20L * 20L;

    private BlockPos target;
    private long startAt = -1L;

    public VerifyAnomalyTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.target = getBlockPos("pos");
        this.startAt = context.hero().level().getGameTime();
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.target == null) {
            return TaskState.FAILED;
        }
        if (!HeroAnomalySupport.isGlitchBlock(hero.level(), this.target, hero.level().getBlockState(this.target))) {
            // 已被处理（我净化的，或别人先处理的）：据实完成该步。
            ReportTask.send(context, "复查完成：该处异常已清除", false);
            return TaskState.DONE;
        }
        if (hero.level().getGameTime() - this.startAt > VERIFY_WINDOW_TICKS) {
            // 有限窗口内仍是异常：如实报告部分完成，不谎称成功。
            ReportTask.send(context, "复查：该处异常仍然存在", false);
            return TaskState.FAILED;
        }
        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        return target == null ? "复查异常(无目标)" : "复查 " + posToString(target);
    }

    /** 只读步骤，重试没有意义；失败即如实结束。 */
    @Override
    public int maxAttempts() {
        return 1;
    }

    @Override
    public long timeoutTicks() {
        return VERIFY_WINDOW_TICKS + 20L;
    }

    @Override
    public long noProgressTicks() {
        return 0L;
    }
}
