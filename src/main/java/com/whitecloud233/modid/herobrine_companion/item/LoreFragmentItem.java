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
                LoreHandbookItem.addFragment(handbookStackOpt.get(), fragmentId);
                // 使用标题 key 发送消息
                Component fragmentTitle = Component.translatable("lore.herobrine_companion." + fragmentId + ".title");
                player.sendSystemMessage(Component.translatable("item.herobrine_companion.lore_fragment.collected", fragmentTitle).withStyle(ChatFormatting.GREEN));

                // 触发进度 (1.20.1 写法)
                if (player instanceof ServerPlayer serverPlayer) {
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

                    // [修改] 将碎片 ID 记录到玩家的 NBT 中，等待 Herobrine 读取
                    CompoundTag playerData = player.getPersistentData();
                    ListTag pendingLore;
                    if (playerData.contains("HeroPendingLore", Tag.TAG_LIST)) {
                        pendingLore = playerData.getList("HeroPendingLore", Tag.TAG_STRING);
                    } else {
                        pendingLore = new ListTag();
                    }

                    // 避免在 pending 列表中重复添加同一个ID
                    boolean alreadyPending = false;
                    for (Tag t : pendingLore) {
                        if (t.getAsString().equals(fragmentId)) {
                            alreadyPending = true;
                            break;
                        }
                    }

                    if (!alreadyPending) {
                        pendingLore.add(StringTag.valueOf(fragmentId));
                        playerData.put("HeroPendingLore", pendingLore);
                    }

                    // [新增] 增加 20 点信任度奖励 (HeroPendingTrustReward)
                    // HeroLogic 会在下一次检测时自动读取此值并增加信任度
                    int currentReward = 0;
                    if (playerData.contains("HeroPendingTrustReward")) {
                        currentReward = playerData.getInt("HeroPendingTrustReward");
                    }
                    playerData.putInt("HeroPendingTrustReward", currentReward + 20);
                }

                // 消耗物品 (即使是创造模式也消耗，防止刷屏)
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
        // 优先检查主手和副手
        if (player.getMainHandItem().getItem() == HerobrineCompanion.LORE_HANDBOOK.get()) {
            return Optional.of(player.getMainHandItem());
        }
        if (player.getOffhandItem().getItem() == HerobrineCompanion.LORE_HANDBOOK.get()) {
            return Optional.of(player.getOffhandItem());
        }
        
        // 然后检查背包
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