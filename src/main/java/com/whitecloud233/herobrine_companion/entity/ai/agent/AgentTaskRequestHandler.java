package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.AgentTaskContext;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskGoals;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskState;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;

import java.util.List;
import java.util.Locale;

/**
 * {@code agent_task} 的服务端语义（交接 D1）：<b>start / status / pause / resume / cancel</b>。
 *
 * <p><b>为什么单独成类</b>：旧版服务端路由只按 {@code name=scene} 规划，
 * argsJson 里的控制参数被完全忽略——工具描述写着能暂停，实际什么也没发生。
 * 现在请求解析、合法性校验、队列操作、回执文案都在这里，网络包只做转发。</p>
 *
 * <p><b>单一职责</b>：解析与校验一次任务控制请求，操作既有 {@link HeroTaskQueue}，
 * 返回一句如实的回执。不新建第二套任务表，也不自己推进任务。</p>
 */
public final class AgentTaskRequestHandler {

    /** 支持的操作。 */
    public enum Action {
        START, STATUS, PAUSE, RESUME, CANCEL;

        public static Action parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "start" -> START;
                case "status" -> STATUS;
                case "pause" -> PAUSE;
                case "resume" -> RESUME;
                case "cancel" -> CANCEL;
                default -> null;
            };
        }
    }

    /** 处理结果：是否受理 + 面向 LLM 的中文回执。 */
    public record Outcome(boolean ok, String message) {
        public static Outcome fail(String message) {
            return new Outcome(false, message);
        }
    }

    private AgentTaskRequestHandler() {
    }

    /**
     * 处理一次 {@code agent_task} 请求。
     *
     * @param args  客户端来的扁平参数（含 action / scene / task_id / target_type / x / y / z / entity_uuid）
     * @param scene 旧版兼容场景（repair / inspect），仅在 action 缺省或为 start 时使用
     */
    public static Outcome handle(HeroEntity hero, AgentToolArgs args, String scene) {
        if (hero == null || hero.level() == null || hero.level().isClientSide) {
            return Outcome.fail("我暂时不在，无法处理任务请求");
        }
        HeroTaskQueue queue = hero.getHeroAgent().taskQueue();
        Action action = Action.parse(args.getString("action"));
        if (action == null) {
            // 兼容旧调用：只给 scene 时等价于 start。
            if (scene == null || scene.isBlank()) {
                return Outcome.fail("缺少 action（start/status/pause/resume/cancel）或 scene（repair/inspect）");
            }
            action = Action.START;
        }
        return switch (action) {
            case STATUS -> status(queue, args.getString("task_id"));
            case PAUSE -> pause(queue, args.getString("task_id"), resolveGoalId(queue, args.getString("task_id")));
            case RESUME -> resume(queue, resolveGoalId(queue, args.getString("task_id")));
            case CANCEL -> cancel(hero, queue, resolveGoalId(queue, args.getString("task_id")));
            case START -> start(hero, queue, args, scene);
        };
    }

    private static Outcome status(HeroTaskQueue queue, String taskId) {
        if (queue.isIdle()) {
            return new Outcome(true, "当前没有正在执行的任务");
        }
        TaskState state = queue.currentState();
        StringBuilder builder = new StringBuilder("任务 ");
        builder.append(taskId == null || taskId.isBlank() ? queue.activeGoalId() : taskId);
        builder.append(" 状态: ").append(state == null ? "未知" : state.displayName());
        builder.append("；当前步骤: ").append(queue.currentProgress());
        builder.append("；剩余步骤: ").append(queue.size());
        if (!queue.recentTerminals(3).isEmpty()) {
            builder.append("；最近: ");
            builder.append(String.join(" / ", queue.recentTerminals(3).stream()
                    .map(HeroTaskQueue.TerminalTask::line).toList()));
        }
        if (!queue.recentLog().isEmpty()) {
            List<String> log = queue.recentLog();
            builder.append("；日志: ").append(log.get(log.size() - 1));
        }
        return new Outcome(true, builder.toString());
    }

    private static Outcome pause(HeroTaskQueue queue, String requestedId, String goalId) {
        if (queue.isIdle()) {
            return Outcome.fail("当前没有任务可暂停");
        }
        if (requestedId != null && !requestedId.isBlank() && !matches(queue, requestedId)) {
            return Outcome.fail("找不到任务 " + requestedId + "（当前是 " + queue.activeGoalId() + "）");
        }
        if (queue.isPaused()) {
            return new Outcome(true, "任务 " + goalId + " 已经是暂停状态");
        }
        queue.pause();
        return new Outcome(true, "已暂停任务 " + goalId + "，进度保留");
    }

    private static Outcome resume(HeroTaskQueue queue, String goalId) {
        if (queue.isIdle()) {
            return Outcome.fail("当前没有任务可继续");
        }
        if (!queue.isPaused()) {
            return new Outcome(true, "任务 " + goalId + " 正在执行，无需继续");
        }
        queue.resume();
        return new Outcome(true, "已继续任务 " + goalId);
    }

    /**
     * 取消：<b>必须释放移动控制权</b>，否会"任务没了但 Hero 还在往旧目标走"。
     *
     * <p>走 {@code cancelGoal(context, ...)} 让每个已启动任务执行 {@code onEnd}
     * 并收回 {@code TaskControl}；不能只把列表清空。</p>
     */
    private static Outcome cancel(HeroEntity hero, HeroTaskQueue queue, String goalId) {
        if (queue.isIdle()) {
            return new Outcome(true, "当前没有任务，无需取消");
        }
        int sizeBefore = queue.size();
        AgentTaskContext context = new AgentTaskContext(hero);
        int removed = queue.cancelGoal(context, goalId);
        // 传入具体目标时只取消该目标；取消"当前"或名字对不上则整队收尾。
        if (removed == 0 && sizeBefore > 0) {
            removed = sizeBefore;
            queue.clearWithCleanup(context);
        }
        return new Outcome(true, "已取消任务 " + goalId + "（移除 " + removed + " 个未完成步骤，后续步骤不会启动，移动已停下）");
    }

    private static Outcome start(HeroEntity hero, HeroTaskQueue queue, AgentToolArgs args, String scene) {
        String goalId = TaskGoals.newGoalId("task");
        List<PlannedTask> planned = planFor(hero, args, scene, goalId);
        if (planned.isEmpty()) {
            return Outcome.fail(scene != null && !scene.isBlank()
                    ? "没有可执行的任务（场景 " + scene + " 未找到目标）"
                    : "没有可执行的任务（未提供可识别的目标）");
        }
        if (!queue.enqueueAll(planned)) {
            // 队列满：不接受，也不覆盖旧目标（未入队的步骤要收尾，避免残留）。
            queue.cancelGoal(new AgentTaskContext(hero), goalId);
            return Outcome.fail("任务队列已满（" + HeroTaskQueue.CAPACITY + "），本次未提交");
        }
        return new Outcome(true, "任务已提交队列（" + planned.size() + " 步），尚未执行完成");
    }

    /**
     * 由扁平参数决定任务序列。只支持可校验的目标形式，不接受任意脚本 / 命令 / 嵌套计划数组。
     */
    private static List<PlannedTask> planFor(HeroEntity hero, AgentToolArgs args, String scene, String goalId) {
        String targetType = args.getString("target_type").trim().toLowerCase(Locale.ROOT);
        if ("anomaly".equals(targetType) || "repair".equals(scene) || "inspect".equals(scene)) {
            boolean returnAfter = args.getBoolean("return_to_owner", false);
            return returnAfter
                    ? TaskGoals.handleAnomalyThenReturn(hero, null, goalId)
                    : TaskGoals.handleAnomaly(hero, null, goalId);
        }
        if ("owner".equals(targetType)) {
            return TaskGoals.returnToOwner(goalId);
        }
        if ("cook".equals(targetType) || "cook".equals(scene)) {
            return planCook(hero, args, goalId);
        }
        return List.of();
    }

    /**
     * 阶段 F："用这个箱子的材料做两份能做的食物，拿给我"。
     *
     * <p>必须给出明确厨具坐标与配方 ID：服务端<b>不猜配方</b>，也不接受"随便做点能做的"。
     * 缺料 / 配方不存在时返回空表，由调用方回执说明缺什么，而不是排一组空转任务。</p>
     */
    private static List<PlannedTask> planCook(HeroEntity hero, AgentToolArgs args, String goalId) {
        if (!args.has("x") || !args.has("y") || !args.has("z")) {
            return List.of();
        }
        String recipeRaw = args.getString("recipe_id").trim();
        if (recipeRaw.isEmpty()) {
            return List.of();
        }
        net.minecraft.resources.ResourceLocation recipeId = net.minecraft.resources.ResourceLocation.tryParse(recipeRaw);
        if (recipeId == null) {
            return List.of();
        }
        net.minecraft.core.BlockPos station = new net.minecraft.core.BlockPos(
                args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0));
        int count = Math.max(1, args.getInt("count", 1));
        return TaskGoals.cookAndDeliver(hero, station, recipeId, count, goalId);
    }

    private static boolean matches(HeroTaskQueue queue, String requestedId) {
        String active = queue.activeGoalId();
        return requestedId.equals(active) || requestedId.equals(queue.currentProgress());
    }

    private static String resolveGoalId(HeroTaskQueue queue, String requestedId) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId;
        }
        String active = queue.activeGoalId();
        return active == null || active.isBlank() ? "current" : active;
    }
}
