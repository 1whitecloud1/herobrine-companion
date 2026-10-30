package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentFeatures;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolDescriptor;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolParameter;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;
import com.whitecloud233.modid.herobrine_companion.network.AgentFeatureChannel;
import com.whitecloud233.modid.herobrine_companion.util.JsonInputs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端工具桥（M4）：把服务端 agent 工具目录转成中性 {@link LlmToolSpec}（由格式适配器包装），
 * 并识别 agent 工具调用。镜像 {@code AIComputerControlSupport} 的 JSON 构造风格。
 */
final class AgentToolJsonSupport {

    static final String TOOL_AGENT_TASK = "agent_task";

    /**
     * 扩展工具 → 能力位映射（T22）。
     *
     * <p>没在本表里的工具属于"旧工具"（主通道里的九个 + 旧 agent_task 场景），
     * 任何服务端都支持，不受能力协商影响。表里的工具只在握手确认后才会出现在
     * 模型工具清单里——否则模型会调用一个旧服务端只回 {@code unknown_tool} 的工具。</p>
     */
    private static final java.util.Map<String, Integer> EXTENSION_FEATURES = java.util.Map.of(
            "hero_navigate", AgentFeatureChannel.FEATURE_NAVIGATE,
            "block_query", AgentFeatureChannel.FEATURE_BLOCK_QUERY,
            "hero_cook", AgentFeatureChannel.FEATURE_COOK,
            "item_transfer", AgentFeatureChannel.FEATURE_TRANSFER,
            "hero_interact", AgentFeatureChannel.FEATURE_INTERACT,
            "hero_remember", AgentFeatureChannel.FEATURE_REMEMBER,
            "build_preview", AgentFeatureChannel.FEATURE_BUILD_PREVIEW,
            "hero_gather", AgentFeatureChannel.FEATURE_GATHER);

    private static final Set<String> AGENT_TOOL_IDS = new LinkedHashSet<>();

    static {
        for (AgentToolDescriptor descriptor : AgentToolRegistry.defaultCatalog()) {
            AGENT_TOOL_IDS.add(descriptor.id());
        }
        AGENT_TOOL_IDS.add(TOOL_AGENT_TASK);
    }

    private AgentToolJsonSupport() {}

    /** 判断某个工具名是否为 agent 工具（目录工具 + 任务工具）。 */
    static boolean isAgentTool(String toolName) {
        return AGENT_TOOL_IDS.contains(toolName);
    }

    /**
     * 某个 agent 工具当前是否真的可用（T22）。
     *
     * <p>旧工具恒可用；扩展工具要求服务端在握手中声明了对应能力位。
     * 未握手 / 旧服务端 / 通道协商失败时扩展工具不可用。</p>
     */
    static boolean isToolAvailable(String toolName) {
        Integer feature = EXTENSION_FEATURES.get(toolName);
        return feature == null || ClientAgentFeatures.supports(feature);
    }

    /** 构建 agent 工具清单（目录工具 + 任务工具），<b>只含本轮真正可用的工具</b>。 */
    static List<LlmToolSpec> agentToolSpecs() {
        List<LlmToolSpec> specs = new ArrayList<>();
        for (AgentToolDescriptor descriptor : AgentToolRegistry.defaultCatalog()) {
            if (!isToolAvailable(descriptor.id())) {
                continue;
            }
            specs.add(toToolSpec(descriptor));
        }
        specs.add(taskToolSpec());
        return specs;
    }

    /** 去掉对话字段后把参数 JSON 序列化为字符串（供 C→S 包）。 */
    static String buildArgsJson(JsonObject args) {
        if (args == null) {
            return "{}";
        }
        JsonObject cleaned = args.deepCopy();
        cleaned.remove("dialogue");
        return cleaned.toString();
    }

    /**
     * {@code agent_task} 的路由名（{@code AgentRequestPacket.name}）。
     *
     * <p>协议上 name 只有 64 字符上限、没有扩展位；为了保持旧包字段与旧服务端兼容，
     * 这里仍然只放场景名。控制参数（action / task_id / target_type…）走 argsJson，
     * 由服务端 {@code AgentTaskRequestHandler} 解析——旧服务端会忽略未知的 name，
     * 新服务端则拿到完整参数。</p>
     */
    static String taskRouteName(JsonObject args) {
        String scene = JsonInputs.optionalString(args, "scene", "");
        if (!scene.isBlank()) {
            return scene;
        }
        String targetType = JsonInputs.optionalString(args, "target_type", "");
        return targetType.isBlank() ? "start" : targetType;
    }

