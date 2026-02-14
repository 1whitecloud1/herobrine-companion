package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, HerobrineCompanion.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        simpleItem(HerobrineCompanion.SOURCE_CODE_FRAGMENT);
        simpleItem(HerobrineCompanion.HERO_SHELTER);
        simpleItem(HerobrineCompanion.ETERNAL_KEY);
        simpleItem(HerobrineCompanion.ABYSSAL_GAZE);
        simpleItem(HerobrineCompanion.UNSTABLE_GUNPOWDER);
        simpleItem(HerobrineCompanion.CORRUPTED_CODE);
        simpleItem(HerobrineCompanion.VOID_MARROW);
        simpleItem(HerobrineCompanion.GLITCH_FRAGMENT);
        simpleItem(HerobrineCompanion.MEMORY_SHARD);
        simpleItem(HerobrineCompanion.RECALL_STONE);
        simpleItem(HerobrineCompanion.SOUL_BOUND_PACT);
        simpleItem(HerobrineCompanion.TRANSCENDENCE_PERMIT);
        simpleItem(HerobrineCompanion.POEM_OF_THE_END);
        simpleItem(HerobrineCompanion.LORE_FRAGMENT);
        simpleItem(HerobrineCompanion.LORE_HANDBOOK);
        simpleItem(HerobrineCompanion.TAB_ICON);

        withExistingParent(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(HerobrineCompanion.GHOST_STEVE_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
    }

    private ItemModelBuilder simpleItem(RegistryObject<? extends Item> item) {
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(HerobrineCompanion.MODID, "item/" + item.getId().getPath()));
    }
}
