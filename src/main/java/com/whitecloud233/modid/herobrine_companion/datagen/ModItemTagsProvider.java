package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput pOutput, CompletableFuture<HolderLookup.Provider> pLookupProvider, CompletableFuture<TagLookup<Block>> pBlockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(pOutput, pLookupProvider, pBlockTags, HerobrineCompanion.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        tag(ModTags.Items.HERO_ITEMS)
                .add(HerobrineCompanion.HERO_SHELTER.get())
                .add(HerobrineCompanion.ETERNAL_KEY.get())
                .add(HerobrineCompanion.UNSTABLE_GUNPOWDER.get())
                .add(HerobrineCompanion.CORRUPTED_CODE.get())
                .add(HerobrineCompanion.VOID_MARROW.get())
                .add(HerobrineCompanion.GLITCH_FRAGMENT.get())
                .add(HerobrineCompanion.MEMORY_SHARD.get())
                .add(HerobrineCompanion.RECALL_STONE.get())
                .add(HerobrineCompanion.END_RING_PORTAL_ITEM.get())
                .add(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.get())
                .add(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.get())
                .add(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.get());
    }
}
