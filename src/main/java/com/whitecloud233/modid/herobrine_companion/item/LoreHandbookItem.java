package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.client.gui.LoreHandbookScreen;
import net.minecraft.client.Minecraft;
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
            // 打开 GUI
            Minecraft.getInstance().setScreen(new LoreHandbookScreen(stack));
        }
        return InteractionResultHolder.success(stack);
    }

    public static void addFragment(ItemStack handbook, String fragmentId) {
        CompoundTag tag = handbook.getOrCreateTag();

        ListTag fragments;
        if (tag.contains("collected_fragments", 9)) {
            fragments = tag.getList("collected_fragments", 8);
        } else {
            fragments = new ListTag();
        }

        // Avoid duplicates
        boolean alreadyHas = false;
        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.getString(i).equals(fragmentId)) {
                alreadyHas = true;
                break;
            }
        }

        if (!alreadyHas) {
            fragments.add(StringTag.valueOf(fragmentId));
            tag.put("collected_fragments", fragments);
        }
    }
}