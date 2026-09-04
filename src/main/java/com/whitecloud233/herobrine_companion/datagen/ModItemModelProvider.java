package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, HerobrineCompanion.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.HERO_SHELTER.get());
        basicItem(ModItems.ETERNAL_KEY.get());
        basicItem(ModItems.UNSTABLE_GUNPOWDER.get());
        basicItem(ModItems.CORRUPTED_CODE.get());
        basicItem(ModItems.VOID_MARROW.get());
        basicItem(ModItems.GLITCH_FRAGMENT.get());
        basicItem(ModItems.SOURCE_CODE_FRAGMENT.get()); // Texture missing, commented out to prevent crash
        basicItem(ModItems.MEMORY_SHARD.get());
        basicItem(ModItems.RECALL_STONE.get());
        basicItem(ModItems.ABYSSAL_GAZE.get());
        basicItem(ModItems.AWAKENED_VESSEL.get());
        basicItem(ModItems.SOUL_BOUND_PACT.get());
        basicItem(ModItems.TRANSCENDENCE_PERMIT.get());
        basicItem(ModItems.SOURCE_FLOW.get());
        // 终末之诗 (poem_of_the_end)：装了 GeckoLib 用 3D 模型渲染，
        // 物品模型为手写的 builtin/entity + display（见 src/main/resources/assets/herobrine_companion/models/item/poem_of_the_end.json），
        // 因此这里不再用 datagen 生成（避免覆盖手写模型）。
        // poem_of_the_end_base 是没装 GeckoLib 时的 2D 回退模型，由
        // PoemOfTheEndModelSwapper 在烘焙阶段替换进来（仍在使用，别删）。
        // withExistingParent(ModItems.POEM_OF_THE_END.getId().getPath(),
        //         ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "item/poem_of_the_end_base"));

        // Lore System Items
        basicItem(ModItems.LORE_HANDBOOK.get());
        basicItem(ModItems.LORE_FRAGMENT.get());

        // Spawn Eggs
        withExistingParent(ModItems.GHOST_CREEPER_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_ZOMBIE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_SKELETON_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_STEVE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.DESTRUCTION_GOD_HEROBRINE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));

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
        // withExistingParent(ModItems.END_RING_PORTAL_ITEM.getId().getPath(), modLoc("block/end_ring_portal"));

        // Option 2: If we want a flat item, we need the texture.
        // Since I can't create the texture file, I will comment this out to prevent the crash.
        // You should add the texture or uncomment this when the texture exists.

        // basicItem(ModItems.END_RING_PORTAL_ITEM.get());
    }
}
