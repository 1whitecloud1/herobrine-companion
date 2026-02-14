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

public class TranscendencePermitItem extends Item {

    public TranscendencePermitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            String tag = "herobrine_companion.transcendence_permit_active";
            // 使用 PersistentData 替代 Tags，更稳定
            boolean isActive = player.getPersistentData().getBoolean(tag);

            if (!isActive) {
                // 启用
                player.getPersistentData().putBoolean(tag, true);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.transcendence_permit.enabled"));
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            } else {
                // 禁用
                player.getPersistentData().remove(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.transcendence_permit.disabled"));
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.transcendence_permit.desc"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}