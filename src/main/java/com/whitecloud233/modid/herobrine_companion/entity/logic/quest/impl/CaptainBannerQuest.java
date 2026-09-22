package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;

/**
 * 委托 6「献给审判者的旗帜」：击杀一名掠夺者队长，把旗帜交给 Hero。
 * 奖励损坏片段 x3 + 信任 +12。
 */
public class CaptainBannerQuest implements HeroQuest {

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_6"));
    }

    @Override
    public void onKill(ServerPlayer player, Entity killed, QuestProgress progress) {
        // 队长/巡逻队长以旗帜为冠，据此识别
        if (killed instanceof Raider raider && hasCaptainBanner(raider) && progress.progress() == 0) {
            progress.setProgress(1);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_captain_down"));
        }
    }

    @Override
    public boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack, QuestProgress progress) {
        if (!HeroQuestManager.isQuestHero(target, progress)) return false;
        if (!isDeliveryItem(stack)) return false;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        HeroQuestManager.completeQuest(player);
        return true; // 消费交互：不再打开 Hero GUI
    }

    /** 交付物判定：任何旗帜。客户端开屏拦截与服务端消费共用同一规则。 */
    public static boolean isDeliveryItem(ItemStack stack) {
        return stack.getItem() instanceof BannerItem;
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_6"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.CORRUPTED_CODE.get(), 3));
        HeroQuestManager.addTrust(player, 12);
    }

    private static boolean hasCaptainBanner(Raider raider) {
        return raider.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem;
    }
}