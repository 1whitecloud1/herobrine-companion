package com.whitecloud233.modid.herobrine_companion.client;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.modid.herobrine_companion.config.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientModSetup {
    
    public static void init(IEventBus modEventBus) {
        // 1. 注册客户端生命周期事件
        modEventBus.addListener(ClientModSetup::clientSetup);

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
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        LogUtils.getLogger().info(">>> AWESOME CLIENT SETUP TRIGGERED <<<");
        // 这里安全地使用 Minecraft.getInstance()，因为这个类绝对不会在服务端被加载
        LogUtils.getLogger().info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}