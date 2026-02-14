package com.whitecloud233.herobrine_companion.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class TranscendencePermitItem extends Item {

    public TranscendencePermitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            String tag = "herobrine_companion.transcendence_permit_active";
            boolean isActive = player.getTags().contains(tag);
            
            if (!isActive) {
                // 启用
                player.addTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.transcendence_permit.enabled"));
                
                // 立即给予飞行能力
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else {
                // 禁用
                player.removeTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.transcendence_permit.disabled"));
                
                // 如果不是创造/旁观模式，移除飞行能力
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 类似 SoulBoundPactItem，不强制发光，或者可以根据客户端逻辑判断
        return super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.transcendence_permit.desc"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
