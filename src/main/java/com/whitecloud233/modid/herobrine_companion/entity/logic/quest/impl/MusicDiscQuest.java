package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;

/**
 * 委托 3「人类的音乐」：把任意一张音乐唱片交给 Hero 换取记忆碎片。
 * 一张唱片，换一段属于他的记忆——「我的记忆，换你的音乐。」
 */
public class MusicDiscQuest implements HeroQuest {

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_3"));
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

    /** 交付物判定：任何音乐唱片。客户端开屏拦截与服务端消费共用同一规则。 */
    public static boolean isDeliveryItem(ItemStack stack) {
        return stack.getItem() instanceof RecordItem;
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_3"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.MEMORY_SHARD.get(), 1));
        HeroQuestManager.addTrust(player, 8);
    }
}