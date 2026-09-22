package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * 委托 2「安抚末影人」：给任务末影人送上土块（+25 点）或夜晚潜行靠近它（+5 点/秒），攒满 100 点。
 * 杀死它或它消失都会失败。奖励末影珍珠 x16 + 信任 +10。
 */
public class PacifyEndermanQuest implements HeroQuest {

    private static final int TARGET_PACIFY_POINTS = 100;
    private static final int DIRT_POINTS = 25;
    private static final int SNEAK_POINTS_PER_SECOND = 5;

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_2"));
        if (player.level() instanceof ServerLevel serverLevel) {
            EnderMan questEnderman = new EnderMan(EntityType.ENDERMAN, serverLevel);

            double angle = player.level().random.nextDouble() * 2 * Math.PI;
            double distance = 10 + player.level().random.nextDouble() * 5;
            int x = (int) (player.getX() + Math.cos(angle) * distance);
            int z = (int) (player.getZ() + Math.sin(angle) * distance);
            int y = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

            questEnderman.teleportTo(x + 0.5, y, z + 0.5);
            questEnderman.setCustomName(Component.translatable("entity.herobrine_companion.quest_enderman"));
            questEnderman.setCustomNameVisible(true);
            questEnderman.setPersistenceRequired();
            questEnderman.getTags().add("quest_target_for:" + player.getUUID());

            serverLevel.addFreshEntity(questEnderman);
            progress.setTargetUuid(questEnderman.getUUID());
        }
    }

    @Override
    public boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(target instanceof EnderMan enderman) || !enderman.getUUID().equals(targetUuid)) {
            return false;
        }
        if (!stack.is(Items.DIRT)) return false;

        stack.shrink(1);
        enderman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        updateProgress(player, progress, DIRT_POINTS);
        return true; // 交互被委托消费
    }

    @Override
    public void onTick(ServerPlayer player, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null) return;
        Entity targetEntity = ((ServerLevel) player.level()).getEntity(targetUuid);

        if (targetEntity instanceof EnderMan questEnderman && targetEntity.isAlive()) {
            if (player.isShiftKeyDown() && player.distanceToSqr(questEnderman) < 100) {
                if (questEnderman.getTarget() == player) return;
                if (player.tickCount % 20 == 0) {
                    updateProgress(player, progress, SNEAK_POINTS_PER_SECOND);
                }
            }
        } else if (player.tickCount % 100 == 0) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_target_gone");
        }
    }

    @Override
    public void onKill(ServerPlayer player, Entity killed, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid != null && killed.getUUID().equals(targetUuid)) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_target_died");
        }
    }

    @Override
    public void onCancel(ServerPlayer player, QuestProgress progress) {
        discardTarget(player, progress);
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_2"));
        HeroQuestManager.giveItem(player, new ItemStack(Items.ENDER_PEARL, 16));
        HeroQuestManager.addTrust(player, 10);
        discardTarget(player, progress);
    }

    private void updateProgress(ServerPlayer player, QuestProgress progress, int amount) {
        int value = Math.min(progress.progress() + amount, TARGET_PACIFY_POINTS);
        progress.setProgress(value);
        player.displayClientMessage(Component.translatable("message.herobrine_companion.quest.pacify_progress", value, TARGET_PACIFY_POINTS), true);
        if (value >= TARGET_PACIFY_POINTS) {
            HeroQuestManager.completeQuest(player);
        }
    }

    private static void discardTarget(ServerPlayer player, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return;
        Entity target = serverLevel.getEntity(targetUuid);
        if (target instanceof EnderMan) {
            target.discard();
        }
    }
}