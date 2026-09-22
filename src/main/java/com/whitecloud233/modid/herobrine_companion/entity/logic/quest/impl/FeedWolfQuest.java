package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * 委托 4「它们还在等主人」：给守在村子边上的任务狼喂骨头，喂满 5 次；
 * 若它提前愿意被驯服（原版驯服机制），则立即完成。
 * 奖励生牛肉 x8 + 信任 +12。杀掉它则委托失败。
 */
public class FeedWolfQuest implements HeroQuest {

    private static final int TARGET_FEEDS = 5;

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_4"));
        if (player.level() instanceof ServerLevel serverLevel) {
            Wolf wolf = new Wolf(EntityType.WOLF, serverLevel);

            double angle = player.level().random.nextDouble() * 2 * Math.PI;
            double distance = 5 + player.level().random.nextDouble() * 5;
            int x = (int) (player.getX() + Math.cos(angle) * distance);
            int z = (int) (player.getZ() + Math.sin(angle) * distance);
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            wolf.moveTo(x + 0.5, y, z + 0.5, player.getYRot(), 0);
            wolf.setCustomName(Component.translatable("entity.herobrine_companion.quest_wolf"));
            wolf.setCustomNameVisible(true);
            wolf.setPersistenceRequired();
            wolf.setOrderedToSit(true); // 像在门口等主人那样坐着
            wolf.getTags().add("herobrine_quest_wolf:" + player.getUUID());

            serverLevel.addFreshEntity(wolf);
            progress.setTargetUuid(wolf.getUUID());
        }
    }

    @Override
    public boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(target instanceof Wolf wolf) || !wolf.getUUID().equals(targetUuid)) {
            return false;
        }
        // 骨头：不取消交互——原版驯服逻辑照常进行（吃骨头 / 出爱心），这里只记账
        if (!stack.is(Items.BONE)) return false;

        progress.addProgress(1);
        player.displayClientMessage(Component.translatable("message.herobrine_companion.quest_wolf_feed_progress", progress.progress(), TARGET_FEEDS), true);

        if (progress.progress() >= TARGET_FEEDS) {
            HeroQuestManager.completeQuest(player);
        }
        return false;
    }

    @Override
    public void onTick(ServerPlayer player, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return;
        Entity targetEntity = serverLevel.getEntity(targetUuid);

        if (!(targetEntity instanceof Wolf wolf) || !wolf.isAlive()) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_target_gone");
            return;
        }
        // 它愿意跟玩家走了（原版驯服成功）：即时完成
        if (wolf.isTame()) {
            HeroQuestManager.completeQuest(player);
        }
    }

    @Override
    public void onKill(ServerPlayer player, Entity killed, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid != null && killed.getUUID().equals(targetUuid)) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_wolf_killed");
        }
    }

    @Override
    public void onCancel(ServerPlayer player, QuestProgress progress) {
        removeWolf(player, progress, false);
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        Wolf wolf = findWolf(player, progress);
        boolean tamed = wolf != null && wolf.isTame();

        if (tamed) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_4_tamed"));
        } else {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_4"));
        }

        HeroQuestManager.giveItem(player, new ItemStack(Items.COOKED_BEEF, 8));
        HeroQuestManager.addTrust(player, 12);

        // 被驯服的狼已经是玩家的伙伴，留下它；未被驯服的按“喂饱”完成处理
        removeWolf(player, progress, tamed);
    }

    private static Wolf findWolf(ServerPlayer player, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return null;
        Entity target = serverLevel.getEntity(targetUuid);
        return target instanceof Wolf wolf ? wolf : null;
    }

    private static void removeWolf(ServerPlayer player, QuestProgress progress, boolean keep) {
        Wolf wolf = findWolf(player, progress);
        if (wolf != null && !keep) {
            wolf.discard();
        }
    }
}