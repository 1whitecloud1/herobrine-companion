package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroAnomalySupport;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 多步目标工厂：把"一句话目标"翻译成一段可执行、可暂停、可存档的任务序列（交接阶段 C/F）。
 *
 * <p><b>为什么集中一处</b>：修复 / 探查 / 返回 / 做饭交付这几条链路都要"先走、再做事、再回来"，
 * 各写一遍就会漂移出自己的距离阈值和清理顺序。这里只负责编排步骤，
 * 每个步骤的具体动作仍由各自的 {@link PlannedTask} 复用既有业务
 * （{@link HeroAnomalySupport}、{@link CookingService} 等）。</p>
 */
public final class TaskGoals {

    /** "处理异常并返回"目标里，返回玩家时的停止距离（格）。 */
    public static final double RETURN_STOP_DISTANCE = 3.0D;

    private TaskGoals() {
    }

    /** 生成一个新的目标 ID。 */
    public static String newGoalId(String prefix) {
        return (prefix == null || prefix.isBlank() ? "goal" : prefix) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** "处理前面的一处异常，然后回到我身边"（阶段 C 闭环）。 */
    public static List<PlannedTask> handleAnomalyThenReturn(HeroEntity hero, BlockPos anchor, String goalId) {
        List<PlannedTask> steps = handleAnomaly(hero, anchor, goalId);
        if (steps.isEmpty()) {
            return List.of();
        }
        return List.of(steps.get(0), steps.get(1), NavigateTask.toOwner(goalId, RETURN_STOP_DISTANCE));
    }

    /** 只做"前往并处理一处异常"（不含返回）。 */
    public static List<PlannedTask> handleAnomaly(HeroEntity hero, BlockPos anchor, String goalId) {
        BlockPos target = anchor;
        if (target == null && hero != null) {
            target = HeroAnomalySupport.findGlitchBlock(hero);
        }
        if (target == null) {
            return List.of();
        }
        String pos = target.getX() + "," + target.getY() + "," + target.getZ();
        PlannedTask repair = new RepairAreaTask(Map.of("pos", pos));
        repair.setGoalId(goalId);
        PlannedTask verify = new VerifyAnomalyTask(Map.of("pos", pos));
        verify.setGoalId(goalId);
        return List.of(repair, verify);
    }

    /** "到我身边来"（步行导航，不传送）。 */
    public static List<PlannedTask> returnToOwner(String goalId) {
        return List.of(NavigateTask.toOwner(goalId, RETURN_STOP_DISTANCE));
    }

    /** 前往一个明确位置。 */
    public static List<PlannedTask> goTo(BlockPos pos, String goalId) {
        return pos == null ? List.of() : List.of(NavigateTask.toPos(pos, 1.0D, goalId));
    }

    /** 前往一个实体（跟随其移动）。 */
    public static List<PlannedTask> follow(UUID entityUuid, String goalId) {
        return entityUuid == null ? List.of() : List.of(NavigateTask.toEntity(entityUuid, goalId));
    }

    /** "用箱子的材料做几份能做的食物，然后拿给我"（阶段 F 第二条完整目标）。 */
    public static List<PlannedTask> cookAndDeliver(HeroEntity hero, BlockPos station,
                                                   ResourceLocation recipeId, int count, String goalId) {
        if (hero == null || station == null || recipeId == null
                || !CookingService.isCookwareStation(hero.level(), station)) {
            return List.of();
        }
        int maxRepeat = CookingService.getCookOptionMaxRepeat(hero, station, recipeId);
        if (maxRepeat <= 0) {
            return List.of();
        }
        int actual = Math.min(Math.max(1, count), maxRepeat);
        return List.of(
                NavigateTask.toPos(station, 1.2D, goalId),
                StartCookTask.at(station, recipeId, actual, goalId),
                CookVerifyTask.at(station, actual, goalId),
                DeliverToOwnerTask.of(actual, goalId));
    }
}