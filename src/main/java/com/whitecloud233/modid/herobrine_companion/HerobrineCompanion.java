package com.whitecloud233.modid.herobrine_companion;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.whitecloud233.modid.herobrine_companion.block.EndRingPortalBlock;
import com.whitecloud233.modid.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import com.whitecloud233.modid.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.modid.herobrine_companion.compat.KubeJS.HerobrineCompanionKubeJSPlugin;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.config.ConfigScreen;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.*;
import com.whitecloud233.modid.herobrine_companion.loot.AddItemModifier;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.world.inventory.ModMenus;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructurePieces;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler; // 【修改点1】导入正确的Handler
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(HerobrineCompanion.MODID)
public class HerobrineCompanion {
    public static IrisPatcher PATCHER_INSTANCE;
    public static final String MODID = "herobrine_companion";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final RegistryObject<HeroSummonItem> HERO_SHELTER = ITEMS.register("hero_shelter", () -> new HeroSummonItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<EternalKeyItem> ETERNAL_KEY = ITEMS.register("eternal_key", () -> new EternalKeyItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<AbyssalGazeItem> ABYSSAL_GAZE = ITEMS.register("abyssal_gaze", () -> new AbyssalGazeItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> UNSTABLE_GUNPOWDER = ITEMS.register("unstable_gunpowder", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_CODE = ITEMS.register("corrupted_code", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VOID_MARROW = ITEMS.register("void_marrow", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GLITCH_FRAGMENT = ITEMS.register("glitch_fragment", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> SOURCE_CODE_FRAGMENT = ITEMS.register("source_code_fragment", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<MemoryShardItem> MEMORY_SHARD = ITEMS.register("memory_shard", () -> new MemoryShardItem(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<RecallStoneItem> RECALL_STONE = ITEMS.register("recall_stone", () -> new RecallStoneItem(new Item.Properties().stacksTo(1).durability(3).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<SoulBoundPactItem> SOUL_BOUND_PACT = ITEMS.register("soul_bound_pact", () -> new SoulBoundPactItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<TranscendencePermitItem> TRANSCENDENCE_PERMIT = ITEMS.register("transcendence_permit", () -> new TranscendencePermitItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<PoemOfTheEndItem> POEM_OF_THE_END = ITEMS.register("poem_of_the_end", () -> new PoemOfTheEndItem(ModToolTiers.END, 5.0F, -2.8F, new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<LoreFragmentItem> LORE_FRAGMENT = ITEMS.register("lore_fragment", () -> new LoreFragmentItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<LoreHandbookItem> LORE_HANDBOOK = ITEMS.register("lore_handbook", () -> new LoreHandbookItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> TAB_ICON = ITEMS.register("tab_icon", () -> new Item(new Item.Properties()));

    public static final RegistryObject<EndRingPortalBlock> END_RING_PORTAL = BLOCKS.register("end_ring_portal", () -> new EndRingPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission().strength(-1.0F, 3600000.8F).noLootTable()));
    public static final RegistryObject<BlockItem> END_RING_PORTAL_ITEM = ITEMS.register("end_ring_portal", () -> new BlockItem(END_RING_PORTAL.get(), new Item.Properties()));

    public static final RegistryObject<Item> GHOST_CREEPER_SPAWN_EGG = ITEMS.register("ghost_creeper_spawn_egg", () -> new ForgeSpawnEggItem(ModEvents.GHOST_CREEPER, 0x0DA70B, 0x000000, new Item.Properties()));
    public static final RegistryObject<Item> GHOST_ZOMBIE_SPAWN_EGG = ITEMS.register("ghost_zombie_spawn_egg", () -> new ForgeSpawnEggItem(ModEvents.GHOST_ZOMBIE, 0x00AFAF, 0x799C65, new Item.Properties()));
    public static final RegistryObject<Item> GHOST_SKELETON_SPAWN_EGG = ITEMS.register("ghost_skeleton_spawn_egg", () -> new ForgeSpawnEggItem(ModEvents.GHOST_SKELETON, 0xC1C1C1, 0x494949, new Item.Properties()));
    public static final RegistryObject<Item> GHOST_STEVE_SPAWN_EGG = ITEMS.register("ghost_steve_spawn_egg", () -> new ForgeSpawnEggItem(ModEvents.GHOST_STEVE, 0xB07C62, 0x3B3F8E, new Item.Properties()));

    public static final RegistryObject<BlockEntityType<EndRingPortalBlockEntity>> END_RING_PORTAL_BE = BLOCK_ENTITY_TYPES.register("end_ring_portal", () -> BlockEntityType.Builder.of(EndRingPortalBlockEntity::new, END_RING_PORTAL.get()).build(null));

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);

    @SuppressWarnings("unused")
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.herobrine_companion")).withTabsBefore(CreativeModeTabs.SPAWN_EGGS).icon(() -> TAB_ICON.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(HERO_SHELTER.get());
        output.accept(ETERNAL_KEY.get());
        output.accept(ABYSSAL_GAZE.get());
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
        output.accept(SOUL_BOUND_PACT.get());
        output.accept(TRANSCENDENCE_PERMIT.get());
        output.accept(POEM_OF_THE_END.get());

        for (int i = 1; i <= 11; i++) {
            ItemStack fragment = new ItemStack(LORE_FRAGMENT.get());
            CompoundTag tag = new CompoundTag();
            tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_" + i);
            fragment.setTag(tag);
            output.accept(fragment);
        }

        output.accept(LORE_HANDBOOK.get());
    }).build());

    public HerobrineCompanion() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            PATCHER_INSTANCE = new IrisPatcher();
            MinecraftForge.EVENT_BUS.register(IrisPatcher.class);
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

        MinecraftForge.EVENT_BUS.register(this);

        LLMConfig.load();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 【修改点2】使用 ConfigScreenHandler 注册配置界面
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> new ConfigScreen(screen)
                )
        );

        // KubeJS Soft Dependency
        try {
            Class.forName("dev.latvian.mods.kubejs.plugin.KubeJSPlugin");
            MinecraftForge.EVENT_BUS.register(HerobrineCompanionKubeJSPlugin.class);
        } catch (ClassNotFoundException e) {
            LOGGER.info("KubeJS not found, skipping KubeJS integration.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(() -> {
            PacketHandler.register();
        });

        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info(">>> AWESOME CLIENT SETUP TRIGGERED <<<");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}