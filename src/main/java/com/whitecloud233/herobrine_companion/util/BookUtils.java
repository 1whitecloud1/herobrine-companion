package com.whitecloud233.herobrine_companion.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

public class BookUtils {

    public static ItemStack createHerobrineLoreBook() {
        // 1. 创建成书物品堆
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        // 2. 准备页面内容 (List<Filterable<Component>>)
        List<Filterable<Component>> pages = new ArrayList<>();

        // 使用翻译键获取长文本
        Component page1 = Component.translatable("book.herobrine_companion.book.lore.page1");
        pages.add(Filterable.passThrough(page1));
        Component page2 = Component.translatable("book.herobrine_companion.book.lore.page2");
        pages.add(Filterable.passThrough(page2));
        Component page3 = Component.translatable("book.herobrine_companion.book.lore.page3");
        pages.add(Filterable.passThrough(page3));
        Component page4 = Component.translatable("book.herobrine_companion.book.lore.page4");
        pages.add(Filterable.passThrough(page4));
        

        // 3. 构建书本内容组件
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(Component.translatable("book.herobrine_companion.book.lore.title").getString()), // 标题
                Component.translatable("book.herobrine_companion.book.lore.author").getString(),                       // 作者
                0,                                 // 0=Original
                pages,                             // 页面内容
                true                               // resolved
        );

        // 4. 将组件设置进物品
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        return book;
    }
}
