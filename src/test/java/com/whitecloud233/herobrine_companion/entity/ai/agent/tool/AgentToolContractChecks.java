package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.NavigateTask;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskGoals;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阶段 D 回归检查：工具目录、扁平参数协议、导航与查询工具的参数校验契约。
 *
 * <p>这些检查不启动游戏，覆盖的是<b>能在纯 JVM 里验证的契约</b>：
 * schema 不超过已发布字段、参数类型化读取、非法参数必须被拒绝（不能回退成默认任务）、
 * 新工具已接线且职责边界不重复。真实世界交互（查容器、做菜、步行）需要游戏内实测。</p>
 */
public final class AgentToolContractChecks {

    public static void main(String[] args) {
        int checks = 0;

        // ---- 工具目录：新工具必须已接线，且同一份清单被服务端与客户端共用 ----
        List<AgentToolDescriptor> catalog = AgentToolFactory.defaultCatalog();
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (AgentToolDescriptor descriptor : catalog) {
            if (!ids.add(descriptor.id())) {
                throw new AssertionError("工具 ID 重复: " + descriptor.id());
            }
        }
        checks += check(ids.contains(HeroNavigateTool.ID), "hero_navigate 必须已注册（D2）");
        checks += check(ids.contains(BlockQueryTool.ID), "block_query 必须已注册（D3）");
        checks += check(ids.contains(HeroCookTool.ID), "hero_cook 必须已注册（D4）");
        checks += check(ids.contains(ItemTransferTool.ID), "item_transfer 必须已注册（E1）");
        checks += check(ids.contains(HeroInteractTool.ID), "hero_interact 必须已注册（E2）");
        checks += check(ids.contains(HeroRememberTool.ID), "hero_remember 必须已注册（E3）");
        checks += check(ids.contains(BuildPreviewTool.ID), "build_preview 必须已注册（E4）");
        checks += check(ids.contains(HeroGatherTool.ID), "hero_gather 必须已注册（E5）");
        checks += check(AgentToolRegistry.defaultCatalog().size() == catalog.size(),
                "客户端目录必须与服务端工具工厂同源（不再手列两份）");
        checks += check(ids.size() == catalog.size(), "工具目录不得有重复 ID");
        // 每个工具的描述符必须和工厂里的实例一致（避免"目录说有、注册表没有"）。
        AgentToolRegistry registry = new AgentToolRegistry(null);
        AgentToolFactory.createDefaults().forEach(registry::register);
        checks += check(registry.catalog().size() == catalog.size(), "服务端注册表必须装配全部默认工具");
        for (AgentToolDescriptor descriptor : catalog) {
            if (registry.lookup(descriptor.id()) == null) {
                throw new AssertionError("注册表缺少工具: " + descriptor.id());
            }
        }
        checks += 1;

        // ---- 导航与召唤必须语义分开（不能用传送伪装步行） ----
        checks += check(!HeroNavigateTool.ID.equals(HeroSummonTool.ID), "导航与召唤必须是两个工具");
        AgentTool navigate = new HeroNavigateTool();
        checks += check(navigate.validate(args("target_type", "owner")).ok(),
                "导航 owner 目标必须合法");
        checks += check(!navigate.validate(args("target_type", "position")).ok(),
                "position 缺坐标必须被拒绝（不能静默变成别的目标）");
        checks += check(!navigate.validate(args("target_type", "teleport")).ok(),
                "未知 target_type 必须被拒绝");
        AgentToolResult missingEntity = navigate.validate(args("target_type", "entity"));
        checks += check(missingEntity.failed() && missingEntity.cause() == AgentToolResult.Cause.INVALID_ARGS,
                "entity 缺 UUID 必须按软件侧参数错误拒绝");

        // ---- block_query：三坐标必须同时给出；分页参数有界 ----
        AgentTool blockQuery = new BlockQueryTool();
        checks += check(blockQuery.validate(args("x", "1")).failed(), "只给 x 必须被拒绝");
        checks += check(blockQuery.validate(args("x", "1", "y", "2", "z", "3")).ok(), "完整坐标必须合法");
        checks += check(blockQuery.validate(AgentToolArgs.empty()).ok(), "全省略表示查询指向的方块");
        checks += check(blockQuery.validate(args("x", "1", "y", "2", "z", "3", "page", "-1")).failed(),
                "负页码必须被拒绝");
        checks += check(!blockQuery.requiresConfirmation(), "只读查询不需要确认");

        // ---- hero_cook：start 必须带真实配方 ID；未知动作拒绝 ----
        AgentTool cook = new HeroCookTool();
        checks += check(cook.validate(args("action", "query", "x", "0", "y", "64", "z", "0")).ok(),
                "配方查询参数必须合法");
        checks += check(!cook.validate(args("action", "start", "x", "0", "y", "64", "z", "0")).ok(),
                "start 缺 recipe_id 必须被拒绝");
        checks += check(!cook.validate(args("action", "cook", "x", "0", "y", "64", "z", "0")).ok(),
                "未知动作必须被拒绝（不能默认执行）");
        checks += check(!cook.validate(args("action", "query")).ok(), "缺厨具坐标必须被拒绝");

        // ---- E1 item_transfer：端点、选择器与容器坐标都必须校验 ----
        AgentTool transfer = new ItemTransferTool();
        checks += check(transfer.validate(args("from", "player", "to", "hero", "item_id", "minecraft:apple")).ok(),
                "玩家→手部的转移参数应合法");
        checks += check(!transfer.validate(args("from", "player", "to", "player", "item_id", "x")).ok(),
                "来源与目标相同必须被拒绝");
        checks += check(!transfer.validate(args("from", "player", "to", "hero")).ok(),
                "缺 item_id / slot 必须被拒绝");
        checks += check(!transfer.validate(args("from", "container", "to", "hero", "item_id", "x")).ok(),
                "涉及容器但缺坐标必须被拒绝");
        checks += check(!transfer.validate(args("from", "player", "to", "hero", "item_id", "x", "count", "0")).ok(),
                "数量 0 必须被拒绝");
        checks += check(!transfer.validate(args("from", "player", "to", "hero", "item_id", "x", "count", "-3")).ok(),
                "负数量必须被拒绝");
        checks += check(!transfer.validate(args("from", "moon", "to", "hero", "item_id", "x")).ok(),
                "未知端点必须被拒绝");

        // ---- E2 hero_interact：只允许白名单操作 ----
        AgentTool interact = new HeroInteractTool();
        checks += check(interact.validate(args("operation", "toggle", "x", "0", "y", "64", "z", "0")).ok(),
                "开合操作参数应合法");
        checks += check(!interact.validate(args("operation", "explode", "x", "0", "y", "64", "z", "0")).ok(),
                "任意方法名式操作必须被拒绝（不接受任意命令）");
        checks += check(!interact.validate(args("operation", "toggle")).ok(), "缺坐标必须被拒绝");

        // ---- E3 hero_remember：区域必须有范围，大小写不敏感，可遗忘 ----
        AgentTool remember = new HeroRememberTool();
        checks += check(remember.validate(args("operation", "save", "name", "家")).ok(), "保存单点地点应合法");
        checks += check(remember.validate(args("operation", "save", "name", "家", "radius", "12")).ok(),
                "保存区域偏好应合法");
        checks += check(!remember.validate(args("operation", "save", "name", "家", "radius", "0")).ok(),
                "半径 0 必须被拒绝（要区域就给出范围）");
        checks += check(!remember.validate(args("operation", "save", "name", "家", "radius", "9999")).ok(),
                "超大半径必须被拒绝");
        checks += check(!remember.validate(args("operation", "save")).ok(), "缺名称必须被拒绝");
        checks += check(!remember.validate(args("operation", "forget")).ok(), "遗忘也必须给名称");
        checks += check(remember.validate(args("operation", "query")).ok(), "查询可以不带名称（列出全部）");
        checks += check(!remember.validate(args("operation", "rename", "name", "x")).ok(),
                "未知操作必须被拒绝");

        com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroLandmarks marks =
                new com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroLandmarks();
        checks += check(marks.save(new com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroLandmarks.Landmark(
                        "Home", "minecraft:overworld", 10, 64, 10, 16, "player")),
                "命名地点必须能保存");
        checks += check(marks.find("home").isPresent(), "地点查找必须大小写不敏感");
        checks += check(marks.isInsideArea("minecraft:overworld", 20, 64, 12),
                "区域偏好必须能被消费（判断点是否在范围内）");
        checks += check(!marks.isInsideArea("minecraft:the_nether", 20, 64, 12),
                "不同维度的区域不生效（维度必须参与判断）");
        checks += check(!marks.isInsideArea("minecraft:overworld", 100, 64, 100),
                "区域外的点不生效（单点不代表整片）");
        // E3 必须有真实消费者：区域偏好要能拦住净化（否则只是存了一个字符串）。
        checks += check(marks.isInsideArea("minecraft:overworld", 10, 200, 10),
                "区域判定必须忽略 Y（房子是立体的，不按高度分割）");
        marks.remove("Home");
        checks += check(!marks.isInsideArea("minecraft:overworld", 10, 64, 10),
                "遗忘之后区域不再生效（可遗忘）");

        // E3 保护区不得变成"虚假成功"：任务层必须能区分"已处理"与"按偏好拒绝"。
        // 修复任务在保护区里应返回 FAILED 而不是 DONE——否则异常还在却汇报"已修复完毕"。
        checks += check(protectedAreaReportsFailure(), 
                "保护区内的修复任务必须报失败，不能虚报修复完成（源码契约检查）");

        // ---- E4 build_preview：必须给 template_id，未知模板不按名称回退 ----
        AgentTool preview = new BuildPreviewTool();
        checks += check(!preview.validate(AgentToolArgs.empty()).ok(), "缺 template_id 必须被拒绝");
        checks += check(!preview.validate(args("template_id", "abc", "x", "1")).ok(),
                "只给 x 必须被拒绝（原点要完整）");
        checks += check(preview.validate(args("template_id", "abc")).ok(), "只给 template_id 应合法");
        checks += check(!preview.requiresConfirmation(), "预览只读，不需要确认");

        // ---- E5 hero_gather：模式、半径、数量都有界 ----
        AgentTool gather = new HeroGatherTool();
        checks += check(gather.validate(args("mode", "pickup")).ok(), "拾取参数应合法");
        checks += check(gather.validate(args("mode", "harvest", "radius", "16", "count", "8", "replant", "true")).ok(),
                "收割（含补种）参数应合法");
        checks += check(!gather.validate(args("mode", "mine")).ok(),
                "首版不支持挖矿等模式，必须拒绝");
        checks += check(!gather.validate(args("mode", "pickup", "radius", "0")).ok(), "半径 0 必须被拒绝");
        checks += check(!gather.validate(args("mode", "pickup", "radius", "999")).ok(), "超大半径必须被拒绝");

        // ---- 参数协议：只保留 primitive，数字可用字符串或数字给出 ----
        AgentToolArgs fromNumber = AgentToolArgs.fromJson("{\"x\":5,\"y\":64,\"z\":-3}");
        checks += check(fromNumber.getInt("x", Integer.MIN_VALUE) == 5, "JSON 数字必须可读");
        checks += check(fromNumber.getInt("z", Integer.MIN_VALUE) == -3, "负数坐标必须可读");
        AgentToolArgs fromString = AgentToolArgs.fromJson("{\"stop_distance\":\"3.5\"}");
        checks += check(Math.abs(fromString.getDouble("stop_distance", 0.0D) - 3.5D) < 1.0E-6D,
                "字符串数字必须可读（模型两种写法都常见）");
        checks += check(AgentToolArgs.fromJson("{\"flag\":true}").getBoolean("flag", false),
                "布尔必须可读");
        AgentToolArgs nested = AgentToolArgs.fromJson("{\"plan\":[1,2,3],\"obj\":{\"a\":1},\"ok\":\"yes\"}");
        checks += check(!nested.has("plan") && !nested.has("obj"),
                "嵌套数组 / 对象必须被忽略（协议真实限制，schema 不能依赖它们）");
        checks += check("yes".equals(nested.getString("ok")), "同级的 primitive 仍必须保留");
        checks += check(AgentToolArgs.fromJson("not-json").asMap().isEmpty(),
                "非法 JSON 必须返回空参数（由调用方按缺参拒绝，不得回退默认任务）");
        checks += check(AgentToolArgs.fromJson("{\"x\":1").asMap().isEmpty(), "截断 JSON 必须被安全处理");

        // ---- 任务控制参数：服务端必须能读到 action / task_id ----
        AgentToolArgs control = AgentToolArgs.fromJson(
                "{\"action\":\"pause\",\"task_id\":\"task-abcd1234\",\"return_to_owner\":true}");
        checks += check("pause".equals(control.getString("action")), "action 必须能过网");
        checks += check("task-abcd1234".equals(control.getString("task_id")), "task_id 必须能过网");
        checks += check(control.getBoolean("return_to_owner", false), "return_to_owner 必须能过网");

        // ---- 阶段 C 目标编排：没有可处理目标时返回空表（据实回执，不空转） ----
        List<PlannedTask> none = TaskGoals.handleAnomalyThenReturn(null, null, TaskGoals.newGoalId("t"));
        checks += check(none.isEmpty(), "没有目标异常时必须返回空任务表，而不是排一个空转任务");
        String goalId = TaskGoals.newGoalId("task");
        checks += check(goalId.startsWith("task-"), "目标 ID 必须带可识别前缀");

        // ---- 目标 ID 让"取消不启动后续步骤"可操作 ----
        HeroTaskQueue queue = new HeroTaskQueue(new com.whitecloud233.herobrine_companion.entity.ai.agent.task.AgentTaskRegistry());
        NavigateTask first = NavigateTask.toOwner(goalId, 3.0D);
        NavigateTask second = NavigateTask.toPos(new net.minecraft.core.BlockPos(1, 64, 1), 1.0D, goalId);
        queue.enqueue(first);
        queue.enqueue(second);
        checks += check(queue.size() == 2, "同一目标的步骤都应入队");
        checks += check(queue.cancelGoal(goalId) == 2, "取消必须能按目标 ID 移除剩余步骤");

        System.out.println("AgentToolContractChecks: " + checks + " checks passed");
    }

