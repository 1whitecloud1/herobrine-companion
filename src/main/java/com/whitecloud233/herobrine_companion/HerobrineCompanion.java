package com.whitecloud233.herobrine_companion;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.whitecloud233.herobrine_companion.block.EndRingPortalBlock;
import com.whitecloud233.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import com.whitecloud233.herobrine_companion.config.ConfigScreen;
import com.whitecloud233.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.datagen.DataGenerators;
import com.whitecloud233.herobrine_companion.entity.ai.LLMConfig;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.item.*;
import com.whitecloud233.herobrine_companion.loot.AddItemModifier;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.world.inventory.ModMenus;
import com.whitecloud233.herobrine_companion.world.structure.ModStructurePieces;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(HerobrineCompanion.MODID)
public class HerobrineCompanion {
    public static IrisPatcher PATCHER_INSTANCE;
    public static final String MODID = "herobrine_companion";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final DeferredItem<Item> TAB_ICON = ITEMS.register("tab_icon", () -> new Item(new Item.Properties()));
    public static final DeferredItem<HeroSummonItem> HERO_SHELTER = ITEMS.register("hero_shelter", () -> new HeroSummonItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<EternalKeyItem> ETERNAL_KEY = ITEMS.register("eternal_key", () -> new EternalKeyItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final DeferredItem<Item> UNSTABLE_GUNPOWDER = ITEMS.register("unstable_gunpowder", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CORRUPTED_CODE = ITEMS.register("corrupted_code", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> VOID_MARROW = ITEMS.register("void_marrow", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GLITCH_FRAGMENT = ITEMS.register("glitch_fragment", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final DeferredItem<Item> SOURCE_CODE_FRAGMENT = ITEMS.register("source_code_fragment", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    
    // New Items
    public static final DeferredItem<MemoryShardItem> MEMORY_SHARD = ITEMS.register("memory_shard", () -> new MemoryShardItem(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final DeferredItem<RecallStoneItem> RECALL_STONE = ITEMS.register("recall_stone", () -> new RecallStoneItem(new Item.Properties().stacksTo(1).durability(3).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<AbyssalGazeItem> ABYSSAL_GAZE = ITEMS.register("abyssal_gaze", () -> new AbyssalGazeItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<SoulBoundPactItem> SOUL_BOUND_PACT = ITEMS.register("soul_bound_pact", () -> new SoulBoundPactItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<TranscendencePermitItem> TRANSCENDENCE_PERMIT = ITEMS.register("transcendence_permit", () -> new TranscendencePermitItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<PoemOfTheEndItem> POEM_OF_THE_END = ITEMS.register("poem_of_the_end", () -> new PoemOfTheEndItem(Tiers.NETHERITE, 5.0F, -3.0F, new Item.Properties().durability(2031).rarity(net.minecraft.world.item.Rarity.EPIC)));
    
    // Lore Items
    public static final DeferredItem<LoreHandbookItem> LORE_HANDBOOK = ITEMS.register("lore_handbook", () -> new LoreHandbookItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final DeferredItem<LoreFragmentItem> LORE_FRAGMENT = ITEMS.register("lore_fragment", () -> new LoreFragmentItem(new Item.Properties().stacksTo(64).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    public static final DeferredHolder<Block, EndRingPortalBlock> END_RING_PORTAL = BLOCKS.register("end_ring_portal", () -> new EndRingPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission().strength(-1.0F, 3600000.8F).noLootTable()));
    public static final DeferredItem<BlockItem> END_RING_PORTAL_ITEM = ITEMS.register("end_ring_portal", () -> new BlockItem(END_RING_PORTAL.get(), new Item.Properties()));

    public static final DeferredItem<Item> GHOST_CREEPER_SPAWN_EGG = ITEMS.register("ghost_creeper_spawn_egg", () -> new DeferredSpawnEggItem(ModEvents.GHOST_CREEPER, 0x0DA70B, 0x000000, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_ZOMBIE_SPAWN_EGG = ITEMS.register("ghost_zombie_spawn_egg", () -> new DeferredSpawnEggItem(ModEvents.GHOST_ZOMBIE, 0x00AFAF, 0x799C65, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_SKELETON_SPAWN_EGG = ITEMS.register("ghost_skeleton_spawn_egg", () -> new DeferredSpawnEggItem(ModEvents.GHOST_SKELETON, 0xC1C1C1, 0x494949, new Item.Properties()));
    public static final DeferredItem<Item> GHOST_STEVE_SPAWN_EGG = ITEMS.register("ghost_steve_spawn_egg", () -> new DeferredSpawnEggItem(ModEvents.GHOST_STEVE, 0xB07C62, 0x3B3F8E, new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndRingPortalBlockEntity>> END_RING_PORTAL_BE = BLOCK_ENTITY_TYPES.register("end_ring_portal", () -> BlockEntityType.Builder.of(EndRingPortalBlockEntity::new, END_RING_PORTAL.get()).build(null));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemModifier>> ADD_ITEM = GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.herobrine_companion")).withTabsBefore(CreativeModeTabs.SPAWN_EGGS).icon(() -> TAB_ICON.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(HERO_SHELTER.get());
        output.accept(ETERNAL_KEY.get());
        //output.accept(END_RING_PORTAL_ITEM.get());
        output.accept(GHOST_CREEPER_SPAWN_EGG.get());
        output.accept(GHOST_ZOMBIE_SPAWN_EGG.get());
        output.accept(GHOST_SKELETON_SPAWN_EGG.get());
        output.accept(GHOST_STEVE_SPAWN_EGG.get());
        output.accept(UNSTABLE_GUNPOWDER.get());
        output.accept(CORRUPTED_CODE.get());
        output.accept(VOID_MARROW.get());
        output.accept(GLITCH_FRAGMENT.get());
        output.accept(SOURCE_CODE_FRAGMENT.get());
        output.accept(MEMORY_SHARD.get());
        output.accept(RECALL_STONE.get());
        output.accept(ABYSSAL_GAZE.get());
        output.accept(SOUL_BOUND_PACT.get());
        output.accept(TRANSCENDENCE_PERMIT.get());
        output.accept(POEM_OF_THE_END.get());
        output.accept(LORE_HANDBOOK.get());
        
        for (int i = 1; i <= 11; i++) {
            ItemStack stack = new ItemStack(LORE_FRAGMENT.get());
            CompoundTag tag = new CompoundTag();
            tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_" + i);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            output.accept(stack);
        }
    }).build());

    public HerobrineCompanion(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        modEventBus.addListener(ModEvents::entityAttributeEvent);
        modEventBus.addListener(ModEvents::registerSpawnPlacements);
        modEventBus.addListener(DataGenerators::gatherData);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            
            PATCHER_INSTANCE = new IrisPatcher();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(PATCHER_INSTANCE);
            
            // Register Config Screen
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigScreen.FACTORY);
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus); 
        
        ModEvents.ENTITY_TYPES.register(modEventBus);
        
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructurePieces.STRUCTURE_PIECES.register(modEventBus);
        
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        LLMConfig.load();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        try {
            var resources = this.getClass().getClassLoader().getResources("META-INF/services/dev.latvian.mods.kubejs.plugin.KubeJSPlugin");
            while (resources.hasMoreElements()) {
                LOGGER.info("Found KubeJS Plugin service: {}", resources.nextElement());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to list KubeJS Plugin services", e);
        }
    }
    
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info(">>> AWESOME CLIENT SETUP TRIGGERED <<<");
    }
    
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        PacketHandler.register(registrar);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}