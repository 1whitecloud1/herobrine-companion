package com.whitecloud233.modid.herobrine_companion.client;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroContractScreen;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroTradeScreen;

import com.whitecloud233.modid.herobrine_companion.client.model.HeroDragonModel;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.client.render.*;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.modid.herobrine_companion.world.inventory.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.error(">>> [CLIENT EVENTS] 类加载确认！ <<<");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        LOGGER.error(">>> [RENDERER REGISTER] 正在注册渲染器... <<<");

        event.registerEntityRenderer(ModEvents.HERO.get(), HeroRenderer::new);
        event.registerBlockEntityRenderer(HerobrineCompanion.END_RING_PORTAL_BE.get(), EndRingPortalRenderer::new);

        event.registerEntityRenderer(ModEvents.GHOST_CREEPER.get(), GhostCreeperRenderer::new);
        event.registerEntityRenderer(ModEvents.GHOST_ZOMBIE.get(), GhostZombieRenderer::new);
        event.registerEntityRenderer(ModEvents.GHOST_SKELETON.get(), GhostSkeletonRenderer::new);
        event.registerEntityRenderer(ModEvents.GHOST_STEVE.get(), GhostSteveRenderer::new);
        event.registerEntityRenderer(ModEvents.GLITCH_ECHO.get(), GlitchEchoRenderer::new);
        event.registerEntityRenderer(ModEvents.REALM_BREAKER_LIGHTNING.get(), RealmBreakerLightningRenderer::new);
        event.registerEntityRenderer(ModEvents.VOID_RIFT.get(), VoidRiftRenderer::new);
        event.registerEntityRenderer(ModEvents.GLITCH_VILLAGER.get(), GlitchVillagerRenderer::new);

        // 尝试正常注册
        event.registerEntityRenderer(EntityType.ENDER_DRAGON, DragonRendererWrapper::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HeroModel.LAYER_LOCATION, () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false), 64, 64));

        // 注册自定义龙模型 Layer
        event.registerLayerDefinition(HeroDragonModel.LAYER_LOCATION, HeroDragonModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.HERO_CONTRACT_MENU.get(), HeroContractScreen::new);
            MenuScreens.register(ModMenus.HERO_TRADE_MENU.get(), HeroTradeScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        // [Fix] 1.20.1 中 ResourceLocation 没有 fromNamespaceAndPath 方法，使用构造函数
        event.register(new ResourceLocation(HerobrineCompanion.MODID, "end_ring_type"), new EndRingDimensionEffects());
    }

    private static Method startAttackMethod;

    @Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            
            Minecraft mc = Minecraft.getInstance();

            // === 实时检查并注入渲染器 ===
            if (mc.level != null) {
                if (mc.player != null && mc.player.tickCount % 20 == 0) {
                    injectCustomRenderer(mc);
                }
            }
            // ===========================

            if (mc.player != null && mc.level != null && !mc.isPaused()) {
                ItemStack stack = mc.player.getMainHandItem();
                if (stack.getItem() instanceof PoemOfTheEndItem poemItem) {
                    if (poemItem.getMode(stack) == PoemOfTheEndItem.MODE_VOID_SHATTER) {
                        if (mc.options.keyAttack.isDown()) {
                            try {
                                if (startAttackMethod == null) {
                                    try {
                                        startAttackMethod = Minecraft.class.getDeclaredMethod("startAttack");
                                    } catch (NoSuchMethodException e) {
                                        // ignore
                                    }
                                    if (startAttackMethod != null) {
                                        startAttackMethod.setAccessible(true);
                                    }
                                }
                                if (startAttackMethod != null) {
                                    boolean attackSuccess = (boolean) startAttackMethod.invoke(mc);
                                    if (attackSuccess) {
                                        mc.player.swing(InteractionHand.MAIN_HAND);
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }

    // 反射暴力替换渲染器
    private static void injectCustomRenderer(Minecraft mc) {
        try {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

            Field renderersField = null;
            Field providersField = null;

            for (Field field : EntityRenderDispatcher.class.getDeclaredFields()) {
                if (field.getType() == Map.class) {
                    field.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) field.get(dispatcher);

                    if (map != null && map.containsKey(EntityType.PIG)) {
                        Object value = map.get(EntityType.PIG);
                        if (value instanceof net.minecraft.client.renderer.entity.EntityRenderer) {
                            renderersField = field;
                        } else if (value instanceof EntityRendererProvider) {
                            providersField = field;
                        }
                    }
                }
            }

            if (renderersField != null) {
                Map<EntityType<?>, net.minecraft.client.renderer.entity.EntityRenderer<?>> oldRenderers =
                        (Map<EntityType<?>, net.minecraft.client.renderer.entity.EntityRenderer<?>>) renderersField.get(dispatcher);

                // 检查是否已经注入
                net.minecraft.client.renderer.entity.EntityRenderer<?> current = oldRenderers.get(EntityType.ENDER_DRAGON);
                if (current instanceof DragonRendererWrapper) {
                    return; // 已经是我们的了
                }

                LOGGER.error(">>> [RUNTIME INJECTION] 检测到渲染器丢失或未注入，正在执行注入... <<<");

                // 创建新的可变 Map
                Map<EntityType<?>, net.minecraft.client.renderer.entity.EntityRenderer<?>> newRenderers =
                        new java.util.HashMap<>(oldRenderers);

                // 在新 Map 里替换
                newRenderers.put(EntityType.ENDER_DRAGON, new DragonRendererWrapper(new EntityRendererProvider.Context(
                        dispatcher, mc.getItemRenderer(), mc.getBlockRenderer(), mc.getEntityRenderDispatcher().getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font)));

                // 把新 Map 赋值回字段
                renderersField.set(dispatcher, newRenderers);

                LOGGER.error(">>> [RUNTIME INJECTION] 成功替换了 renderers 实例！(通过新建 Map) <<<");
            }

            if (providersField != null) {
                Map<EntityType<?>, EntityRendererProvider<?>> oldProviders =
                        (Map<EntityType<?>, EntityRendererProvider<?>>) providersField.get(dispatcher);

                // 创建新的可变 Map
                Map<EntityType<?>, EntityRendererProvider<?>> newProviders =
                        new java.util.HashMap<>(oldProviders);

                // 在新 Map 里替换
                newProviders.put(EntityType.ENDER_DRAGON, (EntityRendererProvider) (context) -> new DragonRendererWrapper(context));

                // 把新 Map 赋值回字段
                providersField.set(dispatcher, newProviders);

                LOGGER.error(">>> [RUNTIME INJECTION] 成功替换了 providers 工厂！(通过新建 Map) <<<");
            }

        } catch (Exception e) {
            LOGGER.error(">>> [RUNTIME INJECTION] 失败: ", e);
        }
    }
}