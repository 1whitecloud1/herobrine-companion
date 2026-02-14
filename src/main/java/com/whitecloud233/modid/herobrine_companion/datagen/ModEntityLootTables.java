package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootingEnchantFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.RegistryObject;

import java.util.stream.Stream;

public class ModEntityLootTables extends EntityLootSubProvider {
    public ModEntityLootTables() {
        super(FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    public void generate() {
        add(ModEvents.HERO.get(), LootTable.lootTable());

        add(ModEvents.GHOST_ZOMBIE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.CORRUPTED_CODE.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(LootingEnchantFunction.lootingMultiplier(UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR) 
                                .setWeight(19))));

        add(ModEvents.GHOST_CREEPER.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.UNSTABLE_GUNPOWDER.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(LootingEnchantFunction.lootingMultiplier(UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR) 
                                .setWeight(19))));

        add(ModEvents.GHOST_SKELETON.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.VOID_MARROW.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(LootingEnchantFunction.lootingMultiplier(UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR)
                                .setWeight(19))));
        
        add(ModEvents.GHOST_STEVE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 1.0F)))
                                .apply(LootingEnchantFunction.lootingMultiplier(UniformGenerator.between(0.0F, 1.0F))))));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return ModEvents.ENTITY_TYPES.getEntries().stream()
                .map(RegistryObject::get)
                .filter(type -> type != ModEvents.GLITCH_ECHO.get())
                .filter(type -> type != ModEvents.REALM_BREAKER_LIGHTNING.get())
                .filter(type -> type != ModEvents.VOID_RIFT.get())
                .map(type -> (EntityType<?>) type);
    }
}
