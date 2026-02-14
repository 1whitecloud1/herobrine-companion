package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.loot.AddItemModifier;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

public class ModGlobalLootModifiersProvider extends GlobalLootModifierProvider {
    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID);
    }

    @Override
    protected void start() {
        // 分别注册三个 Modifier 以规避 AlternativeLootItemCondition 找不到的问题
        add("add_eternal_key_ancient_city", new AddItemModifier(
                new LootItemCondition[] {
                    LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/ancient_city")).build()
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                1.0f
        ));
        
        add("add_eternal_key_end_city", new AddItemModifier(
                new LootItemCondition[] {
                    LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/end_city_treasure")).build()
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                1.0f
        ));
        
        add("add_eternal_key_stronghold", new AddItemModifier(
                new LootItemCondition[] {
                    LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/stronghold_library")).build()
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                1.0f
        ));
    }
}
