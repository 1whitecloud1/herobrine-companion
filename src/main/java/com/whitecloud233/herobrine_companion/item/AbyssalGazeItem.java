package com.whitecloud233.herobrine_companion.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class AbyssalGazeItem extends Item {

    private static final String TAG_ACTIVE = "herobrine_companion.abyssal_gaze_active";
    private static final String ITEM_TAG_ACTIVE = "Active";

    public AbyssalGazeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 配置检查
        if (!com.whitecloud233.herobrine_companion.config.Config.abyssalGazeEnabled) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.item_disabled_in_config").withStyle(net.minecraft.ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            // 根据玩家身上的 Tag 判断当前状态
            boolean isPlayerActive = player.getTags().contains(TAG_ACTIVE);

            if (isPlayerActive) {
                // 如果玩家已激活，则执行关闭逻辑
                player.removeTag(TAG_ACTIVE);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.abyssal_gaze.disabled"));
                player.removeEffect(MobEffects.NIGHT_VISION);
                setActive(stack, false);
            } else {
                // 如果玩家未激活，则执行开启逻辑
                player.addTag(TAG_ACTIVE);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.abyssal_gaze.enabled"));
                // 给予初始夜视效果，后续由 PlayerMechanicsEventHandler 维持
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
                setActive(stack, true);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // 在服务端同步物品状态与玩家状态
        if (!level.isClientSide && entity instanceof Player player) {
            // 每 20 tick (1秒) 检查一次，减少性能开销
            if (level.getGameTime() % 20 == 0) {
                boolean isPlayerActive = player.getTags().contains(TAG_ACTIVE);
                boolean isStackActive = isActive(stack);

                // 如果物品显示状态与玩家实际状态不一致，则更新物品
                if (isPlayerActive != isStackActive) {
                    setActive(stack, isPlayerActive);
                }
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isActive(stack);
    }

    // 1.21.1: 参数改为 TooltipContext
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.abyssal_gaze.desc"));
        if (isActive(stack)) {
            tooltipComponents.add(Component.translatable("item.herobrine_companion.abyssal_gaze.active").withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            tooltipComponents.add(Component.translatable("item.herobrine_companion.abyssal_gaze.inactive").withStyle(net.minecraft.ChatFormatting.RED));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // 1.21.1: 替代原有的 getTag() 检测，改用 CustomData
    private boolean isActive(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.contains(ITEM_TAG_ACTIVE) && customData.copyTag().getBoolean(ITEM_TAG_ACTIVE);
    }

    // 1.21.1: 替代原有的 setTag()，改用 CustomData.update
    private void setActive(ItemStack stack, boolean active) {
        if (active) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ITEM_TAG_ACTIVE, true));
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ITEM_TAG_ACTIVE));
        }
    }
}