package com.whitecloud233.herobrine_companion.init;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HerobrineCompanion.MODID);

    public static final DeferredItem<Item> TAB_ICON = ITEMS.register("tab_icon", () -> new Item(new Item.Properties()));
    public static final DeferredItem<HeroSummonItem> HERO_SHELTER = ITEMS.register("hero_shelter", () -> new HeroSummonItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<EternalKeyItem> ETERNAL_KEY = ITEMS.register("eternal_key", () -> new EternalKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> UNSTABLE_GUNPOWDER = ITEMS.register("unstable_gunpowder", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CORRUPTED_CODE = ITEMS.register("corrupted_code", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> VOID_MARROW = ITEMS.register("void_marrow", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GLITCH_FRAGMENT = ITEMS.register("glitch_fragment", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> SOURCE_CODE_FRAGMENT = ITEMS.register("source_code_fragment", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> DESTRUCTION_GOD_HEROBRINE_SPAWN_EGG = ITEMS.register("destruction_god_herobrine_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.DESTRUCTION_GOD_HEROBRINE, 0x5E4A43, 0xF4F4F4, new Item.Properties().rarity(Rarity.EPIC)));

    // New Items
    public static final DeferredItem<MemoryShardItem> MEMORY_SHARD = ITEMS.register("memory_shard", () -> new MemoryShardItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<RecallStoneItem> RECALL_STONE = ITEMS.register("recall_stone", () -> new RecallStoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<AbyssalGazeItem> ABYSSAL_GAZE = ITEMS.register("abyssal_gaze", () -> new AbyssalGazeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<AwakenedVesselItem> AWAKENED_VESSEL = ITEMS.register("awakened_vessel", () -> new AwakenedVesselItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<SoulBoundPactItem> SOUL_BOUND_PACT = ITEMS.register("soul_bound_pact", () -> new SoulBoundPactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<TranscendencePermitItem> TRANSCENDENCE_PERMIT = ITEMS.register("transcendence_permit", () -> new TranscendencePermitItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<PoemOfTheEndItem> POEM_OF_THE_END = ITEMS.register("poem_of_the_end", () -> new PoemOfTheEndItem(Tiers.NETHERITE, 5.0F, -3.0F, new Item.Properties().durability(2031).rarity(Rarity.EPIC)));
    public static final DeferredItem<SourceFlowItem> SOURCE_FLOW = ITEMS.register("source_flow", () -> new SourceFlowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    
    // Lore Items
    public static final DeferredItem<LoreHandbookItem> LORE_HANDBOOK = ITEMS.register("lore_handbook", () -> new LoreHandbookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<LoreFragmentItem> LORE_FRAGMENT = ITEMS.register("lore_fragment", () -> new LoreFragmentItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<BlockItem> END_RING_PORTAL_ITEM = ITEMS.register("end_ring_portal", () -> new BlockItem(ModBlocks.END_RING_PORTAL.get(), new Item.Properties()));

    public static final DeferredItem<Item> GHOST_CREEPER_SPAWN_EGG = ITEMS.register("ghost_creeper_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GHOST_CREEPER, 0x0DA70B, 0x000000, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_ZOMBIE_SPAWN_EGG = ITEMS.register("ghost_zombie_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GHOST_ZOMBIE, 0x00AFAF, 0x799C65, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_SKELETON_SPAWN_EGG = ITEMS.register("ghost_skeleton_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GHOST_SKELETON, 0xC1C1C1, 0x494949, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_STEVE_SPAWN_EGG = ITEMS.register("ghost_steve_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GHOST_STEVE, 0xB07C62, 0x3B3F8E, new Item.Properties()));
}
