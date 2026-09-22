package com.whitecloud233.herobrine_companion.entity.logic.quest;

import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.QuestStateSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 单一职责：委托分发器。只做三件事——
 * <ol>
 *   <li>按玩家状态定位当前委托（{@link QuestProgress} 只读）；</li>
 *   <li>把事件原样转发给对应的 {@link HeroQuest} 实现；</li>
 *   <li>统一结算（完成 / 失败 / 取消 / 信任增减 / 发放物品 / 冷却写入）。</li>
 * </ol>
 * 不含任何单个委托的业务细节；新增委托只需登记注册表，无需改动本类。
 */
public final class HeroQuestManager {

    public static final String TAG_PLAYER_DOING_QUEST = "player_doing_quest";
    /** 与旧版共用的持久键：进行中的委托可跨版本延续。 */
    public static final String TAG_ACTIVE_QUEST_ID = "HeroActiveQuestId";
    public static final String TAG_PENDING_TRUST = "HeroPendingTrustReward";
    public static final String TAG_PENDING_CLEAR = "HeroPendingQuestClear";

    private HeroQuestManager() {
    }

    // ------------------------------------------------ 生命周期 ------------------------------------------------

    public static void startQuest(HeroEntity hero, ServerPlayer player, int questId) {
        QuestProgress progress = QuestProgress.of(player);
        if (progress.isActive()) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_already_active"));
            return;
        }
        HeroQuest quest = HeroQuestRegistry.byId(questId);
        if (quest == null) return;

        // 冷却校验：完成过的委托需等待冷却结束才能再次接取（防刷信任，服务端权威）
        if (QuestCooldownStore.isOnCooldown(player, questId)) {
            long remaining = QuestCooldownStore.endGameTime(player, questId) - player.level().getGameTime();
            long days = remaining / 24000L;
            long hours = (remaining % 24000L + 999L) / 1000L;
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_cooldown", days, hours));
            return;
        }