    private static AgentToolArgs args(String... pairs) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return new AgentToolArgs(values);
    }

    private static int check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        return 1;
    }

    /**
     * 源码契约：{@code RepairAreaTask} 在保护区里必须返回 FAILED，且不能先报"已修复完毕"。
     *
     * <p>这是"加入 E3 消费者"时最容易犯的错：拦截了净化却在任务层照旧返回 DONE，
     * 结果异常还在、玩家却被告知修好了。行为级检查无法在纯 JVM 里驱动完整任务，
     * 所以直接读源码确认这段分支的形态。</p>
     */
    private static boolean protectedAreaReportsFailure() {
        String packagePath = AgentToolContractChecks.class.getPackageName().replace('.', '/');
        // 本类在 .../agent/tool/，任务在同级 .../agent/task/
        String base = packagePath.substring(0, packagePath.lastIndexOf('/'));
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/" + base + "/task/RepairAreaTask.java");
        try {
            String source = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            int guard = source.indexOf("isProtectedByOwnerPreference");
            if (guard < 0) {
                throw new AssertionError("RepairAreaTask 未检查区域偏好（E3 消费者缺失）");
            }
            // 守卫之后、下一个 return DONE 之前，必须先出现 FAILED。
            int afterGuard = source.indexOf("return TaskState.FAILED;", guard);
            int nextDone = source.indexOf("return TaskState.DONE;", guard);
            return afterGuard > 0 && (nextDone < 0 || afterGuard < nextDone);
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 RepairAreaTask 源码: " + path + " -> " + error);
        }
    }

    private AgentToolContractChecks() {
    }
}
