package com.whitecloud233.herobrine_companion;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.event.ClientModSetup;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KaleidoscopeCompatBuiltinPackFallback;
import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.datagen.DataGenerators;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.event.WorldAnomalyEventHandler;
import com.whitecloud233.herobrine_companion.init.*;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.world.inventory.ModMenus;
import com.whitecloud233.herobrine_companion.world.structure.ModStructurePieces;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(HerobrineCompanion.MODID)
public class HerobrineCompanion {
    public static final String MODID = "herobrine_companion";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HerobrineCompanion(IEventBus modEventBus, ModContainer modContainer) {

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(Config::onLoad);
        HeroEpicFightCompat.bootstrap(modEventBus);
        modEventBus.addListener(KaleidoscopeCompatBuiltinPackFallback::onAddPackFinders);

        modEventBus.addListener(ModEvents::entityAttributeEvent);
        modEventBus.addListener(ModEvents::registerSpawnPlacements);
        modEventBus.addListener(DataGenerators::gatherData);

        if (FMLEnvironment.dist == Dist.CLIENT) {
             ClientModSetup.init(modEventBus);
        }

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModLootModifiers.GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus); 
        
        ModEntities.ENTITY_TYPES.register(modEventBus);
        
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructurePieces.STRUCTURE_PIECES.register(modEventBus);
        
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(WorldAnomalyEventHandler.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        // Register KubeJS plugin if present
        try {
            Class.forName("dev.latvian.mods.kubejs.plugin.KubeJSPlugin");
            NeoForge.EVENT_BUS.register(com.whitecloud233.herobrine_companion.compat.kubejs.HerobrineCompanionKubeJSPlugin.class);
        } catch (ClassNotFoundException e) {
            // KubeJS not present
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        event.enqueueWork(() -> {
            HeroEpicFightCompat.tryRegisterRuntimeBridge();
        });
        if (com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat.isLoaded()) {
            LOGGER.info(">>> [DEBUG] 检测到 Armourer's Workshop 已加载，准备对接渲染系统 <<<");
        } else {
            LOGGER.warn(">>> [DEBUG] 未检测到 Armourer's Workshop，时装功能将不可用 <<<");
        }
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
