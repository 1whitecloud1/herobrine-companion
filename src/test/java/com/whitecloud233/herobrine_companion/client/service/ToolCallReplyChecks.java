package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmResponse;
import com.whitecloud233.herobrine_companion.client.llm.format.OpenAiChatAdapter;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具调用回合不得吞掉正常对话回答。
 *
 * <p>回归目标：模型在同一条回复里既给出正文、又发出工具调用时，编排层必须把正文说给玩家，
 * 而不是用工具参数里的 {@code dialogue}（缺失时是内置英文默认句）覆盖掉它。</p>
 */
public final class ToolCallReplyChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // 1) 供应商报文：正文与工具调用可以同时存在（GLM/OpenAI 常见的“先说再调”）。
        LlmResponse both = new OpenAiChatAdapter().parseResponse(JsonParser.parseString("""
                {"choices":[{"finish_reason":"tool_calls","message":{
                  "content":"今天的天气会很好，不必担心雷雨。",
                  "tool_calls":[{"function":{"name":"minecraft_command_skill",
                    "arguments":"{\\"action\\":\\"set_weather\\",\\"weather\\":\\"clear\\",\\"dialogue\\":\\"Reality bends to a cleaner command.\\"}"}}]}}]}
                """).getAsJsonObject());
        check(both.successful() && both.tool() != null, "A tool call must still be dispatched");
        check(!both.text().isBlank(), "Prose must survive provider parsing");

        // 2) 正文优先于 dialogue：这正是被吞掉的那句话。
        JsonObject enriched = ToolCallReplySupport.withSpokenFallback(both.tool().arguments(), both.text());
        check("今天的天气会很好，不必担心雷雨。".equals(enriched.get("dialogue").getAsString()),
                "The player-visible answer must come from the prose, not the tool dialogue");
        check("set_weather".equals(enriched.get("action").getAsString()), "Tool parameters must not be lost when enriching dialogue");
        check(both.tool().arguments().has("dialogue"), "Sanity: the model did provide a dialogue");
        check(!enriched.get("dialogue").getAsString().contains("Reality bends"),
                "The canned cast line must not replace a real answer");

        // 3) 参数副本：调试日志里保留的仍是模型原样参数。
        JsonObject original = JsonParser.parseString("{\"action\":\"give_item\",\"item_id\":\"minecraft:diamond\"}").getAsJsonObject();
        JsonObject copy = ToolCallReplySupport.withSpokenFallback(original, "拿着，这是我给你的东西。");
        check(!original.has("dialogue"), "Enrichment must not mutate the parsed invocation");
        check("拿着，这是我给你的东西。".equals(copy.get("dialogue").getAsString()), "Plain prose must be carried into the tool dialogue");

        // 4) 只有工具、没有正文（GLM 的 tool-only 形态）时，仍用模型自己写的 dialogue。
        JsonObject toolOnly = ToolCallReplySupport.withSpokenFallback(both.tool().arguments(), "   ");
        check("Reality bends to a cleaner command.".equals(toolOnly.get("dialogue").getAsString()),
                "Tool-only replies keep using the model dialogue");

        // 5) 三档优先级：正文 → dialogue → 兜底句。
        check("正文".equals(ToolCallReplySupport.spokenLine("正文", "台词", "兜底")), "Prose wins");
        check("台词".equals(ToolCallReplySupport.spokenLine(" ", "台词", "兜底")), "Dialogue is the second choice");
        check("兜底".equals(ToolCallReplySupport.spokenLine(null, null, "兜底")), "Fallback is the last choice");
        check("兜底".equals(ToolCallReplySupport.spokenLine("<|think|>思考<|/think|>", "", "兜底")), "Thinking-only text is not an answer");

        // 6) 思考过程不泄漏，§ 格式码保留，旧式 XML 工具块不当作台词。
        check("§e好的。".equals(ToolCallReplySupport.spokenLine("<|think|>内部推理<|/think|>§e好的。", null, "x")),
                "Thinking blocks are stripped but formatting codes survive");
        String xml = """
                §f我会让雷云散开。
                <invoke name="manifest_divine_power"><parameter name="command">weather clear</parameter><parameter name="dialogue">Code altered.</parameter></invoke>
                """;
        check("§f我会让雷云散开。".equals(ToolCallReplySupport.spokenLine(xml, "Code altered.", "x")),
                "Legacy XML tool blocks must not be spoken as dialogue");

        // 7) 非字符串 dialogue 不能被当成可说话文本，也不能让工具参数读取抛异常。
        JsonObject typed = JsonParser.parseString("{\"action\":\"set_time\",\"dialogue\":{\"nested\":1}}").getAsJsonObject();
        JsonObject healed = ToolCallReplySupport.withSpokenFallback(typed, "现在入夜。");
        check("现在入夜。".equals(healed.get("dialogue").getAsString()), "Non-string dialogue is replaced by the prose");

        // 8) 源码契约：两条派发路径都必须走统一出口，防止改动回退到“工具调用即丢弃正文”。
        String service = Files.readString(Path.of(
                "src/main/java/com/whitecloud233/herobrine_companion/client/service/AIService.java"));
        check(service.split("ToolCallReplySupport\\.withSpokenFallback", -1).length - 1 >= 2,
                "Both non-streaming and streaming dispatch must preserve the prose");
        check(!service.contains("executeNamedToolAction(toolInvocation.name(), toolInvocation.arguments(), attempt)"),
                "Raw invocation must not be dispatched before the answer is preserved");
        check(!service.contains("executeNamedToolAction(tool.name(), tool.arguments(), attempt)"),
                "Streaming dispatch must not drop the prose either");

        System.out.println("ToolCallReplyChecks: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
