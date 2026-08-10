package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 探查区域任务（P4）：优先走向最近的异常方块"现场查看"；无异常时走向锚点偏移点做巡视。
 * 到达后返回 DONE，由规划器追加的 {@link ReportTask} 汇报结果。
 *
 * <p><b>单一职责</b>：只做"定目标 → 导航"，不替汇报负责；扫描复用 {@link HeroAnomalySupport}。</p>
 */
public final class InspectAreaTask extends PlannedTask {

    public static final String ID = "inspect_area";

    private static final double ARRIVE_SQR = 6.25D;

    private BlockPos target;
    private boolean nothingToDo;

    public InspectAreaTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        var hero = context.hero();
        var found = HeroAnomalySupport.findGlitchBlocks(hero, 1);
        if (!found.isEmpty()) {
            this.target = found.get(0);
            this.nothingToDo = false;
        } else {
            // 无异常 → 静默完成，不散步、不播报（避免"检查完成"刷屏）。
            this.target = null;
            this.nothingToDo = true;
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.nothingToDo || this.target == null) {
            return TaskState.DONE;
        }
        double distSqr = hero.blockPosition().distSqr(this.target);
        if (distSqr <= ARRIVE_SQR) {
            // 确实发现了异常才汇报。
            ReportTask.send(context, "检查完成，发现异常方块", false);
            this.target = null;
            return TaskState.DONE;
        }
        hero.getNavigation().moveTo(this.target.getX(), this.target.getY(), this.target.getZ(), 1.0D);
        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        if (target == null) {
            return "探查区域";
        }
        return "前往异常点 " + posToString(target);
    }
}
