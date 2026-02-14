package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, HerobrineCompanion.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(HerobrineCompanion.HERO_SHELTER.get());
        basicItem(HerobrineCompanion.ETERNAL_KEY.get());
        basicItem(HerobrineCompanion.UNSTABLE_GUNPOWDER.get());
        basicItem(HerobrineCompanion.CORRUPTED_CODE.get());
        basicItem(HerobrineCompanion.VOID_MARROW.get());
        basicItem(HerobrineCompanion.GLITCH_FRAGMENT.get());
        basicItem(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get()); // Texture missing, commented out to prevent crash
        basicItem(HerobrineCompanion.MEMORY_SHARD.get());
        basicItem(HerobrineCompanion.RECALL_STONE.get());
        basicItem(HerobrineCompanion.ABYSSAL_GAZE.get());
        basicItem(HerobrineCompanion.SOUL_BOUND_PACT.get());
        basicItem(HerobrineCompanion.TRANSCENDENCE_PERMIT.get());
        
        // Lore System Items
        basicItem(HerobrineCompanion.LORE_HANDBOOK.get());
        basicItem(HerobrineCompanion.LORE_FRAGMENT.get());

        // Spawn Eggs
        withExistingParent(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_STEVE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));

        // Block Items
        // The portal block item should use the block texture, or a specific item texture if it exists.
        // Since it's a portal, it likely doesn't have a simple "item/end_ring_portal" texture unless created.
        // For now, let's assume it uses the block model or a placeholder.
        // If it's a block item, we usually use withExistingParent and point to the block model.
        // However, EndRingPortalBlock likely has a custom renderer or specific blockstate.

        // Let's try to use a generated item model but point to a valid texture,
        // OR just use the block model if it exists.
        // Since the error says "Texture ... does not exist", it means basicItem() is looking for
        // items/end_ring_portal.png which is missing.

        // Option 1: If we have a block model, use it.
        // withExistingParent(HerobrineCompanion.END_RING_PORTAL_ITEM.getId().getPath(), modLoc("block/end_ring_portal"));

        // Option 2: If we want a flat item, we need the texture.
        // Since I can't create the texture file, I will comment this out to prevent the crash.
        // You should add the texture or uncomment this when the texture exists.

        // basicItem(HerobrineCompanion.END_RING_PORTAL_ITEM.get());
    }
}
