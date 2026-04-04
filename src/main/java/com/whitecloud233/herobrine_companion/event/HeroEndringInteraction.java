package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.BookUtils;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroEndringInteraction {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getItemStack().getItem() instanceof HeroSummonItem) {
            return;
        }

        // 【核心修复1】：去掉了 !isClientSide 的判定！我们要同时在客户端和服务端进行拦截
        if (event.getTarget() instanceof HeroEntity hero) {

            // 如果正在打架，什么剧情都不准触发，直接放行给战斗管理器！
            if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE) ||
                    hero.getPersistentData().getBoolean("IsChallengeActive")) {
                return;
            }

            if (hero.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                return;
            }

            // 【核心修复2】：拦截副手，只允许主手执行，绝对防止一次右键连跳两段剧情！
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            Player player = event.getEntity();
            CompoundTag data = player.getPersistentData();
            int stage = data.getInt("WakeUpStage");

            // 【核心修复3】：同步更新局部变量 stage
            if (stage == 0) {
                stage = 1;
                data.putInt("WakeUpStage", 1);
            }

            boolean handled = false;

            // 剧情分发（仅在服务端执行实际的给物品和传送操作，但客户端同样会被标记为 handled 从而阻止 GUI 弹出）
            if (stage == 1) {
                if (!event.getLevel().isClientSide) {
                    hero.teleportTo(player.getX() + player.getLookAngle().x * 3, player.getY(), player.getZ() + player.getLookAngle().z * 3);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_1"));
                    data.putInt("WakeUpStage", 2);
                }
                handled = true;

            } else if (stage == 2) {
                if (!event.getLevel().isClientSide) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_2"));

                    ItemStack book = BookUtils.createHerobrineLoreBook();
                    // 【核心修复4】：防止背包满了把书吞掉
                    if (!player.getInventory().add(book)) {
                        player.drop(book, false);
                    }

                    data.putInt("WakeUpStage", 3);
                }
                handled = true;

            } else if (stage == 3) {
                if (!event.getLevel().isClientSide) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_3"));
                }
                handled = true;
            }

            // 如果剧情已经被处理，强行掐断交互事件，坚决不让控制面板弹出来！
            if (handled) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
