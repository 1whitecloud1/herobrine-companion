package com.whitecloud233.herobrine_companion.datagen;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.function.Consumer;

public class ModAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {
        // Root advancement: Unlocked when the player sees Herobrine (looks at him).
        /*
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(
                        HerobrineCompanion.HERO_SHELTER.get(),
                        Component.translatable("advancement.herobrine_companion.root.title"),
                        Component.translatable("advancement.herobrine_companion.root.desc"),
                        ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png"),
                        AdvancementType.TASK,
                        true, // showToast
                        true, // announceChat
                        false  // hidden
                )
                .addCriterion("meet_herobrine", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .save(saver, HerobrineCompanion.MODID + ":root");
        */

        // Branch 1: Lore Handbook
        /*
        AdvancementHolder loreHandbook = Advancement.Builder.advancement()
                .parent(root)
                .display(
                        HerobrineCompanion.LORE_HANDBOOK.get(),
                        Component.translatable("advancement.herobrine_companion.lore_handbook.title"),
                        Component.translatable("advancement.herobrine_companion.lore_handbook.desc"),
                        null,
                        AdvancementType.TASK,
                        true,
                        true,
                        false
                )
                .addCriterion("has_lore_handbook", InventoryChangeTrigger.TriggerInstance.hasItems(HerobrineCompanion.LORE_HANDBOOK.get()))
                .save(saver, HerobrineCompanion.MODID + ":lore_handbook");

        // Sub-branches for Lore Fragments (1-11)
        for (int i = 1; i <= 11; i++) {
            String fragmentId = "fragment_" + i;
            
            AdvancementHolder fragmentAdvancement = Advancement.Builder.advancement()
                    .parent(loreHandbook)
                    .display(
                            HerobrineCompanion.LORE_FRAGMENT.get(),
                            Component.translatable("advancement.herobrine_companion.fragment_" + i + ".title"),
                            Component.translatable("advancement.herobrine_companion.fragment_" + i + ".desc"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false
                    )
                    .addCriterion("has_fragment_" + i, CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                    .save(saver, HerobrineCompanion.MODID + ":fragment_" + i);
        }
        */

        // Branch 2: Eternal Key (Placeholder for now as user focused on the first branch)
        /*
        AdvancementHolder eternalKey = Advancement.Builder.advancement()
                .parent(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "root"))
                .display(
                        HerobrineCompanion.ETERNAL_KEY.get(),
                        Component.translatable("advancement.herobrine_companion.eternal_key.title"),
                        Component.translatable("advancement.herobrine_companion.eternal_key.desc"),
                        null,
                        AdvancementType.GOAL,
                        true,
                        true,
                        false
                )
                .addCriterion("has_eternal_key", InventoryChangeTrigger.TriggerInstance.hasItems(HerobrineCompanion.ETERNAL_KEY.get()))
                .save(saver, HerobrineCompanion.MODID + ":eternal_key");

        Advancement.Builder.advancement()
                .parent(eternalKey)
                .display(
                        HerobrineCompanion.END_RING_PORTAL_ITEM.get(),
                        Component.translatable("advancement.herobrine_companion.enter_end_ring.title"),
                        Component.translatable("advancement.herobrine_companion.enter_end_ring.desc"),
                        null,
                        AdvancementType.CHALLENGE,
                        true,
                        true,
                        false
                )
                .addCriterion("entered_end_ring", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModStructures.END_RING_DIMENSION_KEY))
                .save(saver, HerobrineCompanion.MODID + ":enter_end_ring");
        */

        // Branch 3: Soul Bound Pact (Placeholder for now)
        /*
        AdvancementHolder soulBoundPact = Advancement.Builder.advancement()
                .parent(root)
                .display(
                        HerobrineCompanion.SOUL_BOUND_PACT.get(),
                        Component.translatable("advancement.herobrine_companion.soul_bound_pact.title"),
                        Component.translatable("advancement.herobrine_companion.soul_bound_pact.desc"),
                        null,
                        AdvancementType.GOAL,
                        true,
                        true,
                        false
                )
                .addCriterion("has_soul_bound_pact", InventoryChangeTrigger.TriggerInstance.hasItems(HerobrineCompanion.SOUL_BOUND_PACT.get()))
                .save(saver, HerobrineCompanion.MODID + ":soul_bound_pact");
        */
    }
}