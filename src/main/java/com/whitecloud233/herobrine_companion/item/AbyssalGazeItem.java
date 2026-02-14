package com.whitecloud233.herobrine_companion.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class AbyssalGazeItem extends Item {

    public AbyssalGazeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            String tag = "herobrine_companion.abyssal_gaze_active";
            
            // 检查玩家当前是否有该标签
            boolean hasTag = player.getTags().contains(tag);
            
            if (!hasTag) {
                // 启用
                player.addTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.abyssal_gaze.enabled"));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
            } else {
                // 禁用
                player.removeTag(tag);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.abyssal_gaze.disabled"));
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 物品本身不再存储状态，始终不发光，或者根据玩家状态发光？
        // 通常物品在物品栏里，我们无法轻易获取持有它的玩家（除非在 inventoryTick 中更新 NBT）
        // 为了避免 NBT 问题，我们移除物品上的 NBT 逻辑。
        // 如果需要发光，可以在 inventoryTick 中检查玩家标签并设置 NBT，但这会增加复杂性。
        // 用户要求 "have an empty NBTtag"，所以我们不再在物品上存储 "Active" 标签。
        return false; 
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.abyssal_gaze.desc"));
        // 移除基于物品 NBT 的状态显示，因为我们不再在物品上存储状态。
        // 如果需要显示状态，可能需要根据客户端玩家的状态（但这在 Tooltip 中可能不准确，如果是查看别人的物品）。
        // 简单起见，只保留描述。
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
