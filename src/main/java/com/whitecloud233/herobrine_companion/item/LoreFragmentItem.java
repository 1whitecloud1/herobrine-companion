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
                boolean addedToHandbook = LoreHandbookItem.addFragment(handbookStackOpt.get(), fragmentId);
                Component fragmentTitle = Component.translatable("lore.herobrine_companion." + fragmentId + ".title");

                if (player instanceof ServerPlayer serverPlayer) {
                    CompoundTag playerData = player.getPersistentData();

                    ListTag collectedLore;
                    if (playerData.contains("HeroCollectedLore", Tag.TAG_LIST)) {
                        collectedLore = playerData.getList("HeroCollectedLore", Tag.TAG_STRING);
                    } else {
                        collectedLore = new ListTag();
                    }

                    boolean alreadyCollectedByPlayer = false;
                    for (Tag t : collectedLore) {
                        if (t.getAsString().equals(fragmentId)) {
                            alreadyCollectedByPlayer = true;
                            break;
                        }
                    }

                    if (!alreadyCollectedByPlayer) {
                        // 1. 玩家首次收集
                        collectedLore.add(StringTag.valueOf(fragmentId));
                        playerData.put("HeroCollectedLore", collectedLore);

                        player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.collected", fragmentTitle).withStyle(ChatFormatting.GREEN));

                        // 2. 1.21.1 触发进度 (使用 AdvancementHolder)
                        ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, fragmentId);
                        AdvancementHolder advancement = serverPlayer.getServer().getAdvancements().get(advancementId);
                        if (advancement != null) {
                            serverPlayer.getAdvancements().award(advancement, "has_" + fragmentId);
                        }

                        // 3. 加入待处理队列
                        ListTag pendingLore;
                        if (playerData.contains("HeroPendingLore", Tag.TAG_LIST)) {
                            pendingLore = playerData.getList("HeroPendingLore", Tag.TAG_STRING);
                        } else {
                            pendingLore = new ListTag();
                        }
                        pendingLore.add(StringTag.valueOf(fragmentId));
                        playerData.put("HeroPendingLore", pendingLore);

                        // 4. 发放信任度
                        int currentReward = playerData.contains("HeroPendingTrustReward") ? playerData.getInt("HeroPendingTrustReward") : 0;
                        playerData.putInt("HeroPendingTrustReward", currentReward + 20);

                    } else {
                        // 玩家已收集过
                        if (addedToHandbook) {
                            player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.added_to_new_handbook", fragmentTitle).withStyle(ChatFormatting.GREEN));
                        } else {
                            player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.already_collected", fragmentTitle).withStyle(ChatFormatting.YELLOW));
                        }
                    }
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