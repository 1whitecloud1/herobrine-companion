package com.whitecloud233.herobrine_companion.client.event;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.herobrine_companion.client.render.PoemOfTheEndModelSwapper;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LocalModelConnector;
import com.whitecloud233.herobrine_companion.BuildFlags;
import com.whitecloud233.herobrine_companion.client.service.LocalModelLauncher;
import com.whitecloud233.herobrine_companion.client.service.ModContentIndex;
import com.whitecloud233.herobrine_companion.config.ConfigScreen;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.StructureIndexRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.util.concurrent.CompletableFuture;

public class ClientModSetup {
    
    public static IrisPatcher PATCHER_INSTANCE;

    public static void init(IEventBus modEventBus) {
        // 1. 注册客户端生命周期事件
        modEventBus.addListener(ClientModSetup::clientSetup);
        modEventBus.addListener(KeyBindingHandler::onKeyRegister);
        modEventBus.addListener(ClientEvents::registerRenderers);
        modEventBus.addListener(ClientEvents::registerLayerDefinitions);
        modEventBus.addListener(ClientEvents::registerScreens);
        modEventBus.addListener(ClientEvents::registerDimensionSpecialEffects);
        modEventBus.addListener(ClientEvents::registerClientExtensions);
        // 终末之诗：没装 GeckoLib 时把物品模型换成 2D 版本
        modEventBus.addListener(PoemOfTheEndModelSwapper::onRegisterAdditional);
        modEventBus.addListener(PoemOfTheEndModelSwapper::onModifyBakingResult);

        // 2. 注册配置界面 (适配 NeoForge 写法)
        ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (minecraft, screen) -> new ConfigScreen(screen)
        );

        // 3. 初始化 Patcher
        PATCHER_INSTANCE = new IrisPatcher();
        
        // 【修改点】：
        // 1. 使用 NeoForge 的事件总线
        // 2. 传入刚才实例化的对象 PATCHER_INSTANCE，而不是 IrisPatcher.class
        NeoForge.EVENT_BUS.register(PATCHER_INSTANCE);
        NeoForge.EVENT_BUS.register(KeyBindingHandler.class);
        NeoForge.EVENT_BUS.addListener(SpeechBubbleClientEvents::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(SpeechBubbleClientEvents::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ModrinthUpdateChecker::onPlayerJoinWorld);
        
        // 4. 进存档后（客户端语言已加载）后台补齐本地模组内容索引
        NeoForge.EVENT_BUS.addListener(ClientModSetup::onClientLoggingIn);

        // 5. 退存档自动关闭本地模型服务器，释放内存/显存
        NeoForge.EVENT_BUS.addListener(ClientModSetup::onClientLoggingOut);

        // 6. 加载 LLM 配置
        LLMConfig.load();
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        LogUtils.getLogger().info(">>> AWESOME CLIENT SETUP TRIGGERED <<<");
        // 这里安全地使用 Minecraft.getInstance()，因为这个类绝对不会在服务端被加载
        LogUtils.getLogger().info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        // 游戏加载时读取本地模组内容索引（指纹一致则直接加载，不构建）
        ModContentIndex.initialize();
    }

    private static void onClientLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        // 语言包已加载，本地化名才准确；缺索引/索引不完整（存档未就绪时构建）时，
        // 后台等待 level 就绪后构建或补建并写盘，避免卡顿。
        CompletableFuture.runAsync(() -> {
            for (int attempt = 0; attempt < 30; attempt++) {
                ModContentIndex.ensureReady();
                if (ModContentIndex.isComplete()) {
                    return;
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        });
        // 向服务器请求结构清单（客户端无结构注册表，结构数据以服务端响应为准），
        // 响应回填本地索引并标记完整。
        PacketHandler.sendToServer(new StructureIndexRequestPacket());
        // 上次会话聊天在用本地模型 → 进世界后自动拉起本地服务器，免去每次手动重连。
        maybeAutoConnectLocalModel();
    }

    private static void onClientLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        // 退存档立即关闭本模组拉起的 llama-server，释放内存/显存（下次进存档会自动再拉起）。
        LocalModelLauncher.shutdownForSession();
    }

    /** 上次会话聊天路由指向本地模型且本地槽位已配置 → 后台自动启动本地服务器（下载缺失文件/起进程/等就绪）。 */
    private static void maybeAutoConnectLocalModel() {
        if (BuildFlags.CF_SAFE) {
            // 安全版不含本地模型功能：跳过自动连接，避免进世界时弹"本地模型自动链接失败"提示。
            return;
        }
        if (!isLocalChatActive()) {
            return; // 当前聊天没有在用本地模型，不打扰
        }
        // whenComplete 兜底：准备流程异常时也给出明确提示，避免静默无反应。
        LocalModelLauncher.prepareAsync(null).whenComplete((result, error) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return;
            }
            mc.execute(() -> {
                if (mc.player == null) {
                    return;
                }
                LocalModelLauncher.PrepareResult outcome = result != null ? result
                        : new LocalModelLauncher.PrepareResult(false, LocalModelLauncher.DEFAULT_ENDPOINT,
                                LocalModelConnector.DEFAULT_MODEL,
                                error == null ? "未知错误" : LocalModelLauncher.rootMessage(error));
                if (outcome.ready()) {
                    mc.player.sendSystemMessage(Component.translatable(
                            "message.herobrine_companion.local_auto_connect.success", outcome.modelId()));
                } else {
                    mc.player.sendSystemMessage(Component.translatable(
                            "message.herobrine_companion.local_auto_connect.failed", outcome.message()));
                }
            });
        });
    }

    /** 聊天类任务（主聊/场景聊/跨会话）是否任一路由到本地模型（本地模式在用）。 */
    private static boolean isLocalChatActive() {
        return LLMConfig.isLocalRouteActive();
    }
}
