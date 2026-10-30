package com.whitecloud233.modid.herobrine_companion.entity.logic.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 单一职责：读写玩家身上正在进行的委托的状态（ID / 进度 / 目标实体 UUID / 发布 Hero UUID / 标记坐标 / 状态位）。
 * 所有字段都写入玩家 PersistentData，跨维度跨登录保持。不包含任何委托业务规则。
 */
public class QuestProgress {

    /** 与旧版共用的键：已进行中的委托可平滑延续。 */
    public static final String TAG_ID = "HeroActiveQuestId";
    public static final String TAG_PROGRESS = "HeroActiveQuestProgress";
    private static final String TAG_TARGET_UUID = "HeroQuestTargetUUID";
    private static final String TAG_HERO_UUID = "HeroQuestHeroUUID";
    private static final String TAG_POSITIONS = "HeroQuestPositions";
    private static final String TAG_FLAGS = "HeroQuestFlags";

    /** Copy the entire active quest when Minecraft replaces the player entity. */
    public static void copyTo(CompoundTag original, CompoundTag replacement) {
        for (String key : new String[]{TAG_ID, TAG_PROGRESS, TAG_TARGET_UUID, TAG_HERO_UUID, TAG_POSITIONS, TAG_FLAGS}) {
            if (original.contains(key)) {
                replacement.put(key, original.get(key).copy());
            } else {
                replacement.remove(key);
            }
        }
    }

    private final ServerPlayer player;

    private QuestProgress(ServerPlayer player) {
        this.player = player;
    }

    public static QuestProgress of(ServerPlayer player) {
        return new QuestProgress(player);
    }

    /**
     * 「这名玩家是否有进行中的委托」。供只有 {@link Player} 或 {@code CompoundTag} 的调用点使用。
     *
     * <p>键名只在 {@link #TAG_ID} 处定义；这里提供静态入口，是为了让
     * {@code HeroQuestManager} 等外部调用方不必再自己写一遍字面量。</p>
     */
    public static boolean isActive(Player player) {
        return player != null && player.getPersistentData().getInt(TAG_ID) != 0;
    }

    public ServerPlayer player() {
        return player;
    }

    private CompoundTag data() {
        return player.getPersistentData();
    }

    public boolean isActive() {
        return data().getInt(TAG_ID) != 0;
    }

    public int questId() {
        return data().getInt(TAG_ID);
    }

    /** 开启委托：写入 ID 并把进度归零。 */
    public void start(int questId) {
        CompoundTag data = data();
        data.putInt(TAG_ID, questId);
        data.putInt(TAG_PROGRESS, 0);
    }

    /** 清空全部委托状态；取消与失败结算都走这里，任何扩展字段都不会残留。 */
    public void clear() {
        CompoundTag data = data();
        data.remove(TAG_ID);
        data.remove(TAG_PROGRESS);
        data.remove(TAG_TARGET_UUID);
        data.remove(TAG_HERO_UUID);
        data.remove(TAG_POSITIONS);
        data.remove(TAG_FLAGS);
    }

    public int progress() {
        return data().getInt(TAG_PROGRESS);
    }

    public void setProgress(int value) {
        data().putInt(TAG_PROGRESS, Math.max(0, value));
    }

    public void addProgress(int delta) {
        setProgress(progress() + delta);
    }

    /** 任务目标实体（任务狼 / 任务末影人 / 镜像幽灵…）。 */
    public UUID targetUuid() {
        return data().hasUUID(TAG_TARGET_UUID) ? data().getUUID(TAG_TARGET_UUID) : null;
    }

    public void setTargetUuid(UUID uuid) {
        data().putUUID(TAG_TARGET_UUID, uuid);
    }

    /** 发布这条委托的 Hero。 */
    public UUID heroUuid() {
        return data().hasUUID(TAG_HERO_UUID) ? data().getUUID(TAG_HERO_UUID) : null;
    }

    public void setHeroUuid(UUID uuid) {
        data().putUUID(TAG_HERO_UUID, uuid);
    }

    /** 标记坐标（如雷暴守望点）。缺失时返回空数组。 */
    public int[] positions() {
        return data().getIntArray(TAG_POSITIONS);
    }

    public void setPositions(int[] positions) {
        data().putIntArray(TAG_POSITIONS, positions);
    }

    /** 委托自定义状态位（0..31），由实现类自行约定含义。 */
    public boolean flag(int bit) {
        return (data().getInt(TAG_FLAGS) & (1 << bit)) != 0;
    }

    public void setFlag(int bit, boolean value) {
        int flags = data().getInt(TAG_FLAGS);
        if (value) {
            flags |= (1 << bit);
        } else {
            flags &= ~(1 << bit);
        }
        data().putInt(TAG_FLAGS, flags);
    }
}
