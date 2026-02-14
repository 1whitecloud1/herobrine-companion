package com.whitecloud233.modid.herobrine_companion.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SoulBoundPactItem extends Item {

    public SoulBoundPactItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            String tag = "herobrine_companion.soul_bound_pact_active";
            boolean isActive = player.getTags().contains(tag);

            if (!isActive) {
                // 启用
                player.addTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.soul_bound_pact.enabled"));
            } else {
                // 禁用
                player.removeTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.soul_bound_pact.disabled"));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.soul_bound_pact.desc"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}