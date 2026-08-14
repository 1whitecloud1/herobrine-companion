package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.MemoryEpisode;

import java.util.ArrayList;
import java.util.List;

/**
 * 教训规则解析器（P5）：把长期记忆里的<b>结构化教训</b>（形如
 * {@code lesson:key=value|key2=value2}）解析成可供分类/规划消费的规则对。
 *
 * <p><b>单一职责</b>：只做"记忆 → 规则对"的纯解析，不写教训、不决策。
 * 写入侧见 {@code DefaultAgentReflector}，消费侧见 {@code AutonomyIntentionClassifier}。</p>
 */
public final class HeroLessonRules {

    private static final String LESSON_PREFIX = "lesson:";

    private HeroLessonRules() {
    }

    /** 一条可被行为层消费的规则（不可变）。 */
    public record LessonRule(String key, String value) {
    }

    /** 解析最近最多 cap 条教训中的结构化规则。 */
    public static List<LessonRule> parse(HeroMemory memory, int cap) {
        if (memory == null || cap <= 0) {
            return List.of();
        }
        List<LessonRule> rules = new ArrayList<>();
        for (MemoryEpisode episode : memory.topEpisodes(cap)) {
            if (!episode.isLesson()) {
                continue;
            }
            String text = episode.text().trim();
            if (!text.startsWith(LESSON_PREFIX)) {
                continue;
            }
            String body = text.substring(LESSON_PREFIX.length());
            for (String part : body.split("\\|")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    String key = part.substring(0, eq).trim();
                    String value = part.substring(eq + 1).trim();
                    if (!key.isEmpty() && !value.isEmpty()) {
                        rules.add(new LessonRule(key, value));
                    }
                }
            }
        }
        return rules;
    }

    /** 教训规则的可读单行摘要（供传感器注入 / 决策日志）。 */
    public static String summarize(List<LessonRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("教训规则:");
        for (LessonRule rule : rules) {
            builder.append(" ").append(rule.key()).append("=").append(rule.value());
        }
        return builder.toString();
    }
}
