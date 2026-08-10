package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 三层记忆（M4）：让 agent "记得住"。
 *
 * <ul>
 *   <li><b>情景层</b>：按时间发生的事件 / 教训（{@link MemoryEpisode}），有界 + 超龄遗忘。</li>
 *   <li><b>语义层</b>：事实键值（玩家偏好、世界事实），长期。</li>
 *   <li><b>工作层</b>：当前上下文，仅内存不落盘。</li>
 * </ul>
 *
 * <p>检索复刻 {@code LoreRAGManager} 的轻量关键字子串匹配（无向量），产出可注入 LLM 的记忆串。
 * 遗忘规则：非 lesson 且超龄（默认 7 天）→ 丢弃；超容量 → 丢"低重要度 + 最旧"。</p>
 */
public final class HeroMemory {

    /** 情景记忆容量上限。 */
    public static final int MAX_EPISODES = 200;

    /** 非 lesson 事件的最大保留时长（tick）。7 天 ≈ 12,096,000 tick。 */
    public static final long MAX_EVENT_AGE_TICKS = 7L * 24000L * 60L;

    private static final String KEY_EPISODES = "Episodes";
    private static final String KEY_FACTS = "Facts";

    private final List<MemoryEpisode> episodes = new ArrayList<>();
    private final Map<String, String> facts = new LinkedHashMap<>();
    private final Map<String, String> working = new LinkedHashMap<>();

    // ---- 情景层 ----

    /** 记录一条事件记忆。 */
    public void rememberEvent(String text, int importance, long gameTime) {
        if (text == null || text.isBlank()) {
            return;
        }
        episodes.add(new MemoryEpisode(gameTime, "event", clampImportance(importance), text.trim()));
    }

    /** 记录一条教训（重要度高、不被时间遗忘）。 */
    public void learnLesson(String text, long gameTime) {
        if (text == null || text.isBlank()) {
            return;
        }
        episodes.add(new MemoryEpisode(gameTime, "lesson", MemoryEpisode.IMPORTANCE_LESSON, text.trim()));
    }

    /** 遗忘 / 压缩：超龄非 lesson 丢弃，超容量丢低重要度最旧。 */
    public void prune(long gameTime) {
        episodes.removeIf(e -> !e.isLesson() && gameTime - e.gameTime() > MAX_EVENT_AGE_TICKS);
        if (episodes.size() > MAX_EPISODES) {
            episodes.sort(Comparator.comparingInt(MemoryEpisode::importance)
                    .thenComparingLong(MemoryEpisode::gameTime));
            while (episodes.size() > MAX_EPISODES) {
                episodes.remove(0);
            }
        }
    }

    // ---- 语义层 ----

    public void setFact(String key, String value) {
        if (key != null && !key.isBlank()) {
            facts.put(key, value == null ? "" : value);
        }
    }

    public String getFact(String key) {
        return facts.get(key);
    }

    public Map<String, String> facts() {
        return Map.copyOf(facts);
    }

    // ---- 工作层（不落盘） ----

    public void setWorking(String key, String value) {
        if (key != null && !key.isBlank()) {
            working.put(key, value == null ? "" : value);
        }
    }

    public String getWorking(String key) {
        return working.get(key);
    }

    public void clearWorking() {
        working.clear();
    }

    // ---- 检索 ----

    /** 关键字子串匹配检索（重要度降序 → 新近优先），上限 max 条。 */
    public List<MemoryEpisode> retrieve(String query, int max) {
        if (query == null || query.isBlank() || episodes.isEmpty()) {
            return List.of();
        }
        List<String> needles = tokenize(query);
        if (needles.isEmpty()) {
            return List.of();
        }
        List<MemoryEpisode> hits = new ArrayList<>();
        for (MemoryEpisode episode : episodes) {
            String lower = episode.text().toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (lower.contains(needle)) {
                    hits.add(episode);
                    break;
                }
            }
        }
        hits.sort(Comparator.comparingInt(MemoryEpisode::importance).reversed()
                .thenComparing(MemoryEpisode::gameTime).reversed());
        return hits.size() <= max ? hits : hits.subList(0, max);
    }

    /** 检索并拼成一段可注入 LLM 的记忆提示（上限 cap 条）。 */
    public String retrievePrompt(String query, int cap) {
        List<MemoryEpisode> hits = retrieve(query, cap);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("（记忆）");
        for (MemoryEpisode episode : hits) {
            builder.append("\n- ").append(episode.line());
        }
        return builder.toString();
    }

    /** 顶层情景记忆：按重要度降序 → 新近优先，取前 cap 条（供 digest / 管理器展示）。 */
    public List<MemoryEpisode> topEpisodes(int cap) {
        if (episodes.isEmpty() || cap <= 0) {
            return List.of();
        }
        List<MemoryEpisode> sorted = new ArrayList<>(episodes);
        sorted.sort(Comparator.comparingInt(MemoryEpisode::importance).reversed()
                .thenComparing(Comparator.comparingLong(MemoryEpisode::gameTime).reversed()));
        return sorted.size() <= cap ? List.copyOf(sorted) : List.copyOf(sorted.subList(0, cap));
    }

    /** 清空情景层（保留语义 facts 与工作层）——用于"改人设后清掉旧叙事"。 */
    public void clearEpisodes() {
        episodes.clear();
    }

    /** 清空全部记忆（情景 + 语义 + 工作层）。 */
    public void clearAll() {
        episodes.clear();
        facts.clear();
        working.clear();
    }

    private static List<String> tokenize(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String part : lower.split("[\\s,，。；;]+")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2) {
                tokens.add(trimmed);
            }
        }
        // 保证整句也参与匹配（中文整句命中率更高）
        if (lower.length() >= 2) {
            tokens.add(0, lower);
        }
        return tokens;
    }

    // ---- 存档 ----

    public void write(CompoundTag tag) {
        ListTag episodesTag = new ListTag();
        for (MemoryEpisode episode : episodes) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("time", episode.gameTime());
            entry.putString("category", episode.category());
            entry.putInt("importance", episode.importance());
            entry.putString("text", episode.text());
            episodesTag.add(entry);
        }
        tag.put(KEY_EPISODES, episodesTag);

        CompoundTag factsTag = new CompoundTag();
        for (Map.Entry<String, String> fact : facts.entrySet()) {
            factsTag.putString(fact.getKey(), fact.getValue());
        }
        tag.put(KEY_FACTS, factsTag);
    }

    public void read(CompoundTag tag) {
        episodes.clear();
        facts.clear();
        if (tag == null) {
            return;
        }
        if (tag.contains(KEY_EPISODES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_EPISODES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                episodes.add(new MemoryEpisode(
                        entry.getLong("time"),
                        entry.getString("category"),
                        entry.getInt("importance"),
                        entry.getString("text")));
            }
        }
        if (tag.contains(KEY_FACTS, Tag.TAG_COMPOUND)) {
            CompoundTag factsTag = tag.getCompound(KEY_FACTS);
            for (String key : factsTag.getAllKeys()) {
                facts.put(key, factsTag.getString(key));
            }
        }
    }

    public int episodeCount() {
        return episodes.size();
    }

    private static int clampImportance(int importance) {
        return Math.max(0, Math.min(3, importance));
    }
}