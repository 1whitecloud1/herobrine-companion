package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentFeatures;
import com.whitecloud233.modid.herobrine_companion.network.AgentFeatureChannel;
import com.whitecloud233.modid.herobrine_companion.network.AgentFeatureRequest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 客户端侧的扩展能力握手（交接 4.2 / T22）。
 *
 * <p><b>只做一件事</b>：登录时若对端声明支持 {@link AgentFeatureChannel}，
 * 就发一次能力查询；退出时清空能力位。不做 UI、不决定工具语义。</p>
 *
 * <p><b>旧服务端</b>：它不会注册本通道，{@code remoteSupports} 返回 false，
 * 于是这里<b>连包都不发</b>，{@link ClientAgentFeatures} 保持 0，
 * 工具清单里不会出现旧服务端不认识的扩展工具——旧功能照常可用。</p>
 */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAgentFeatureSync {

    private ClientAgentFeatureSync() {
    }

    /** 登录后请求一次能力位；不支持则静默跳过（旧服务端路径）。 */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientAgentFeatures.clear();
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !AgentFeatureChannel.isSupported(listener.getConnection())) {
            // 旧服务端：不发包、不展示扩展工具，主通道旧工具行为不变。
            return;
        }
        AgentFeatureChannel.sendRequest(new AgentFeatureRequest(UUID.randomUUID()));
    }

    /** 退出 / 断线：回到"未确认"，避免把上一个存档的能力带到下一个。 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAgentFeatures.clear();
    }
}
