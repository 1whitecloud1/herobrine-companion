package com.whitecloud233.modid.herobrine_companion.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LoreHandbookItem extends Item {
    public LoreHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // [安全修改]：不直接 new Screen，而是呼叫客户端钩子
            com.whitecloud233.modid.herobrine_companion.client.ClientHooks.openHandbook(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    // [修改] 返回 boolean 值，true 表示新添加，false 表示图鉴中已存在
    public static boolean addFragment(ItemStack handbook, String fragmentId) {
        CompoundTag tag = handbook.getOrCreateTag();

        ListTag fragments;
        if (tag.contains("collected_fragments", 9)) {
            fragments = tag.getList("collected_fragments", 8);
        } else {
            fragments = new ListTag();
        }

        // Avoid duplicates
        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.getString(i).equals(fragmentId)) {
                return false; // 图鉴里已经有这个碎片了
            }
        }

        fragments.add(StringTag.valueOf(fragmentId));
        tag.put("collected_fragments", fragments);
        return true; // 成功添加到图鉴
    }
}