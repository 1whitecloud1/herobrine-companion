package com.whitecloud233.modid.herobrine_companion.entity.logic.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 单一职责：一个委托的全部<b>行为逻辑</b>（开始 / 结算 / 清理 / 各类事件钩子）。
 * 展示数据（名称 / 描述 / 奖励文本）与 ID 归 {@link HeroQuestRegistry} 管，进度存储归 {@link QuestProgress} 管。
 * 每个实现类只承载一个委托的规则，不接触其他委托的细节。
 */
public interface HeroQuest {

    /** 委托被接受时调用一次。可在此生成任务实体、标记地点、开场台词等。 */
    default void onStart(ServerPlayer player, QuestProgress progress) {
    }

    /** 每 tick 调用。用于回合制检测（潜行被发现、烛火状态、雷暴、目标存活等）。 */
    default void onTick(ServerPlayer player, QuestProgress progress) {
    }

    /** 取消 / 失败时的统一清理（移除任务实体、重置状态）。 */
    default void onCancel(ServerPlayer player, QuestProgress progress) {
    }

    /**
     * 玩家右键实体。
     *
     * @return true 表示该次交互被委托消费（调用方应取消原交互，避免继续触发原版行为 / 打开 GUI）。
     */
    default boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack, QuestProgress progress) {
        return false;
    }

    /** 玩家杀死了生物（死亡事件由玩家造成）。 */
    default void onKill(ServerPlayer player, Entity killed, QuestProgress progress) {
    }

    /** 玩家死亡。用于“雷暴守望”这类存在死亡失败分支的委托。 */
    default void onPlayerDeath(ServerPlayer player, QuestProgress progress) {
    }

    /** 委托完成：发放奖励、播放完成台词。状态清理由管理器统一完成。 */
    default void onComplete(ServerPlayer player, QuestProgress progress) {
    }
}