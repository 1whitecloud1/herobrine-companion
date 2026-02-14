package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

public class ModToolTiers {
    public static final Tier END = TierSortingRegistry.registerTier(
            new ForgeTier(5, 5000, 15.0F, 8.0F, 25,
                    net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL, () -> Ingredient.of(HerobrineCompanion.GLITCH_FRAGMENT.get())),
            new ResourceLocation(HerobrineCompanion.MODID, "end"),
            List.of(Tiers.NETHERITE), List.of());
}
