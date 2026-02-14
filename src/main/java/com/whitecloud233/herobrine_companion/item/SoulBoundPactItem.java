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
        // 客户端无法直接获取玩家的 Tag 来决定物品是否发光，除非同步。
        // 为了简单起见，这里我们不让物品发光，或者总是发光，或者依赖物品 NBT (但这会与 Tag 不同步)。
        // 如果要完美同步，需要 Packet。
        // 这里我们简单处理：如果是在客户端且持有该物品的玩家有 Tag，则发光。
        // 注意：Minecraft.getInstance().player 在 Item 类中直接调用是不安全的，需小心。
        // 但 isFoil 是客户端方法。
        
        // 实际上，为了简单且不引入复杂同步，我们可以让物品本身也存储一个 NBT 状态用于显示，
        // 虽然这可能导致不同步（比如用命令移除了 Tag），但对于单人游戏或正常流程足够了。
        // 或者，我们只在 use 方法中切换 Tag，不改变物品 NBT，这样物品就不会发光。
        // 题目没要求发光，所以这里不强制实现 isFoil。
        return super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.soul_bound_pact.desc"));
        // 由于无法在 Tooltip 中轻易获取当前查看物品的玩家（context 不包含 player），
        // 我们无法准确显示“当前状态：已激活”。
        // 除非我们假设查看者是客户端玩家。
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
