package com.whitecloud233.modid.herobrine_companion.client.event;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.util.DebugLog;
import com.whitecloud233.modid.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.modid.herobrine_companion.client.service.ModContentIndex;
import com.whitecloud233.modid.herobrine_companion.config.ConfigScreen;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.StructureIndexPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.concurrent.CompletableFuture;

public class ClientModSetup {

    @SuppressWarnings("removal")
    public static void init(IEventBus modEventBus) {
        // 1. 注册客户端生命周期事件
        modEventBus.addListener(ClientModSetup::clientSetup);

        // 1.5 注册额外的模型烘焙：终末之诗未装 GeckoLib 时的回退渲染
        //     需要直接烘焙原来的手写模型 poem_of_the_end_base#inventory
        modEventBus.addListener(ClientModSetup::onRegisterAdditionalModels);

        // 2. 注册配置界面（将原本在主类构造函数里的代码移到这里）
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> new ConfigScreen(screen)
                )
        );

        // 3. 初始化 Patcher
        HerobrineCompanion.PATCHER_INSTANCE = new IrisPatcher();
        MinecraftForge.EVENT_BUS.register(IrisPatcher.class);

        // 4. 进存档后（客户端语言已加载）后台补齐本地模组内容索引
        MinecraftForge.EVENT_BUS.addListener(ClientModSetup::onClientLoggingIn);
    }

    private static void onRegisterAdditionalModels(final ModelEvent.RegisterAdditional event) {
        // 注意：ModelBakery 对 inventory 变体的附加模型会自动补 "item/" 前缀，
        // 因此这里只注册文件名，不要写 "item/poem_of_the_end_base"（否则会变成 item/item/...）
        event.register(new ModelResourceLocation(HerobrineCompanion.MODID, "poem_of_the_end_base", "inventory"));
        // 无名之蛋糕摆件(Bedrock birthday_cake_prop):geo 转换出的方块模型
        event.register(com.whitecloud233.modid.herobrine_companion.client.render.BirthdayCakePropRenderer.MODEL);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        DebugLog.info(LogUtils.getLogger(), ">>> AWESOME CLIENT SETUP TRIGGERED <<<");
        // 这里安全地使用 Minecraft.getInstance()，因为这个类绝对不会在服务端被加载
        DebugLog.info(LogUtils.getLogger(), "MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
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
        // 向服务器请求结构清单（1.20.1 客户端无结构注册表，结构数据以服务端响应为准），
        // 响应回填本地索引并标记完整。
        PacketHandler.sendToServer(new StructureIndexPacket(true));
    }
}