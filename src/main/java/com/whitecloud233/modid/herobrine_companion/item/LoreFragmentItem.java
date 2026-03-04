package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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

        // 1.20.1 NBT 获取方式
        CompoundTag fragmentTag = fragmentStack.getTag();

        if (fragmentTag == null || !fragmentTag.contains(LORE_ID_KEY)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.corrupted").withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(fragmentStack);
        }

        String fragmentId = fragmentTag.getString(LORE_ID_KEY);

        Optional<ItemStack> handbookStackOpt = findHandbook(player);

        if (handbookStackOpt.isPresent()) {
            if (!level.isClientSide) {
                // 写入图鉴 (返回 true 说明图鉴是第一次记录这个碎片)
                boolean addedToHandbook = LoreHandbookItem.addFragment(handbookStackOpt.get(), fragmentId);
                Component fragmentTitle = Component.translatable("lore.herobrine_companion." + fragmentId + ".title");

                if (player instanceof ServerPlayer serverPlayer) {
                    CompoundTag playerData = player.getPersistentData();

                    // [新增] 获取玩家维度的永久解锁记录，防止换图鉴刷信任度
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
                        // --- 玩家首次解锁该碎片 ---

                        // 1. 加入玩家永久记录
                        collectedLore.add(StringTag.valueOf(fragmentId));
                        playerData.put("HeroCollectedLore", collectedLore);

                        // 2. 发送成功收集消息
                        player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.collected", fragmentTitle).withStyle(ChatFormatting.GREEN));

                        // 3. 触发进度
                        ResourceLocation advancementId = new ResourceLocation(HerobrineCompanion.MODID, fragmentId);
                        Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(advancementId);
                        if (advancement != null) {
                            AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                            if (!progress.isDone()) {
                                for (String criterion : progress.getRemainingCriteria()) {
                                    serverPlayer.getAdvancements().award(advancement, criterion);
                                }
                            }
                        }

                        // 4. 加入 Herobrine 待处理队列
                        ListTag pendingLore;
                        if (playerData.contains("HeroPendingLore", Tag.TAG_LIST)) {
                            pendingLore = playerData.getList("HeroPendingLore", Tag.TAG_STRING);
                        } else {
                            pendingLore = new ListTag();
                        }
                        pendingLore.add(StringTag.valueOf(fragmentId));
                        playerData.put("HeroPendingLore", pendingLore);

                        // 5. 增加 20 点信任度奖励
                        int currentReward = playerData.getInt("HeroPendingTrustReward"); // 不存在默认返回 0
                        playerData.putInt("HeroPendingTrustReward", currentReward + 20);

                    } else {
                        // --- 玩家已经解锁过该碎片 ---
                        if (addedToHandbook) {
                            // 碎片没解锁过当前这本图鉴（比如玩家换了本新书），但玩家以前解锁过了，只提示补全图鉴，不给信任度
                            player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.added_to_new_handbook", fragmentTitle).withStyle(ChatFormatting.GREEN));
                        } else {
                            // 图鉴也有了，玩家也解锁过了
                            player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.already_collected", fragmentTitle).withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }

                // 无论是否重复，始终消耗物品 (防止刷屏/占格子)
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
        if (player.getMainHandItem().getItem() == HerobrineCompanion.LORE_HANDBOOK.get()) {
            return Optional.of(player.getMainHandItem());
        }
        if (player.getOffhandItem().getItem() == HerobrineCompanion.LORE_HANDBOOK.get()) {
            return Optional.of(player.getOffhandItem());
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == HerobrineCompanion.LORE_HANDBOOK.get()) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();

        if (tag != null && tag.contains(LORE_ID_KEY)) {
            String fragmentId = tag.getString(LORE_ID_KEY);
            tooltip.add(Component.translatable("lore.herobrine_companion." + fragmentId + ".title").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.herobrine_companion.lore_fragment.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable("item.herobrine_companion.lore_fragment.corrupted").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}