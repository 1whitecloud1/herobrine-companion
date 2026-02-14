package com.whitecloud233.modid.herobrine_companion.util;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModTags {
    public static class Items {
        public static final TagKey<Item> HERO_ITEMS = tag("hero_items");

        private static TagKey<Item> tag(String name) {
            return ItemTags.create(new ResourceLocation(HerobrineCompanion.MODID, name));
        }
    }
}
