package com.whitecloud233.modid.herobrine_companion.init;

import com.mojang.serialization.Codec;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.loot.AddItemModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HerobrineCompanion.MODID);

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);
}
