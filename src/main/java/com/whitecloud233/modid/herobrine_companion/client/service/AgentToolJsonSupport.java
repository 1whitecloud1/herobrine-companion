package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolDescriptor;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolParameter;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;

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

    /** 构建 agent 工具清单（目录工具 + 任务工具）。 */
    static List<LlmToolSpec> agentToolSpecs() {
        List<LlmToolSpec> specs = new ArrayList<>();
        for (AgentToolDescriptor descriptor : AgentToolRegistry.defaultCatalog()) {
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

    private static LlmToolSpec taskToolSpec() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject scene = new JsonObject();
        scene.addProperty("type", "string");
        scene.addProperty("description", "让 Herobrine 去执行的多步任务场景。repair=修复周围区域，inspect=探查周围区域。");
        JsonArray sceneEnum = new JsonArray();
        sceneEnum.add("repair");
        sceneEnum.add("inspect");
        scene.add("enum", sceneEnum);
        properties.add("scene", scene);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("scene");
        schema.add("required", required);
        return new LlmToolSpec(TOOL_AGENT_TASK, "让 Herobrine 的多步任务队列执行一个场景（修复 / 探查）。", schema);
    }
}