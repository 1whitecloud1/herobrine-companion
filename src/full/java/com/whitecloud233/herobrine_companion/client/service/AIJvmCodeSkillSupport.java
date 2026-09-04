package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolSpec;

import java.util.Locale;

/**
 * jvm_code_skill 工具契约（单一职责：定义 LLM 可见的工具 JSON schema、参数解析与意图检测）。
 * 不涉及编译、执行或确认流程——那些在 client.jvm 域内。
 */
final class AIJvmCodeSkillSupport {
    static final String TOOL_JVM_CODE = "jvm_code_skill";

    private static final int MAX_CODE_LENGTH = 12_000;

    private static final String TOOL_DESCRIPTION = "A real-time Java code execution tool that compiles and runs code inside the game's JVM. "
            + "Use ONLY for a clear, explicit request from the local player to modify the game at code level; "
            + "never use it proactively, and prefer minecraft_command_skill for ordinary world actions. "
            + "Provide ONLY the BODY of the method `public String run() throws Throwable` — no class, no package, no imports. "
            + "The body is compiled inside `HbGeneratedJvmAction implements JvmCodeAction` in package "
            + "com.whitecloud233.herobrine_companion.client.jvm with these imports already present: "
            + "net.minecraft.client.Minecraft; net.minecraft.client.player.LocalPlayer; net.minecraft.server.MinecraftServer; "
            + "net.minecraft.server.level.ServerLevel; net.minecraft.server.level.ServerPlayer; net.minecraft.world.entity.Entity; "
            + "net.minecraft.world.level.Level; net.minecraft.core.BlockPos; net.minecraft.world.phys.Vec3; "
            + "net.minecraft.network.chat.Component; java.util.*. Use fully-qualified names for anything else. "
            + "The code runs on the client render thread; to change world/server state hop to the server thread via "
            + "Minecraft.getInstance().getSingleplayerServer().execute(() -> { ... });. "
            + "Keep run() short (under 1 second) and end the body with `return \"<short status>\";`. "
            + "Code that only uses Minecraft/game APIs runs automatically; code that touches local files, the network, system processes, or reflection still requires a local confirmation dialog. "
            + "If the local player explicitly and directly asks to end/quit/close the game or world (e.g. \"结束游戏\", \"关闭游戏\", \"退出游戏\", \"end the game\", \"quit the game\", \"close the game\"), you may write that shutdown call (e.g. Minecraft.getInstance().stop(), Window.close(), MinecraftServer.halt()/stopServer()). "
            + "Such calls ALWAYS open a local confirmation dialog explaining they will close Minecraft; the game closes only if the player explicitly approves that dialog, and the call is cancelled otherwise. "
            + "Never write shutdown code proactively, for inferred convenience, or for anything other than a direct explicit player request to end the game. "
            + "Never claim success before the tool result.";

    private AIJvmCodeSkillSupport() {}

    static boolean isSupportedToolName(String toolName) {
        return TOOL_JVM_CODE.equals(toolName);
    }

    static LlmToolSpec toolSpec() {
        return new LlmToolSpec(TOOL_JVM_CODE, TOOL_DESCRIPTION, createInputSchema());
    }

    static ParseResult parseCode(JsonObject args) {
        String code = getString(args, "code");
        if (code.isBlank()) {
            return ParseResult.error("A non-empty Java method body is required.");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return ParseResult.error("The Java method body is too long (max " + MAX_CODE_LENGTH + " chars).");
        }
        return ParseResult.success(code);
    }

    static boolean isLikelyJvmCodeSkillRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return containsAny(normalized,
                "写代码", "写段代码", "执行代码", "运行代码", "代码", "注入", "jvm", "java",
                "改游戏逻辑", "修改游戏逻辑", "游戏底层", "底层代码", "字节码", "反射", "编译",
                "结束游戏", "关闭游戏", "退出游戏", "关闭世界", "退出世界", "关掉游戏",
                "end the game", "quit the game", "close the game", "shut down the game",
                "run this code", "execute code", "run code", "java code", "inject", "injection",
                "jvm", "bytecode", "reflection", "compile code");
    }

    private static JsonObject createInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);

        JsonObject properties = new JsonObject();
        addStringProperty(properties, "code",
                "The BODY of the method `public String run() throws Throwable` (no class, no package, no imports).", MAX_CODE_LENGTH);
        addStringProperty(properties, "dialogue", "Brief in-character dialogue to show only after the confirmed execution succeeds.", 300);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("code");
        required.add("dialogue");
        schema.add("required", required);
        return schema;
    }

    private static void addStringProperty(JsonObject properties, String name, String description, int maxLength) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        property.addProperty("maxLength", maxLength);
        properties.add(name, property);
    }

    private static String getString(JsonObject args, String name) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull() || !args.get(name).isJsonPrimitive()) {
            return "";
        }
        try {
            return args.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    record ParseResult(String code, String error) {
        static ParseResult success(String code) {
            return new ParseResult(code, "");
        }

        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        boolean isValid() {
            return this.code != null;
        }
    }
}
