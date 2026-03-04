package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.loot.AddItemModifier;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

import java.util.Optional;

public class ModGlobalLootModifiersProvider extends GlobalLootModifierProvider {
    public ModGlobalLootModifiersProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID);
    }

    @Override
    protected void start() {
        // 注册 永恒之匙 (远古城市) - 30% 概率
        add("add_eternal_key_ancient_city", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/ancient_city")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 添加 50% 概率
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                Optional.empty()
        ));

        // 注册 永恒之匙 (末地城) - 50% 概率
        add("add_eternal_key_end_city", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/end_city_treasure")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 添加 50% 概率
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                Optional.empty()
        ));

        // 注册 永恒之匙 (要塞图书馆) - 50% 概率
        add("add_eternal_key_stronghold", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/stronghold_library")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 添加 50% 概率
                },
                HerobrineCompanion.ETERNAL_KEY.get(),
                Optional.empty()
        ));

        // 注册 末地城 碎片 8 - 50% 概率
        add("fragment_8_end_city", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/end_city_treasure")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 1.0f 改为 0.5f
                },
                HerobrineCompanion.LORE_FRAGMENT.get(),
                Optional.of("{lore_id:\"fragment_8\"}")
        ));

        // 注册 远古城市 碎片 10 - 50% 概率
        add("fragment_10_ancient_city", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/ancient_city")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 1.0f 改为 0.5f
                },
                HerobrineCompanion.LORE_FRAGMENT.get(),
                Optional.of("{lore_id:\"fragment_10\"}")
        ));

        // 注册 废弃矿井 碎片 2 - 50% 概率
        add("mineshaft_fragment_2", new AddItemModifier(
                new LootItemCondition[] {
                        LootTableIdCondition.builder(new ResourceLocation("minecraft:chests/abandoned_mineshaft")).build(),
                        LootItemRandomChanceCondition.randomChance(0.3f).build() // 1.0f 改为 0.5f
                },
                HerobrineCompanion.LORE_FRAGMENT.get(),
                Optional.of("{lore_id:\"fragment_2\"}")
        ));
    }
}