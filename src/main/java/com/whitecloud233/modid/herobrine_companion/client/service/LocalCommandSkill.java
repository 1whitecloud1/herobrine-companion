package com.whitecloud233.modid.herobrine_companion.client.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 本地模型专属的确定性指令层。
 *
 * <p>本地 3B 量化模型在 OpenAI 工具调用格式上不可靠，经常把“传送到你那里 / 去末地”
 * 这类明确指令回成纯文本（甚至回复“无法执行”）。本类在本地模式下先用关键词
 * 直接识别并执行高频指令（传送到 Hero / 召唤 Hero / 维度传送），
 * 再让模型只负责生成一句符合角色的台词。</p>
 *
 * <p>只做识别 + 委托执行（执行在 {@link AIGameCommandExecutor}），不触碰任何云端 provider。</p>
 */
public final class LocalCommandSkill {

    private LocalCommandSkill() {
    }

    /**
     * 尝试执行玩家消息中的明确指令（可多条，按出现顺序依次执行并去重）。
     *
     * @return 命中并成功执行 → 给模型的“已执行”说明文案；未命中或全部失败 → null
     */
    public static CompletableFuture<String> tryExecute(String userMessage, UUID playerUUID) {
        List<String> actions = matchActions(userMessage);
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<String> result = CompletableFuture.completedFuture("");
        for (String action : actions) {
            result = result.thenCompose(acc -> AIGameCommandExecutor.executeCommandWithFeedback(action, playerUUID)
                    .thenApply(ok -> Boolean.TRUE.equals(ok)
                            ? (acc.isEmpty() ? noticeFor(action) : acc + "；随后" + noticeFor(action))
                            : acc));
        }
        return result.thenApply(acc -> acc.isEmpty() ? null : acc);
    }

    private static List<String> matchActions(String text) {
        List<String> actions = new ArrayList<>();
        if (text == null) {
            return actions;
        }
        String t = text.toLowerCase(Locale.ROOT);
        // 提问/讲解类不执行：怎么去末地、末地在哪里、教程……
        if (containsAny(t,
                "怎么", "如何", "怎样", "哪里", "在哪", "教程", "多少", "什么", "为什么", "能不能",
                "how", "where", "what", "why", "tutorial", "syntax")) {
            return actions;
        }
        Set<String> matched = new LinkedHashSet<>();
        // 玩家 → Hero 身边（传送到你那里 / 拉我过去）
        if (containsAny(t,
                "到你那里", "到你身边", "去你那里", "去你身边", "去你那",
                "传送到你", "传送去你", "拉我过去", "带我过去", "带我去你", "传送过去",
                "take me to you", "bring me to you", "teleport me to you", "go to you")) {
            matched.add(AIGameCommandExecutor.ACTION_TELEPORT_TO_HERO);
        }
        // Hero → 玩家身边（过来 / 传送到我）
        if (containsAny(t,
                "到我这里", "来我这里", "到我身边", "来我身边", "传送到我", "到我", "过来", "传送过来",
                "come here", "come to me", "come over", "teleport to me")) {
            matched.add(AIGameCommandExecutor.ACTION_SUMMON_HERO_TO_PLAYER);
        }
        // 维度传送
        if (hasDimension(t, "末地", "the_end", "the end", "end") && hasMoveIntent(t)) {
            matched.add(AIGameCommandExecutor.ACTION_TELEPORT_TO_END);
        }
        if (hasDimension(t, "下界", "地狱", "nether") && hasMoveIntent(t)) {
            matched.add(AIGameCommandExecutor.ACTION_TELEPORT_TO_NETHER);
        }
        if (hasDimension(t, "主世界", "overworld") && hasMoveIntent(t)) {
            matched.add(AIGameCommandExecutor.ACTION_TELEPORT_TO_OVERWORLD);
        }
        actions.addAll(matched);
        return actions;
    }

    private static boolean hasDimension(String t, String... keywords) {
        for (String keyword : keywords) {
            if (t.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMoveIntent(String t) {
        return containsAny(t,
                "传送", "传送到", "去", "到", "前往", "进入", "进", "tp",
                "带我", "拉我", "过去", "回", "回去", "回家");
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String noticeFor(String action) {
        if (AIGameCommandExecutor.ACTION_TELEPORT_TO_HERO.equals(action)) {
            return "玩家已被传送到你身边";
        }
        if (AIGameCommandExecutor.ACTION_SUMMON_HERO_TO_PLAYER.equals(action)) {
            return "你已瞬移到玩家身边";
        }
        if (AIGameCommandExecutor.ACTION_TELEPORT_TO_END.equals(action)) {
            return "玩家已被传送到末地";
        }
        if (AIGameCommandExecutor.ACTION_TELEPORT_TO_NETHER.equals(action)) {
            return "玩家已被传送到下界";
        }
        if (AIGameCommandExecutor.ACTION_TELEPORT_TO_OVERWORLD.equals(action)) {
            return "玩家已被传送回主世界";
        }
        return null;
    }
}
