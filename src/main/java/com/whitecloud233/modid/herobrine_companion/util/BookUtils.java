package com.whitecloud233.modid.herobrine_companion.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BookUtils {

    public static ItemStack createHerobrineLoreBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        CompoundTag tag = new CompoundTag();
        tag.putString("title", Component.translatable("book.herobrine_companion.book.lore.title").getString());
        tag.putString("author", Component.translatable("book.herobrine_companion.book.lore.author").getString());
        tag.putInt("generation", 0);

        ListTag pages = new ListTag();
        
        // 1.20.1 书页内容必须是 JSON 格式的字符串
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.herobrine_companion.book.lore.page1"))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.herobrine_companion.book.lore.page2"))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.herobrine_companion.book.lore.page3"))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.herobrine_companion.book.lore.page4"))));
        
        tag.put("pages", pages);
        tag.putBoolean("resolved", true);

        book.setTag(tag);

        return book;
    }
}
