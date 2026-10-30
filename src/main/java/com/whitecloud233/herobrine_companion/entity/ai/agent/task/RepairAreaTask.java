package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 修复区域任务：扫描周围异常方块，走到可处理位置并执行 AOE 净化。
 *
 * <p><b>单一职责</b>：只做"扫描 → 申请移动控制 → 到达后净化"编排；
 * 扫描/净化逻辑复用 {@link HeroAnomalySupport}，不重复实现。</p>
 *
 * <p><b>生命周期</b>（交接 B/A4）：移动经 {@link TaskControl} + GoalSelector 仲裁，
 * 结束 / 取消 / 失败都会释放控制权；无进展与超时都有上限，不会永久占队。</p>
 */
public final class RepairAreaTask extends PlannedTask {

    public static final String ID = "repair_area";

    /** 到达后仍需贴近处理的距离（格）。 */
    private static final double ARRIVE_DISTANCE = 2.5D;

    private BlockPos target;
    private boolean nothingToDo;
    private boolean cleansed;

    public RepairAreaTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        this.cleansed = false;
        this.target = getBlockPos("pos");
        if (this.target == null) {
            this.target = HeroAnomalySupport.findGlitchBlock(hero);
        }
        this.nothingToDo = this.target == null;
        if (this.nothingToDo) {
            return;
        }
        TaskControl control = hero.taskControl();
        if (control.acquire(holderKey(), hero, hero.level().getGameTime())) {
            control.moveTo(this.target, 1.2D, ARRIVE_DISTANCE);
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.nothingToDo) {
            // 没有异常方块 → 无事可修，静默完成（不播报）。
            return TaskState.DONE;
        }
        if (this.cleansed) {
            return TaskState.DONE;
        }
        // 目标在处理前被别人修掉了：据实完成该步，不算失败。
        if (HeroAnomalySupport.findGlitchBlock(hero) == null && hero.blockPosition().distSqr(this.target) <= ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            ReportTask.send(context, "该区域已被处理", false);
            return TaskState.DONE;
        }
        if (hero.taskControl().isHeldBy(holderKey()) && hero.taskControl().arrived()) {
            // 玩家标记为"我家"的区域不做净化：这是**有依据的拒绝**，不是完成。
            // 若这里照旧报"已修复完毕"，就是在虚报成功——异常还在，玩家却被骗了。
            if (HeroAnomalySupport.isProtectedByOwnerPreference(hero, hero.level(), this.target)) {
                ReportTask.send(context, "这一带是你标记过的地方，我没有动它", false);
                return TaskState.FAILED;
            }
            HeroAnomalySupport.performAreaCleanse(hero, this.target);
            this.cleansed = true;
            // 确实修复了才汇报，避免固定尾随播报刷屏。
            ReportTask.send(context, "该区域已修复完毕", false);
            return TaskState.DONE;
        }
        return TaskState.RUNNING;
    }

    @Override
    public String progressText() {
        return this.target == null ? "修复区域(扫描中)" : "修复 " + posToString(this.target);
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

    private String holderKey() {
        return goalId() == null || goalId().isBlank() ? ID : goalId();
    }
}
