package com.whitecloud233.modid.herobrine_companion;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.client.event.ClientModSetup;
import com.whitecloud233.modid.herobrine_companion.util.DebugLog;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KaleidoscopeCompatBuiltinPackFallback;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.init.*;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.world.inventory.ModMenus;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructurePieces;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(HerobrineCompanion.MODID)
public class HerobrineCompanion {
    public static Object PATCHER_INSTANCE;
    public static final String MODID = "herobrine_companion";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HerobrineCompanion() {
        IEventBus modEventBus = resolveModEventBus();

        com.whitecloud233.modid.herobrine_companion.compat.cooking.CookingCompatBootstrap.registerBackends();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(KaleidoscopeCompatBuiltinPackFallback::onAddPackFinders);

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

        MinecraftForge.EVENT_BUS.register(this);

        LLMConfig.load();

        registerCommonConfig();
        com.whitecloud233.modid.herobrine_companion.compat.safety.CompatBootstrap.register(modEventBus);


    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        DebugLog.info(LOGGER, "HELLO FROM COMMON SETUP");
        event.enqueueWork(() -> {
            PacketHandler.register();
            com.whitecloud233.modid.herobrine_companion.network.ConfigSyncChannel.register();
            com.whitecloud233.modid.herobrine_companion.network.TemplateSyncChannel.register();
            // 可选能力通道（T22）：旧端不协商就整条不启用，主通道与既有工具行为不变。
            com.whitecloud233.modid.herobrine_companion.network.AgentFeatureChannel.register();
            com.whitecloud233.modid.herobrine_companion.network.CompanionGuideChannel.register();
            com.whitecloud233.modid.herobrine_companion.network.HeroContentChannel.register();
        });

        DebugLog.info(LOGGER, "DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        DebugLog.info(LOGGER, "HELLO from server starting");
    }

    private static IEventBus resolveModEventBus() {
        try {
            Object context = FMLJavaModLoadingContext.class.getMethod("get").invoke(null);
            return (IEventBus) context.getClass().getMethod("getModEventBus").invoke(context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve Forge mod event bus", e);
        }
    }

    private static void registerCommonConfig() {
        try {
            Object context = ModLoadingContext.class.getMethod("get").invoke(null);
            context.getClass().getMethod("registerConfig", ModConfig.Type.class, net.minecraftforge.fml.config.IConfigSpec.class)
                    .invoke(context, ModConfig.Type.COMMON, Config.SPEC);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register common config", e);
        }
    }
}
