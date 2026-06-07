package com.whitecloud233.herobrine_companion.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AIObservationPacket(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                                  String contextTranslationKey, String contextFallbackName) implements CustomPacketPayload {
    public static final Type<AIObservationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "ai_observation"));

    public static final StreamCodec<ByteBuf, AIObservationPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, AIObservationPacket::heroId,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::observationDesc,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::fallbackKey,
            ByteBufCodecs.INT, AIObservationPacket::fallbackVariants,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::contextTranslationKey,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::contextFallbackName,
            AIObservationPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> NetworkClientBridge.handleAIObservation(
                this.heroId,
                this.observationDesc,
                this.fallbackKey,
                this.fallbackVariants,
                this.contextTranslationKey,
                this.contextFallbackName
        ));
    }
}
