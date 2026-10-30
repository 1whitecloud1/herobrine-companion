package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 检查厨具是否真的产出了目标产物（交接阶段 F 第 5 步）。
 *
 * <p><b>为什么不能靠"已开始"判定完成</b>：{@code requestCookSelection} 返回 true 只表示
 * 动作已安排。本任务在有限窗口内观察厨具产物 / Hero 手持，只有<b>真的出现产物</b>才算完成；
 * 窗口内没出现就如实失败并说明"仍未看到产物"。</p>
 *
 * <p><b>单一职责</b>：只读观察 + 有限等待，不复制配方逻辑、不扣材料。</p>
 */
public final class CookVerifyTask extends PlannedTask {

    public static final String ID = "cook_verify";

    /** 做饭窗口：份数越多越久，给一个与份数相关但有上限的观察窗。 */
    private static final long BASE_WINDOW_TICKS = 20L * 30L;

    private final BlockPos station;
    private final int expectedCount;
    private long startAt = -1L;
    private int observed;
    /** 开工时手上已有的可食用物品数量（食材基线，不能算作成品）。 */
    private int baseline;

    public CookVerifyTask(Map<String, String> args) {
        super(ID, args);
        this.station = getBlockPos("pos");
        this.expectedCount = Math.max(1, getInt("count", 1));
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.startAt = context.hero().level().getGameTime();
        this.observed = 0;
        // 记录开工时手上的可食用物品作为基线：生的土豆/胡萝卜本身就是 isEdible，
        // 若不减去基线，"手上本来就有食材"会被误判成"已经做出成品"（虚假成功）。
        this.baseline = countProducedInHands(context.hero());
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.station == null) {
            return TaskState.FAILED;
        }
        if (!CookingService.isCookwareStation(hero.level(), this.station)) {
            // 厨具被拆掉 / 换掉：据实失败，不盲目继续。
            return TaskState.FAILED;
        }
        // 只有**相对开工时有新增**的可食用产物才算做出来了；原有食材不算成品。
        this.observed = Math.max(0, countProducedInHands(hero) - this.baseline);
        if (this.observed >= this.expectedCount) {
            ReportTask.send(context, "制作完成，产物已在手上", false);
            return TaskState.DONE;
        }
        if (hero.level().getGameTime() - this.startAt > window()) {
            ReportTask.send(context, "制作窗口结束，仍只看到 " + this.observed + "/" + this.expectedCount + " 份产物", false);
            return TaskState.FAILED;
        }
        return TaskState.RUNNING;
    }

    /** 观察窗口随份数增长，但有上限（避免长任务无限期挂着）。 */
    private long window() {
        return Math.min(BASE_WINDOW_TICKS + (long) this.expectedCount * 20L * 10L, 20L * 60L * 5L);
    }

    /** 只数可食用产物（避免把工具/材料误当成成品）。 */
    private static int countProducedInHands(HeroEntity hero) {
        int total = 0;
        if (!hero.getMainHandItem().isEmpty() && hero.getMainHandItem().isEdible()) {
            total += hero.getMainHandItem().getCount();
        }
        if (!hero.getOffhandItem().isEmpty() && hero.getOffhandItem().isEdible()) {
            total += hero.getOffhandItem().getCount();
        }
        return total;
    }

    @Override
    public String progressText() {
        return "检查产物 " + observed + "/" + expectedCount;
    }

    @Override
    public int maxAttempts() {
        return 1;
    }

    @Override
    public long timeoutTicks() {
        return window() + 20L;
    }

    @Override
    public long noProgressTicks() {
        return 0L;
    }

    /** 依赖运行时手持状态：恢复后不能盲目重放，交给玩家重新确认。 */
    @Override
    public boolean discardOnRestore() {
        return true;
    }

    /** 便捷构造：观察某厨具的产物。 */
    public static CookVerifyTask at(BlockPos station, int expected, String goalId) {
        CookVerifyTask task = new CookVerifyTask(Map.of(
                "pos", station.getX() + "," + station.getY() + "," + station.getZ(),
                "count", String.valueOf(expected)));
        task.setGoalId(goalId);
        return task;
    }

    /** 便于上层判断"这个厨具当前有哪些真实可做配方"。 */
    public static CookOption firstOption(HeroEntity hero, BlockPos station) {
        var options = CookingService.getCookOptions(hero, station);
        return options.isEmpty() ? null : options.get(0);
    }
}
