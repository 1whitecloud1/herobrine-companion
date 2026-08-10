package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
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

    @SuppressWarnings("removal")
    @Override
    protected void registerModels() {
        simpleItem(ModItems.SOURCE_CODE_FRAGMENT);
        simpleItem(ModItems.HERO_SHELTER);
        simpleItem(ModItems.ETERNAL_KEY);
        simpleItem(ModItems.ABYSSAL_GAZE);
        simpleItem(ModItems.UNSTABLE_GUNPOWDER);
        simpleItem(ModItems.CORRUPTED_CODE);
        simpleItem(ModItems.VOID_MARROW);
        simpleItem(ModItems.GLITCH_FRAGMENT);
        simpleItem(ModItems.MEMORY_SHARD);
        simpleItem(ModItems.RECALL_STONE);
        simpleItem(ModItems.AWAKENED_VESSEL);
        simpleItem(ModItems.SOUL_BOUND_PACT);
        simpleItem(ModItems.TRANSCENDENCE_PERMIT);
        // 终末之诗：模型为手写的自定义 Bedrock 几何加载器（见 src/main/resources/models/item/poem_of_the_end.json），
        // 不再用 datagen 生成，避免覆盖手写 loader 字段。
        simpleItem(ModItems.LORE_FRAGMENT);
        simpleItem(ModItems.LORE_HANDBOOK);
        simpleItem(ModItems.TAB_ICON);

        withExistingParent(ModItems.GHOST_CREEPER_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_ZOMBIE_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_SKELETON_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(ModItems.GHOST_STEVE_SPAWN_EGG.getId().getPath(), new ResourceLocation("item/template_spawn_egg"));
    }

    @SuppressWarnings("removal")
    private ItemModelBuilder simpleItem(RegistryObject<? extends Item> item) {
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(HerobrineCompanion.MODID, "item/" + item.getId().getPath()));
    }
}
