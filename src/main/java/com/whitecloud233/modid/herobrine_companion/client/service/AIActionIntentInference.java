package com.whitecloud233.modid.herobrine_companion.client.service;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroAcceptChallengeTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroAscendTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroDescendTool;

import java.util.Locale;

/**
 * 玩家/回复<b>动作意图推断</b>（纯字符串启发式，无任何游戏/MC 调用）。
 *
 * <p>单一职责：回答三个问题——①玩家这条消息是不是可行动的"世界/本机动作请求"、
 * ②AI 回复是否<b>谎称</b>已执行了没有工具调用支撑的动作、③是否需要据此重试或缓冲流式输出。
 * 全部输入是字符串 + 少数开关，输出是布尔/动作常量，因此可以脱离 Minecraft 直接单测。</p>
 *
 * <p>本类只做<b>判断</b>，不做执行；实际命令执行见 {@link AIGameCommandExecutor}，
 * 回复清洗/防重复见 {@link AIReplyGuard}。</p>
 */
public final class AIActionIntentInference {

    private AIActionIntentInference() {
    }

    /**
     * 是否应当为这条消息缓冲流式输出（不逐字透传）：命中本机控制 / JVM 代码 / 世界动作意图时，
     * 因为大概率会触发工具调用 + 重试，直接流式会把中间草稿漏给玩家。
     */
    static boolean shouldBufferPotentialActionReply(String originalUserMessage, boolean crossSessionMode, boolean allowWorldActions) {
        if (crossSessionMode || !allowWorldActions) {
            return false;
        }
        if (LLMConfig.isComputerControlEnabled()
                && AIComputerControlSupport.isLikelyComputerControlRequest(originalUserMessage)) {
            return true;
        }
        if (LLMConfig.isJvmCodeSkillEnabled()
                && AIJvmCodeSkillSupport.isLikelyJvmCodeSkillRequest(originalUserMessage)) {
            return true;
        }
        return LLMConfig.isCommandEagerMode()
                ? isEagerWorldActionIntent(originalUserMessage)
                : isLikelyWorldActionRequest(originalUserMessage);
    }

    static boolean hasUnbackedWorldActionClaim(String originalUserMessage, String cleanReply) {
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty()
                || !(isLikelyWorldActionRequest(request) || isEagerWorldActionIntent(request))) {
            return false;
        }
        if (containsNoActionQualifier(reply)) {
            return false;
        }

