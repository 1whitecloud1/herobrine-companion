package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 探查异常任务：走向最近的异常方块"现场查看"，到达后据实汇报。
 *
 * <p><b>单一职责</b>：只做"定目标 → 申请移动控制 → 到达汇报"，扫描复用 {@link HeroAnomalySupport}。</p>
 *
 * <p><b>有界</b>（交接 A4）：旧实现没有超时、不处理寻路提交失败，异常点不可达时会一直
 * {@code moveTo} 并返回 RUNNING，把后续任务永久堵死。现在移动经 GoalSelector 仲裁、
 * 有超时与无进展判定，失败后释放控制权并让队列前进。</p>
 */
public final class InspectAreaTask extends PlannedTask {

    public static final String ID = "inspect_area";

    /** 到达判定距离（格）。 */
    private static final double ARRIVE_DISTANCE = 2.5D;

    private BlockPos target;
    private boolean nothingToDo;

    public InspectAreaTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        this.target = getBlockPos("pos");
        if (this.target == null) {
            var found = HeroAnomalySupport.findGlitchBlocks(hero, 1);
            this.target = found.isEmpty() ? null : found.get(0);
        }
        this.nothingToDo = this.target == null;
        if (this.nothingToDo) {
            // 无异常 → 静默完成，不散步、不播报（避免"检查完成"刷屏）。
            return;
        }
        TaskControl control = hero.taskControl();
        if (control.acquire(holderKey(), hero, hero.level().getGameTime())) {
            control.moveTo(this.target, 1.0D, ARRIVE_DISTANCE);
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.nothingToDo || this.target == null) {
            return TaskState.DONE;
        }
        // 异常在途中被别人处理掉：据实完成，不盲用旧坐标。
        if (!HeroAnomalySupport.isGlitchBlock(hero.level(), this.target, hero.level().getBlockState(this.target))) {
            ReportTask.send(context, "异常已被处理，无需再检查", false);
            return TaskState.DONE;
        }
        if (hero.taskControl().isHeldBy(holderKey()) && hero.taskControl().arrived()) {
            // 确实发现了异常才汇报。
            ReportTask.send(context, "检查完成，发现异常方块", false);
            return TaskState.DONE;
        }
        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        if (target == null) {
            return "探查区域";
        }
        return "前往异常点 " + posToString(target);
    }

    @Override
    public int maxAttempts() {
        return 2;
    }

    /** 有界：不可达时在预算内结束，后续任务仍可执行（交接 A4 验收 T09）。 */
    @Override
    public long timeoutTicks() {
        return getLong("timeout_ticks", 20L * 45L);
    }

    @Override
    public long noProgressTicks() {
        return 20L * 15L;
    }

    private String holderKey() {
        return goalId() == null || goalId().isBlank() ? ID : goalId();
    }
}