        progress.start(questId);
        progress.setHeroUuid(hero.getUUID());
        hero.addTag(TAG_PLAYER_DOING_QUEST);
        quest.onStart(player, progress);
        syncQuestState(player, progress);
    }

    public static void cancelQuest(HeroEntity hero, ServerPlayer player) {
        QuestProgress progress = QuestProgress.of(player);
        int questId = progress.questId();
        if (questId == 0) return;

        HeroQuest quest = HeroQuestRegistry.byId(questId);
        if (quest != null) quest.onCancel(player, progress);

        progress.clear();
        hero.removeTag(TAG_PLAYER_DOING_QUEST);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_cancelled"));
        syncQuestState(player, progress);
    }

    /** 失败结算：清理 + 系统提示 + 状态清除。 */
    public static void failQuest(ServerPlayer player, String messageKey) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return;

        HeroQuest quest = HeroQuestRegistry.byId(progress.questId());
        if (quest != null) quest.onCancel(player, progress);

        clearDirtyTags(player, progress);
        player.sendSystemMessage(Component.translatable(messageKey));
        syncQuestState(player, progress);
    }

    /** 完成结算：调起所属实现的奖励与台词，然后统一清理状态并写入重复接取冷却。 */
    public static void completeQuest(ServerPlayer player) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return;

        int questId = progress.questId();
        HeroQuest quest = HeroQuestRegistry.byId(questId);
        if (quest != null) quest.onComplete(player, progress);

        clearDirtyTags(player, progress);
        QuestCooldownStore.setCooldown(player, questId, player.level().getGameTime() + HeroQuestRegistry.COOLDOWN_TICKS);
        syncQuestState(player, progress);
    }

    private static void clearDirtyTags(ServerPlayer player, QuestProgress progress) {
        HeroEntity hero = findQuestHero(player, progress);
        progress.clear();
        if (hero != null) {
            hero.removeTag(TAG_PLAYER_DOING_QUEST);
        } else {
            player.getPersistentData().putBoolean(TAG_PENDING_CLEAR, true);
        }
    }

    // ------------------------------------------------ 事件转发 ------------------------------------------------

    public static void tickAll(ServerPlayer player) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return;
        HeroQuest quest = HeroQuestRegistry.byId(progress.questId());
        if (quest != null) quest.onTick(player, progress);
    }

    /**
     * 玩家右键实体。
     *
     * @return true = 交互已被委托消费，调用方应取消原交互（避免打开 Hero GUI、触发原版行为等）。
     */
    public static boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return false;
        HeroQuest quest = HeroQuestRegistry.byId(progress.questId());
        return quest != null && quest.onEntityInteract(player, target, stack, progress);
    }

    public static void onKill(ServerPlayer player, Entity killed) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return;
        HeroQuest quest = HeroQuestRegistry.byId(progress.questId());
        if (quest != null) quest.onKill(player, killed, progress);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        QuestProgress progress = QuestProgress.of(player);
        if (!progress.isActive()) return;
        HeroQuest quest = HeroQuestRegistry.byId(progress.questId());
        if (quest != null) quest.onPlayerDeath(player, progress);
    }

    // ------------------------------------------------ 查询 ------------------------------------------------

    public static boolean isPlayerDoingQuest(Player player) {
        return player.getPersistentData().getInt(TAG_ACTIVE_QUEST_ID) != 0;
    }

    /** Hero 不应攻击 / 安抚的任务目标（幽灵怪、镜像幽灵等），交给玩家处理。 */
    public static boolean shouldIgnoreTarget(Entity target) {
        return target instanceof GhostZombieEntity
                || target instanceof GhostCreeperEntity
                || target instanceof GhostSkeletonEntity
                || target.getTags().contains("herobrine_quest_mirror");
    }

    /** 目标实体是否为发布当前委托的那只 Hero（旧存档未记录时放行任意 Hero）。 */
    public static boolean isQuestHero(Entity target, QuestProgress progress) {
        if (!(target instanceof HeroEntity)) return false;
        UUID heroUuid = progress.heroUuid();
        return heroUuid == null || heroUuid.equals(target.getUUID());
    }

    // ------------------------------------------------ 奖励辅助 ------------------------------------------------

    /** 发放信任：优先找到发布委托的 Hero（或同维度就近的无主 Hero）；找不到时挂起待领取。 */
    public static void addTrust(ServerPlayer player, int amount) {
        HeroEntity hero = findQuestHero(player, QuestProgress.of(player));
        if (hero != null) {
            hero.increaseTrust(amount);
            HeroDataHandler.updateGlobalTrust(hero);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_increase", amount, hero.getTrustLevel()));
        } else {
            player.getPersistentData().putInt(TAG_PENDING_TRUST, amount);
            player.getPersistentData().putBoolean(TAG_PENDING_CLEAR, true);
        }
    }

    /** 发放物品：背包满则掉落在脚下，绝不吞道具。 */
    public static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** 解析进度里记录的 Hero；缺失时兜底旧逻辑（同维度就近无主 Hero）。 */
    public static HeroEntity findQuestHero(ServerPlayer player, QuestProgress progress) {
        UUID heroUuid = progress.heroUuid();
        if (heroUuid != null && player.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(heroUuid);
            if (entity instanceof HeroEntity hero && hero.isAlive() && !hero.isRemoved()) {
                return hero;
            }
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == serverLevel && hero.isAlive() && !hero.isRemoved()) {
                    boolean isOwner = hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID());
                    boolean isNearby = hero.getOwnerUUID() == null && hero.distanceToSqr(player) < 1024.0D;
                    if (isOwner || isNearby) {
                        if (hero.getOwnerUUID() == null) hero.setOwnerUUID(player.getUUID());
                        return hero;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------ 状态同步 ------------------------------------------------

    /** 把当前委托 ID 与各委托冷却状态同步给客户端（0 = 无进行中委托），供交付开屏拦截与冷却展示。 */
    private static void syncQuestState(ServerPlayer player, QuestProgress progress) {
        PacketHandler.sendToPlayer(new QuestStateSyncPacket(
                progress.isActive() ? progress.questId() : 0,
                QuestCooldownStore.activeCooldowns(player)), player);
    }

    /** 玩家登录 / 维度切换等时机补发一次委托状态，保证客户端缓存新鲜。 */
    public static void syncQuestStateTo(ServerPlayer player) {
        syncQuestState(player, QuestProgress.of(player));
    }
}