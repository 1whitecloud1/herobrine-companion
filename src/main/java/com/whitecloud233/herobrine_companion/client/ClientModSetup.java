package com.whitecloud233.herobrine_companion.client;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.render.IrisPatcher;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.config.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public class ClientModSetup {
    
    public static IrisPatcher PATCHER_INSTANCE;

    public static void init(IEventBus modEventBus) {
        // 1. 注册客户端生命周期事件
        modEventBus.addListener(ClientModSetup::clientSetup);

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
        
        // 4. 加载 LLM 配置
        LLMConfig.load();
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        LogUtils.getLogger().info(">>> AWESOME CLIENT SETUP TRIGGERED <<<");
        // 这里安全地使用 Minecraft.getInstance()，因为这个类绝对不会在服务端被加载
        LogUtils.getLogger().info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
