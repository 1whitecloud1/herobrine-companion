package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 启动一次真实制作（交接阶段 F 第 3–5 步的"开始"）。
 *
 * <p><b>为什么单独成步</b>：材料转移和启动制作是两件可分别失败的事。
 * 把它们分开，回执就能区分"材料没凑齐"与"材料到了但没开始做"。</p>
 *
 * <p><b>复用</b>：只调用 {@link CookingService#requestCookSelection}，
 * 与 UI / 手动邀请走同一条业务入口；材料扣除、绑定校验、份数限幅都由它负责，
 * 本任务不复制任何配方遍历或材料扣除逻辑。</p>
 *
 * <p>{@code requestCookSelection} 返回 true 只表示<b>已安排动作</b>，完成与否由
 * {@link CookVerifyTask} 观察产物判定。</p>
 */
public final class StartCookTask extends PlannedTask {

    public static final String ID = "start_cook";

    private final BlockPos station;
    private final ResourceLocation recipeId;
    private final int requestedCount;
    private int started;
    private boolean scheduled;

    public StartCookTask(Map<String, String> args) {
        super(ID, args);
        this.station = getBlockPos("pos");
        String recipe = getString("recipe_id");
        this.recipeId = recipe.isBlank() ? null : ResourceLocation.tryParse(recipe);
        this.requestedCount = Math.max(1, getInt("count", 1));
        this.started = 0;
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.scheduled = false;
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (this.station == null || this.recipeId == null) {
            return TaskState.FAILED;
        }
        if (!CookingService.isCookwareStation(hero.level(), this.station)) {
            return TaskState.FAILED;
        }
        // 目标配方必须此刻真实可做：不按名称虚构配方，也不做"先开始再说"的事。
        boolean available = CookingService.getCookOptions(hero, this.station).stream()
                .anyMatch(option -> this.recipeId.equals(option.recipeId()));
        if (!available) {
            return TaskState.FAILED;
        }
        int maxRepeat = CookingService.getCookOptionMaxRepeat(hero, this.station, this.recipeId);
        int actual = Math.min(CookingRules.clampRepeatCount(this.requestedCount), maxRepeat);
        if (actual <= 0) {
            return TaskState.FAILED;
        }
        var requester = resolveRequester(hero, context);
        if (requester == null) {
            return TaskState.FAILED;
        }
        // requestCookSelection 只表示"已安排"，完成由 CookVerifyTask 观察。
        boolean ok = CookingService.requestCookSelection(hero, requester, this.station, this.recipeId, actual);
        this.scheduled = ok;
        this.started = ok ? actual : 0;
        return ok ? TaskState.DONE : TaskState.FAILED;
    }

    private static net.minecraft.server.level.ServerPlayer resolveRequester(HeroEntity hero, AgentTaskContext context) {
        if (hero.getServer() == null) {
            return null;
        }
        var owner = hero.getOwnerUUID();
        return owner == null ? null : hero.getServer().getPlayerList().getPlayer(owner);
    }

    @Override
    public String progressText() {
        return scheduled ? "已安排制作 " + started + " 份" : "启动制作 " + (recipeId == null ? "?" : recipeId);
    }

    @Override
    public int maxAttempts() {
        return 1;
    }

    @Override
    public long timeoutTicks() {
        return 20L * 10L;
    }

    @Override
    public long noProgressTicks() {
        return 0L;
    }

    /** 便捷构造。 */
    public static StartCookTask at(BlockPos station, ResourceLocation recipeId, int count, String goalId) {
        StartCookTask task = new StartCookTask(Map.of(
                "pos", station.getX() + "," + station.getY() + "," + station.getZ(),
                "recipe_id", recipeId.toString(),
                "count", String.valueOf(count)));
        task.setGoalId(goalId);
        return task;
    }

    /** 便于上层挑一个真实可做的配方（不虚构）。 */
    public static CookOption chooseRecipe(HeroEntity hero, BlockPos station) {
        var options = CookingService.getCookOptions(hero, station);
        return options.isEmpty() ? null : options.get(0);
    }
}