    private static LlmToolSpec toToolSpec(AgentToolDescriptor descriptor) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (AgentToolParameter parameter : descriptor.parameters()) {
            JsonObject property = new JsonObject();
            property.addProperty("type", parameter.type());
            property.addProperty("description", parameter.description());
            properties.add(parameter.name(), property);
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }
        schema.add("properties", properties);
        if (!required.isEmpty()) {
            schema.add("required", required);
        }
        return new LlmToolSpec(descriptor.id(), descriptor.description(), schema);
    }

    /**
     * {@code agent_task} 的 schema（交接 D1）。
     *
     * <p>只暴露<b>扁平、可校验</b>的字段：{@code AgentToolArgs.fromJson} 只保留 JSON primitive，
     * 嵌套数组 / 对象的计划不会被服务端收到，所以这里不提供嵌套结构，
     * 服务端也会独立再校验一次（schema 只是给模型的提示，不是授权依据）。</p>
     */
    private static LlmToolSpec taskToolSpec() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description", "任务操作：start=启动一段多步目标，status=查询当前进度，pause/resume=暂停与继续（保留进度），cancel=取消且不启动后续步骤。");
        JsonArray actionEnum = new JsonArray();
        actionEnum.add("start");
        actionEnum.add("status");
        actionEnum.add("pause");
        actionEnum.add("resume");
        actionEnum.add("cancel");
        action.add("enum", actionEnum);
        properties.add("action", action);

        JsonObject scene = new JsonObject();
        scene.addProperty("type", "string");
        scene.addProperty("description", "start 时的目标场景（旧版兼容）。repair=修复异常方块，inspect=前往异常方块现场检查。查询附近有什么请直接根据本轮 Current Environment 实体列表回答，无需调用任务工具。");
        JsonArray sceneEnum = new JsonArray();
        sceneEnum.add("repair");
        sceneEnum.add("inspect");
        scene.add("enum", sceneEnum);
        properties.add("scene", scene);

        JsonObject returnAfter = new JsonObject();
        returnAfter.addProperty("type", "boolean");
        returnAfter.addProperty("description", "start 时是否在处理完后返回玩家身边（“处理前面的异常，然后回来”设为 true）。");
        properties.add("return_to_owner", returnAfter);

        JsonObject targetType = new JsonObject();
        targetType.addProperty("type", "string");
        targetType.addProperty("description", "目标类型：anomaly=处理一处异常方块，owner=返回玩家身边，cook=用厨具做菜并交付。");
        JsonArray targetEnum = new JsonArray();
        targetEnum.add("anomaly");
        targetEnum.add("owner");
        targetEnum.add("cook");
        targetType.add("enum", targetEnum);
        properties.add("target_type", targetType);

        JsonObject recipeId = new JsonObject();
        recipeId.addProperty("type", "string");
        recipeId.addProperty("description", "target_type=cook 时的真实配方 ID（必须来自 hero_cook action=query 的返回，不能凭空编造）。");
        properties.add("recipe_id", recipeId);

        JsonObject count = new JsonObject();
        count.addProperty("type", "integer");
        count.addProperty("description", "target_type=cook 时期望份数；实际份数受可用材料与配方上限限制。");
        properties.add("count", count);

        JsonObject taskId = new JsonObject();
        taskId.addProperty("type", "string");
        taskId.addProperty("description", "status/pause/resume/cancel 可选：要操作的任务 ID；缺省表示本玩家当前任务。");
        properties.add("task_id", taskId);

        schema.add("properties", properties);
        return new LlmToolSpec(TOOL_AGENT_TASK,
                "启动、查询、暂停、继续或取消 Herobrine 的多步任务。仅在玩家要求实际长任务时调用；普通附近情况查询直接使用本轮注入的环境数据回答。"
                        + "start 的回执只表示已进入队列，不代表执行完成；status 才会返回真实进度与终态。",
                schema);
    }
}
