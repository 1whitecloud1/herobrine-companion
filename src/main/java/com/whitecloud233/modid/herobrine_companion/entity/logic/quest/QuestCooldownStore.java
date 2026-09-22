package com.whitecloud233.modid.herobrine_companion.entity.logic.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单一职责：已完成委托的「重复接取冷却」计时。
 * 数据存玩家持久数据（跨维度 / 跨重启生效），以世界绝对游戏刻为基准，仅服务端读写。
 */
public final class QuestCooldownStore {

    private static final String TAG_COOLDOWNS = "HeroQuestCooldowns";

    private QuestCooldownStore() {
    }

    /** 记录某委托的冷却结束时刻（世界绝对游戏刻）。已过期的记录顺手清理。 */
    public static void setCooldown(ServerPlayer player, int questId, long endGameTime) {
        CompoundTag data = player.getPersistentData();
        CompoundTag cooldowns = data.getCompound(TAG_COOLDOWNS);
        cooldowns.putLong(String.valueOf(questId), endGameTime);
        prune(cooldowns, player.level().getGameTime());
        data.put(TAG_COOLDOWNS, cooldowns);
    }

    public static boolean isOnCooldown(ServerPlayer player, int questId) {
        return endGameTime(player, questId) > player.level().getGameTime();
    }

    /** 冷却结束时刻；无记录返回 0。 */
    public static long endGameTime(ServerPlayer player, int questId) {
        return player.getPersistentData().getCompound(TAG_COOLDOWNS).getLong(String.valueOf(questId));
    }

    /** 全部未过期的冷却（questId → 结束时刻），用于同步给客户端展示。 */
    public static Map<Integer, Long> activeCooldowns(ServerPlayer player) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        CompoundTag cooldowns = player.getPersistentData().getCompound(TAG_COOLDOWNS);
        long now = player.level().getGameTime();
        for (String key : cooldowns.getAllKeys()) {
            long end = cooldowns.getLong(key);
            if (end > now) {
                try {
                    result.put(Integer.parseInt(key), end);
                } catch (NumberFormatException ignored) {
                    // 脏键直接忽略
                }
            }
        }
        return result;
    }

    private static void prune(CompoundTag cooldowns, long now) {
        List<String> expired = new ArrayList<>();
        for (String key : cooldowns.getAllKeys()) {
            if (cooldowns.getLong(key) <= now) {
                expired.add(key);
            }
        }
        for (String key : expired) {
            cooldowns.remove(key);
        }
    }
}