package com.whitecloud233.herobrine_companion.client.event;


import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.render.PoemOfTheEndGeoCompat;
import com.whitecloud233.herobrine_companion.init.ModItems;
import com.whitecloud233.herobrine_companion.client.gui.HeroContractScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroTradeScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroWardrobeScreen;
import com.whitecloud233.herobrine_companion.client.model.GhostSteveModel;
import com.whitecloud233.herobrine_companion.client.model.HeroDragonModel;
import com.whitecloud233.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.herobrine_companion.client.render.*;
import com.whitecloud233.herobrine_companion.client.render.VoidRiftRenderer;
import com.whitecloud233.herobrine_companion.init.*;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.herobrine_companion.init.ModBlockEntities;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import com.whitecloud233.herobrine_companion.world.inventory.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.slf4j.Logger;
import com.whitecloud233.herobrine_companion.destructiongod.client.render.DestructionGodHerobrineRenderer;
import com.whitecloud233.herobrine_companion.destructiongod.client.render.FaultAwareBlockDisplayRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class ClientEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.error(">>> [CLIENT EVENTS] 类加载确认！ <<<");
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        LOGGER.error(">>> [RENDERER REGISTER] 正在注册渲染器... <<<");

        event.registerEntityRenderer(ModEntities.HERO.get(), HeroRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.END_RING_PORTAL_BE.get(), EndRingPortalRenderer::new);
        event.registerEntityRenderer(ModEntities.DESTRUCTION_GOD_HEROBRINE.get(), DestructionGodHerobrineRenderer::new);

        event.registerEntityRenderer(ModEntities.GHOST_CREEPER.get(), GhostCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.BIRTHDAY_CAKE_PROP.get(), BirthdayCakePropRenderer::new);
        event.registerEntityRenderer(ModEntities.GHOST_ZOMBIE.get(), GhostZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.GHOST_SKELETON.get(), GhostSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.GHOST_STEVE.get(), GhostSteveRenderer::new);
        event.registerEntityRenderer(ModEntities.GLITCH_ECHO.get(), GlitchEchoRenderer::new);
        event.registerEntityRenderer(ModEntities.REALM_BREAKER_LIGHTNING.get(), RealmBreakerLightningRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_RIFT.get(), VoidRiftRenderer::new);
        event.registerEntityRenderer(ModEntities.GLITCH_VILLAGER.get(), GlitchVillagerRenderer::new);
        event.registerEntityRenderer(ModEntities.CLEAVE_BLADE.get(), CleaveBladeRenderer::new);
        event.registerEntityRenderer(EntityType.BLOCK_DISPLAY, FaultAwareBlockDisplayRenderer::new);

        // 尝试正常注册
        event.registerEntityRenderer(EntityType.ENDER_DRAGON, DragonRendererWrapper::new);
    }

    public static void onRegisterAdditionalModels(final net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        // 无名之蛋糕摆件(Bedrock birthday_cake_prop):geo 转换出的方块模型。
        // ⚠️ 这里注册的 MRL 必须是 standalone variant(见 BirthdayCakePropRenderer.MODEL 的注释),
        // 否则 register() 会抛 "Side-loaded models must use the 'standalone' variant",
        // 导致整次资源重载被判定失败(游戏清空已选资源包并重载)。
        event.register(com.whitecloud233.herobrine_companion.client.render.BirthdayCakePropRenderer.MODEL);
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // 👇 [修改后]：必须指向我们新写的 HeroModel.createBodyLayer
        event.registerLayerDefinition(HeroModel.LAYER_LOCATION, () -> HeroModel.createBodyLayer(false));

        // 注册自定义龙模型 Layer
        event.registerLayerDefinition(HeroDragonModel.LAYER_LOCATION, HeroDragonModel::createBodyLayer);
        // 👇 [新增代码] 注册 GhostSteve 的纯净独立模型 Layer
        event.registerLayerDefinition(GhostSteveModel.LAYER_LOCATION, GhostSteveModel::createBodyLayer);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HERO_CONTRACT_MENU.get(), HeroContractScreen::new);
        // HeroTradeScreen 不继承 AbstractContainerScreen，因此这里需要与 HeroWardrobeScreen 一样
        // 满足 ScreenConstructor<M, U extends Screen & MenuAccess<M>> 的泛型约束。
        event.register(ModMenus.HERO_TRADE_MENU.get(), HeroTradeScreen::new);
        event.register(ModMenus.HERO_WARDROBE_MENU.get(), HeroWardrobeScreen::new);
    }

    public static void registerDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "end_ring_type"), new EndRingDimensionEffects());
    }

    /**
     * 终末之诗的 3D 渲染器。NeoForge 1.21.1 移除了 {@code Item#initializeClient}，
     * 客户端扩展改由本事件注册。
     *
     * <p>GeckoLib 和 Epic Fight 都没装时 {@code createRenderer()} 才返回 null，
     * 物品模型会被 {@code PoemOfTheEndModelSwapper} 换成 2D 版本。</p>
     */
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        BlockEntityWithoutLevelRenderer poemRenderer = PoemOfTheEndGeoCompat.createRenderer();
        if (poemRenderer == null) {
            return;
        }
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return poemRenderer;
            }
        }, ModItems.POEM_OF_THE_END.get());
    }

    private static Method startAttackMethod;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null && mc.player != null) {
            // 处理「装扮栏里的第三方快捷按钮」排队的动作（等本模组菜单关闭后再打开目标界面）
            com.whitecloud233.herobrine_companion.compat.screens.ExternalScreenShortcuts.tick();

            // === 实时检查并注入渲染器 ===
            if (mc.player.tickCount % 20 == 0) {
                injectCustomRenderer(mc);
            }
        }
        // ===========================

        if (mc.player != null && mc.level != null && !mc.isPaused()
                && mc.screen == null && mc.mouseHandler.isMouseGrabbed() && !mc.player.isUsingItem()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof PoemOfTheEndItem poemItem) {
                if (poemItem.getMode(stack) == PoemOfTheEndItem.MODE_VOID_SHATTER) {
                    if (mc.options.keyAttack.isDown()) {
                        try {
                            if (startAttackMethod == null) {
                                try {
                                    startAttackMethod = Minecraft.class.getDeclaredMethod("startAttack");
                                } catch (NoSuchMethodException e) {
                                    throw e;
                                }
                                startAttackMethod.setAccessible(true);
                            }
                            startAttackMethod.invoke(mc);
                        } catch (Exception e) {
                            // ignore
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