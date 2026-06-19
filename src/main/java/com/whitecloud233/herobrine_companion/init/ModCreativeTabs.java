package com.whitecloud233.herobrine_companion.init;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HerobrineCompanion.MODID);

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", 
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.herobrine_companion"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.TAB_ICON.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.HERO_SHELTER.get());
                        output.accept(ModItems.ETERNAL_KEY.get());
                        //output.accept(ModItems.END_RING_PORTAL_ITEM.get());
                        output.accept(ModItems.GHOST_CREEPER_SPAWN_EGG.get());
                        output.accept(ModItems.GHOST_ZOMBIE_SPAWN_EGG.get());
                        output.accept(ModItems.GHOST_SKELETON_SPAWN_EGG.get());
                        output.accept(ModItems.GHOST_STEVE_SPAWN_EGG.get());
                        output.accept(ModItems.UNSTABLE_GUNPOWDER.get());
                        output.accept(ModItems.CORRUPTED_CODE.get());
                        output.accept(ModItems.VOID_MARROW.get());
                        output.accept(ModItems.GLITCH_FRAGMENT.get());
                        output.accept(ModItems.SOURCE_CODE_FRAGMENT.get());
                        output.accept(ModItems.MEMORY_SHARD.get());
                        output.accept(ModItems.RECALL_STONE.get());
                        output.accept(ModItems.ABYSSAL_GAZE.get());
                        output.accept(ModItems.AWAKENED_VESSEL.get());
                        output.accept(ModItems.SOUL_BOUND_PACT.get());
                        output.accept(ModItems.TRANSCENDENCE_PERMIT.get());
                        output.accept(ModItems.POEM_OF_THE_END.get());
                        output.accept(ModItems.SOURCE_FLOW.get());
                        output.accept(ModItems.LORE_HANDBOOK.get());
                        output.accept(ModItems.DESTRUCTION_GOD_HEROBRINE_SPAWN_EGG.get());

                        for (int i = 1; i <= 11; i++) {
                            ItemStack stack = new ItemStack(ModItems.LORE_FRAGMENT.get());
                            CompoundTag tag = new CompoundTag();
                            tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_" + i);
                            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                            output.accept(stack);
                        }
                    }).build());
}
