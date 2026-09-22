package com.whitecloud233.herobrine_companion.entity.dialogue;

/**
 * LLM 台词通道 —— 不同来源各自计时,互不挤占。
 *
 * <p>背景:原先所有 Actor 对话共享同一个"最小间隔"闸,觉醒生物频繁说话时,
 * 玩家的托付台词会被判定在冷却期内、被迫退回本地文本。按通道分开计时后:
 * <ul>
 *   <li>{@link #AWAKENED}:觉醒生物/通用情景台词,沿用 awakenedDialogueMinIntervalSeconds(默认 30s);</li>
 *   <li>{@link #GIFT}:赠礼(托付)台词,独立计时,间隔由 giftDialogueMinIntervalSeconds
 *       控制(默认 0 = 不限制),因此不会再被觉醒对话挤掉。</li>
 * </ul>
 */
public enum DialogueChannel {
    AWAKENED,
    GIFT
}