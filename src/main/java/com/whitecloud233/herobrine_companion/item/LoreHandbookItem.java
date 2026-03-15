package com.whitecloud233.herobrine_companion.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class LoreHandbookItem extends Item {
    public LoreHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.whitecloud233.herobrine_companion.client.ClientHooks.openHandbook(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    // [修改] 1.21.1 使用 CustomData 读写数据
    public static boolean addFragment(ItemStack handbook, String fragmentId) {
        CustomData customData = handbook.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        ListTag fragments;
        if (tag.contains("collected_fragments", Tag.TAG_LIST)) {
            fragments = tag.getList("collected_fragments", Tag.TAG_STRING);
        } else {
            fragments = new ListTag();
        }

        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.getString(i).equals(fragmentId)) {
                return false; // 图鉴里已存在
            }
        }

        fragments.add(StringTag.valueOf(fragmentId));
        tag.put("collected_fragments", fragments);

        // 写回 DataComponent
        handbook.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }
}