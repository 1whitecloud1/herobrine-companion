package com.whitecloud233.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.herobrine_companion.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 委托 1「清理不稳定区域」：清除 5 只幽灵实体。奖励虚空骨髓 x3 + 信任 +15。
 */
public class ClearUnstableZoneQuest implements HeroQuest {

    private static final int TARGET_KILLS = 5;

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_1"));
    }

    @Override
    public void onKill(ServerPlayer player, Entity killed, QuestProgress progress) {
        if (!isGhost(killed)) return;
        progress.addProgress(1);
        player.displayClientMessage(Component.translatable("message.herobrine_companion.quest.kill_progress", progress.progress(), TARGET_KILLS), true);
        if (progress.progress() >= TARGET_KILLS) {
            HeroQuestManager.completeQuest(player);
        }
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_1"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.VOID_MARROW.get(), 3));
        HeroQuestManager.addTrust(player, 15);
    }

    private static boolean isGhost(Entity entity) {
        return entity instanceof GhostZombieEntity
                || entity instanceof GhostCreeperEntity
                || entity instanceof GhostSkeletonEntity
                || entity instanceof GhostSteveEntity;
    }
}