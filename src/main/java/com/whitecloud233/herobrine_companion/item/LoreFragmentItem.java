package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public class LoreFragmentItem extends Item {
    public static final String LORE_ID_KEY = "lore_id";

    public LoreFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack fragmentStack = player.getItemInHand(hand);
        
        // 适配 1.20.5+ DataComponents
        CustomData customData = fragmentStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag fragmentTag = customData != null ? customData.copyTag() : new CompoundTag();

        if (!fragmentTag.contains(LORE_ID_KEY)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.corrupted").withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(fragmentStack);
        }

        String fragmentId = fragmentTag.getString(LORE_ID_KEY);

        Optional<ItemStack> handbookStackOpt = findHandbook(player);

        if (handbookStackOpt.isPresent()) {
            if (!level.isClientSide) {
                LoreHandbookItem.addFragment(handbookStackOpt.get(), fragmentId);
                // Use the title key for the message
                Component fragmentTitle = Component.translatable("lore.herobrine_companion." + fragmentId + ".title");
                player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.collected", fragmentTitle).withStyle(ChatFormatting.GREEN));
                
                // Trigger advancement
                if (player instanceof ServerPlayer serverPlayer) {
                    ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, fragmentId);
                    AdvancementHolder advancement = serverPlayer.getServer().getAdvancements().get(advancementId);
                    if (advancement != null) {
                        serverPlayer.getAdvancements().award(advancement, "has_" + fragmentId);
                    } else {
                        System.out.println("Failed to find advancement: " + advancementId);
                    }
                    
                    // [修改] 将碎片 ID 记录到玩家的 NBT 中，等待 Herobrine 读取
                    CompoundTag playerData = player.getPersistentData();
                    ListTag pendingLore;
                    if (playerData.contains("HeroPendingLore", Tag.TAG_LIST)) {
                        pendingLore = playerData.getList("HeroPendingLore", Tag.TAG_STRING);
                    } else {
                        pendingLore = new ListTag();
                    }
                    pendingLore.add(StringTag.valueOf(fragmentId));
                    playerData.put("HeroPendingLore", pendingLore);
                }

                fragmentStack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(fragmentStack, level.isClientSide());
        } else {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.no_handbook").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResultHolder.fail(fragmentStack);
        }
    }

    private Optional<ItemStack> findHandbook(Player player) {
        if (player.getMainHandItem().is(HerobrineCompanion.LORE_HANDBOOK.get())) {
            return Optional.of(player.getMainHandItem());
        }
        if (player.getOffhandItem().is(HerobrineCompanion.LORE_HANDBOOK.get())) {
            return Optional.of(player.getOffhandItem());
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(HerobrineCompanion.LORE_HANDBOOK.get())) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : null;

        if (tag != null && tag.contains(LORE_ID_KEY)) {
            String fragmentId = tag.getString(LORE_ID_KEY);
            tooltip.add(Component.translatable("lore.herobrine_companion." + fragmentId + ".title").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.herobrine_companion.lore_fragment.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable("item.herobrine_companion.lore_fragment.corrupted").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}