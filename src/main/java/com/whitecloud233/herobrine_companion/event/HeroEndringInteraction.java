package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.BookUtils;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroEndringInteraction {

    // 1. 觉醒协议对话 (右键 Hero)
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 跳过物品逻辑
        if (event.getItemStack().getItem() instanceof HeroSummonItem) {
            return;
        }

        if (event.getTarget() instanceof HeroEntity hero && !event.getLevel().isClientSide) {
            // 维度检查
            if (event.getLevel().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                return;
            }

            Player player = event.getEntity();
            CompoundTag data = player.getPersistentData();
            int stage = data.getInt("WakeUpStage");
            
            if (stage == 0) data.putInt("WakeUpStage", 1);
            
            boolean handled = false;
            if (stage == 1) {
                hero.teleportTo(player.getX() + player.getLookAngle().x * 3, player.getY(), player.getZ() + player.getLookAngle().z * 3);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_1"));
                data.putInt("WakeUpStage", 2);
                handled = true;
            } else if (stage == 2) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_2"));
                
                // [修改] 使用 BookUtils 创建成书
                ItemStack book = BookUtils.createHerobrineLoreBook();
                player.getInventory().add(book);

                data.putInt("WakeUpStage", 3);
                handled = true;
            } else if (stage == 3) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_3"));
                handled = true;
            }
            
            if (handled) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
