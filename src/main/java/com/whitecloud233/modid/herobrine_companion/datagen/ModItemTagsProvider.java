package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
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
                .add(ModItems.HERO_SHELTER.get())
                .add(ModItems.ETERNAL_KEY.get())
                .add(ModItems.ABYSSAL_GAZE.get())
                .add(ModItems.UNSTABLE_GUNPOWDER.get())
                .add(ModItems.CORRUPTED_CODE.get())
                .add(ModItems.VOID_MARROW.get())
                .add(ModItems.GLITCH_FRAGMENT.get())
                .add(ModItems.SOURCE_CODE_FRAGMENT.get())
                .add(ModItems.MEMORY_SHARD.get())
                .add(ModItems.RECALL_STONE.get())
                .add(ModItems.AWAKENED_VESSEL.get())
                .add(ModItems.SOUL_BOUND_PACT.get())
                .add(ModItems.TRANSCENDENCE_PERMIT.get())
                .add(ModItems.POEM_OF_THE_END.get())
                .add(ModItems.LORE_FRAGMENT.get())
                .add(ModItems.LORE_HANDBOOK.get())
                .add(ModItems.SOURCE_FLOW.get())
                .add(ModItems.END_RING_PORTAL_ITEM.get())
                .add(ModItems.GHOST_CREEPER_SPAWN_EGG.get())
                .add(ModItems.GHOST_ZOMBIE_SPAWN_EGG.get())
                .add(ModItems.GHOST_SKELETON_SPAWN_EGG.get())
                .add(ModItems.GHOST_STEVE_SPAWN_EGG.get());
    }
}
