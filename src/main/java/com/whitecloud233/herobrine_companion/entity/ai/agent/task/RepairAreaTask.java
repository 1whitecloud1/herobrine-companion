package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 修复区域任务（P4）：扫描周围异常方块，导航到最近一个并执行 AOE 净化。
 * 无异常方块时视为已完成（无事可修）。
 *
 * <p><b>单一职责</b>：只做"扫描 → 导航 → 净化"编排；扫描/净化逻辑复用
 * {@link HeroAnomalySupport}，不重复实现。</p>
 */
public final class RepairAreaTask extends PlannedTask {

    public static final String ID = "repair_area";

    /** 到达判定距离平方（≈2.5 格）。 */
    private static final double ARRIVE_SQR = 6.25D;

    private BlockPos target;
    private boolean nothingToDo;

    public RepairAreaTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.target = HeroAnomalySupport.findGlitchBlock(context.hero());
        this.nothingToDo = this.target == null;
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.nothingToDo) {
            // 没有异常方块 → 无事可修，静默完成（不播报）。
            return TaskState.DONE;
        }
        double distSqr = hero.blockPosition().distSqr(this.target);
        if (distSqr <= ARRIVE_SQR) {
            HeroAnomalySupport.performAreaCleanse(hero, this.target);
            this.target = null;
            // 确实修复了才汇报，避免固定尾随播报刷屏。
            ReportTask.send(context, "该区域已修复完毕", false);
            return TaskState.DONE;
        }
        hero.getNavigation().moveTo(this.target.getX(), this.target.getY(), this.target.getZ(), 1.2D);
        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        return target == null ? "修复区域(扫描中)" : "修复 " + posToString(target);
    }

    @Override
    public int maxAttempts() {
        return 2;
    }

    /** 修复可能耗时较长，给足超时。 */
    @Override
    public long timeoutTicks() {
        return 20L * 60L;
    }
}
