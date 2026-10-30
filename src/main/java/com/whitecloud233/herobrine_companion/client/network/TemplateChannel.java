package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.network.TemplateLibraryReply;
import com.whitecloud233.herobrine_companion.network.TemplatePlacementReply;
import com.whitecloud233.herobrine_companion.network.TemplatePlacementRequest;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 模板通道的可用性判定。
 *
 * <p>1.20.1 版用独立 {@code SimpleChannel} 的 {@code isRemotePresent} 判断对端是否支持；
 * NeoForge 1.21.1 只有一个 {@code PayloadRegistrar}，等价做法是查对端是否注册了
 * 本通道的 payload 类型。这样旧服务器（没有模板包）会得到 {@code false}，
 * UI 显示「不支持」而不是发出未知包。</p>
 */
public final class TemplateChannel {
    private TemplateChannel() { }

    /** 对端是否注册了模板通道（即可以收发 {@link TemplateLibraryReply}）。 */
    public static boolean supported(@Nullable net.minecraft.network.Connection connection) {
        if (connection == null) return false;
        return NetworkRegistry.hasChannel(connection, null, TemplateLibraryReply.TYPE.id());
    }
}
