package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetCustomDataFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.stream.Stream;

public class ModEntityLootTables extends EntityLootSubProvider {
    public ModEntityLootTables(HolderLookup.Provider provider) {
        super(FeatureFlags.REGISTRY.allFlags(), provider);
    }

    @Override
    public void generate() {
        // Hero Entity - No drops for now (or add special drops if desired)
        add(ModEvents.HERO.get(), LootTable.lootTable());

        // 准备 Fragment 5 的 NBT
        CompoundTag fragment5Tag = new CompoundTag();
        fragment5Tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_5");

        add(ModEvents.GHOST_ZOMBIE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.CORRUPTED_CODE.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.registries, UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR) // Empty entry
                                .setWeight(19)))
                // [新增] Fragment 5 掉落池 (5% 几率)
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.LORE_FRAGMENT.get())
                                .apply(SetCustomDataFunction.setCustomData(fragment5Tag))
                                .setWeight(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR)
                                .setWeight(19))));

        add(ModEvents.GHOST_CREEPER.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.UNSTABLE_GUNPOWDER.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.registries, UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR) // Empty entry
                                .setWeight(19)))
                // [新增] Fragment 5 掉落池 (5% 几率)
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.LORE_FRAGMENT.get())
                                .apply(SetCustomDataFunction.setCustomData(fragment5Tag))
                                .setWeight(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR)
                                .setWeight(19))));

        add(ModEvents.GHOST_SKELETON.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.VOID_MARROW.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.registries, UniformGenerator.between(0.0F, 1.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.GLITCH_FRAGMENT.get())
                                .setWeight(1)
                                .setQuality(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR) // Empty entry
                                .setWeight(19)))
                // [新增] Fragment 5 掉落池 (5% 几率)
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.LORE_FRAGMENT.get())
                                .apply(SetCustomDataFunction.setCustomData(fragment5Tag))
                                .setWeight(1))
                        .add(LootItem.lootTableItem(net.minecraft.world.item.Items.AIR)
                                .setWeight(19))));

        // Ghost Steve Loot Table
        add(ModEvents.GHOST_STEVE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 1.0F)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.registries, UniformGenerator.between(0.0F, 1.0F))))));

        // Glitch Echo - No drops, but we don't register it here because it's not a LivingEntity
        // and EntityLootSubProvider validates that all registered entities are LivingEntities or have special handling.
        // Since GlitchEchoEntity extends Entity directly (not LivingEntity), it shouldn't be in this provider
        // if the provider enforces LivingEntity checks, OR we need to filter it out from getKnownEntityTypes.
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        // Filter out GlitchEchoEntity because it's not a LivingEntity and doesn't need a standard loot table
        // Also filter out RealmBreakerLightningEntity and VoidRiftEntity
        return ModEvents.ENTITY_TYPES.getEntries().stream()
                .map(net.neoforged.neoforge.registries.DeferredHolder::get)
                .filter(type -> type != ModEvents.GLITCH_ECHO.get())
                .filter(type -> type != ModEvents.REALM_BREAKER_LIGHTNING.get())
                .filter(type -> type != ModEvents.VOID_RIFT.get())
                .map(type -> (EntityType<?>) type); // Explicit cast to ensure correct generic type
    }
}
