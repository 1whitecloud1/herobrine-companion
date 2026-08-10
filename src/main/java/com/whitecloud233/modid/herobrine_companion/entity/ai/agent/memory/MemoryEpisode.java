package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory;

/**
 * 单条情景记忆（不可变）。category ∈ {@code event} / {@code lesson}。
 *
 * @param gameTime   记录时的游戏 tick
 * @param category   类别（event=事件 / lesson=教训）
 * @param importance 重要度 0-3；lesson 固定 3（永不因超龄被遗忘）
 * @param text       记忆文本
 */
public record MemoryEpisode(long gameTime, String category, int importance, String text) {

    /** 教训类记忆的重要度（高优先级，不被时间遗忘）。 */
    public static final int IMPORTANCE_LESSON = 3;

    public boolean isLesson() {
        return IMPORTANCE_LESSON == importance && "lesson".equals(category);
    }

    public String line() {
        return text;
    }
}