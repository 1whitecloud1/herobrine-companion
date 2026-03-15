package com.whitecloud233.herobrine_companion.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TriggerEternalOathPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TriggerEternalOathPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "trigger_eternal_oath"));

    public static final StreamCodec<ByteBuf, TriggerEternalOathPacket> STREAM_CODEC = StreamCodec.unit(new TriggerEternalOathPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TriggerEternalOathPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 【安全调用】仅在客户端执行逻辑，且通过全限定类名调用 ClientHooks，避免服务端加载问题
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.whitecloud233.herobrine_companion.client.ClientHooks.triggerEternalOath();
            }
        });
    }
}
