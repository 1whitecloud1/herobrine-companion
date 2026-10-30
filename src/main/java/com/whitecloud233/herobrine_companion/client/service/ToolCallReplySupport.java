package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.util.JsonInputs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.whitecloud233.herobrine_companion.util.LegacyFormattingText;

import java.util.regex.Pattern;

/**
 * 工具调用回合的<b>可说话文本</b>：保证一次工具调用不会吞掉模型已经给出的正常回答。
 *
 * <p><b>问题背景</b>：OpenAI Chat / Responses、Anthropic 等协议都允许同一条回复同时带正文
 * （{@code content}）与工具调用。编排层原先只要检测到工具调用，就直接用工具参数里的
 * {@code dialogue}（缺失时退回内置英文默认句）作为最终回复，正文被整段丢弃。表现就是玩家
 * 问了一个问题，却只看到 “Reality bends to a cleaner command.” 之类与回答无关的台词——
 * 也就是“LLM 调用指令吞掉了正常对话回答”。流式模式下更明显：逐字预览里已经有正文，聊天栏
 * 最终却换成了另一句，前后不一致。</p>
 *
 * <p><b>职责边界</b>：只做“从正文 / 工具 dialogue / 兜底句里选一句给玩家看”的纯函数，
 * 并把选中的句子回填到参数 {@code dialogue}，让各执行器（命令、本机控制、JVM、agent 工具）
 * 在成功与回执分支复用同一句。不发起请求、不判断工具权限、不执行任何动作。</p>
 */
final class ToolCallReplySupport {

    /** 旧式 XML 工具调用块；整块移除，避免命令/参数文本被当成台词念出来。 */
    private static final Pattern XML_TOOL_BLOCK = Pattern.compile("(?is)<invoke\\b[^>]*>.*?</invoke>");

    /** 其余尖括号标签（含 HTML、残留工具标记）。 */
    private static final Pattern MARKUP_TAG = Pattern.compile("<[^>]*>");

    private ToolCallReplySupport() {
    }

    /**
     * 选定本轮最终说给玩家的话：<b>正文优先</b>，其次工具参数里的 dialogue，最后才是兜底句。
     *
     * <p>正文是模型对玩家问题的直接回答，工具调用只是顺带改变世界，不能反过来覆盖它；
     * dialogue 只在模型“只发工具、不说话”（GLM 等常见）时兜底。</p>
     *
     * @param modelText    模型回复的正文（可为 null）
     * @param toolDialogue 工具参数 dialogue（可为 null）
     * @param fallback     两者都没有时的兜底句
     */
    static String spokenLine(String modelText, String toolDialogue, String fallback) {
        String prose = proseWithoutToolMarkup(modelText);
        if (!prose.isBlank()) {
            return prose;
        }
        String dialogue = cleanText(toolDialogue);
        if (!dialogue.isBlank()) {
            return dialogue;
        }
        return fallback;
    }

    /**
     * 返回一份补齐 {@code dialogue} 的参数副本：正文非空时以正文为准，否则保留模型给的
     * dialogue。调用方把副本交给执行器，工具调用便不再丢掉正常回答。
     *
     * <p>副本是深拷贝，调用方手里的解析结果不变（调试日志仍能看到模型原样参数）。</p>
     */
    static JsonObject withSpokenFallback(JsonObject arguments, String modelText) {
        JsonObject enriched = arguments == null ? new JsonObject() : arguments.deepCopy();
        String dialogue = JsonInputs.string(enriched, "dialogue", "");
        String spoken = spokenLine(modelText, dialogue, "");
        if (!spoken.isBlank()) {
            enriched.addProperty("dialogue", spoken);
        }
        return enriched;
    }

    /** 去掉思考块与 {@code <invoke>} 工具块后剩下的正文（旧式 XML 调用只应念包围它的那句话）。 */
    static String proseWithoutToolMarkup(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String stripped = AIReplyGuard.stripThinkingBlocks(text);
        if (stripped == null || stripped.isEmpty()) {
            return "";
        }
        return cleanMarkup(XML_TOOL_BLOCK.matcher(stripped).replaceAll(" "));
    }

    /** 清理文本：剥离思考块与标签，保留 § 格式码（与 finalizeTextReply 的可见文本口径一致）。 */
    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String stripped = AIReplyGuard.stripThinkingBlocks(text);
        return cleanMarkup(stripped == null ? "" : stripped);
    }

    private static String cleanMarkup(String text) {
        return LegacyFormattingText.normalize(MARKUP_TAG.matcher(text).replaceAll("")).trim();
    }

}