        boolean completedTone = containsAny(reply,
                "已", "已经", "完成", "搞定", "做好了", "办好了", "执行了", "改好了", "done",
                "completed", "executed", "i have", "i've", "it is done", "as you asked");
        boolean actionClaim = containsAny(reply,
                "指令", "命令", "传送", "召唤", "生成", "给予", "给你", "清除", "清空", "击杀", "杀掉", "踢出",
                "设置", "改为", "改变", "天气", "时间", "难度", "模式", "方块", "填充", "放置", "效果", "药水",
                "附魔", "经验", "边界", "白名单", "封禁", "解封", "command", "teleport", "summon", "spawned",
                "gave", "given", "cleared", "killed", "kicked", "set ", "changed", "weather", "time", "gamemode",
                "difficulty", "effect", "enchanted", "filled", "placed", "worldborder", "whitelist", "banned");
        return completedTone && actionClaim;
    }

    static boolean hasUnbackedComputerActionClaim(String originalUserMessage, String cleanReply) {
        if (!AIComputerControlSupport.isLikelyComputerControlRequest(originalUserMessage)) {
            return false;
        }
        String reply = normalizeActionInferenceText(cleanReply);
        if (reply.isEmpty() || containsNoActionQualifier(reply) || containsClarificationOrSafetyQualifier(reply)) {
            return false;
        }
        boolean completedTone = containsAny(reply,
                "已", "已经", "完成", "搞定", "打开了", "创建了", "复制了", "写好了",
                "done", "completed", "opened", "created", "copied", "i have", "i've", "it is done");
        boolean computerAction = containsAny(reply,
                "记事本", "计算器", "资源管理器", "文件夹", "便笺", "笔记", "剪贴板", "电脑", "本机",
                "notepad", "calculator", "explorer", "folder", "note", "clipboard", "computer");
        return completedTone && computerAction;
    }

    static boolean shouldRetryEagerTextOnlyAction(String originalUserMessage, String cleanReply) {
        if (!LLMConfig.isCommandEagerMode()) {
            return false;
        }
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty() || !isEagerWorldActionIntent(request)) {
            return false;
        }
        return !containsNoActionQualifier(reply) && !containsClarificationOrSafetyQualifier(reply);
    }

    static boolean containsClarificationOrSafetyQualifier(String reply) {
        if (reply.contains("?") || reply.contains("？")) {
            return true;
        }
        return containsAny(reply,
                "需要", "请告诉", "先告诉", "确认", "哪个", "哪一个", "什么", "多少", "坐标", "目标",
                "无法", "不能", "不会", "不应该", "危险", "权限", "缺少", "need", "needs", "tell me",
                "which", "what", "how many", "coordinate", "target", "specify", "clarify", "confirm",
                "cannot", "can't", "won't", "unsafe", "permission", "missing");
    }

    static boolean isEagerWorldActionIntent(String text) {
        String normalized = normalizeActionInferenceText(text);
        if (normalized.isEmpty()) {
            return false;
        }
        if (containsAny(normalized,
                "怎么", "如何", "教程", "语法", "参数", "解释", "说明", "什么意思",
                "what is", "how to", "syntax", "explain", "tutorial", "parameter")) {
            return false;
        }
        if (isLikelyWorldActionRequest(normalized)) {
            return true;
        }
        return containsAny(normalized,
                "我想要", "想要", "需要", "缺", "没有", "不够", "拿不到", "找不到", "在哪", "在哪里",
                "带我", "过来", "回来", "回家", "基地", "村庄", "矿洞", "传过去", "太远", "迷路", "卡住",
                "出不去", "救我", "帮我", "保护我", "太黑", "看不见", "天黑", "下雨", "雨太", "雷",
                "太危险", "怪太多", "打不过", "血少", "快死", "饿", "没食物", "没工具", "没有装备",
                "想飞", "钻石", "铁", "金", "绿宝石", "木头", "石头", "水", "岩浆", "火", "着火",
                "中毒", "缓慢", "虚弱", "挖不动", "经验不够", "附魔", "升级", "修复", "清理", "垃圾",
                "背包满", "need ", "needs ", "want ", "wants ", "wish ", "lack ", "lacking ", "out of ",
                "missing ", "not enough", "can't find", "cannot find", "where is", "where are", "bring me",
                "take me", "come here", "go home", "home base", "village", "mineshaft", "lost", "stuck",
                "trapped", "help me", "save me", "protect me", "too dark", "can't see", "cannot see",
                "night", "rain", "storm", "thunder", "dangerous", "too many mobs", "low health", "dying",
                "hungry", "no food", "no tool", "no tools", "no armor", "want to fly", "diamond", "diamonds",
                "iron", "gold", "emerald", "wood", "stone", "water", "lava", "fire", "burning", "poison",
                "slowness", "weakness", "can't mine", "cannot mine", "need xp", "enchant", "repair",
                "clean up", "inventory full");
    }

    static boolean isLikelyWorldActionRequest(String text) {
        String normalized = normalizeActionInferenceText(text);
        if (normalized.isEmpty()) {
            return false;
        }
        if (containsAny(normalized,
                "怎么", "如何", "教程", "语法", "参数", "解释", "说明", "what is", "how to", "syntax", "explain")) {
            return false;
        }
        return containsAny(normalized,
                "给我", "给予", "清除", "清空", "传送", "tp", "召唤", "生成", "杀", "踢", "设置", "改成", "改为",
                "切换", "下雨", "天晴", "雷暴", "时间", "天气", "难度", "模式", "放置", "填充", "方块", "定位",
                "播放", "粒子", "效果", "药水", "附魔", "经验", "边界", "白名单", "封禁", "解封", "保存", "重载",
                "give me", "give ", "clear ", "teleport", "tp ", "summon", "spawn", "kill", "kick", "set ",
                "change ", "switch ", "weather", "time", "difficulty", "gamemode", "place ", "fill ", "setblock",
                "locate", "playsound", "particle", "effect", "enchant", "xp", "experience", "worldborder",
                "whitelist", "ban ", "pardon", "reload", "stop server");
    }

    static boolean containsNoActionQualifier(String reply) {
        return containsAny(reply,
                "没有执行", "未执行", "并未执行", "无法执行", "不能执行", "不会执行", "执行失败", "失败",
                "没有调用", "未调用", "不能改变", "无法改变", "did not", "didn't", "not execute", "not executed",
                "no command", "without executing", "failed", "cannot", "can't", "unable", "i won't", "i would");
    }

    /**
     * 从"玩家请求 + AI 草稿"推断是否应触发的回复驱动动作（挑战接受 / 起飞 / 降落），
     * 返回对应 agent 工具 id（{@link HeroAcceptChallengeTool} / {@link HeroAscendTool} / {@link HeroDescendTool}），
     * 由调用方发 {@code AgentRequestPacket} 交给服务端 agent 执行；无法推断则返回 {@code null}。
     */
    static String inferReplyDrivenAction(String originalUserMessage, String cleanReply) {
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty()) {
            return null;
        }

        if (isChallengeRequest(request) && isChallengeAcceptance(reply)) {
            return HeroAcceptChallengeTool.ID;
        }
        if (isFlyRequest(request) && isFlyAffirmation(reply)) {
            return HeroAscendTool.ID;
        }
        if (isLandRequest(request) && isLandAffirmation(reply)) {
            return HeroDescendTool.ID;
        }
        return null;
    }

    static String normalizeActionInferenceText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean isChallengeRequest(String text) {
        return containsAny(text, "挑战", "决斗", "试炼", "单挑", "比试", "challenge", "duel", "fight me", "battle me");
    }

    private static boolean isChallengeAcceptance(String text) {
        if (containsAny(text, "不接受", "拒绝", "can't", "cannot", "won't", "refuse", "decline")) {
            return false;
        }
        return containsAny(text, "接受", "奉陪", "来吧", "开始吧", "应战", "challenge accepted", "i accept", "very well", "let us fight", "come then");
    }

    private static boolean isFlyRequest(String text) {
        return containsAny(text, "飞", "飞起来", "升空", "漂浮", "悬浮", "腾空", "fly", "levitate", "float", "ascend", "rise up");
    }

    private static boolean isFlyAffirmation(String text) {
        if (containsAny(text, "不飞", "不会飞", "不能飞", "can't fly", "cannot fly", "won't fly")) {
            return false;
        }
        return containsAny(text, "飞起来", "升空", "漂浮", "悬浮", "腾空", "在空中", "flying", "levitating", "levitate", "rise", "ascend", "airborne");
    }

    private static boolean isLandRequest(String text) {
        return containsAny(text, "落下", "下来", "降落", "着陆", "落地", "land", "descend", "come down");
    }

    private static boolean isLandAffirmation(String text) {
        if (containsAny(text, "不下去", "不降落", "won't land", "won't come down", "cannot descend")) {
            return false;
        }
        return containsAny(text, "落地", "降落", "着陆", "下来", "回到地面", "landing", "landed", "descend", "come down");
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isEmpty() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
