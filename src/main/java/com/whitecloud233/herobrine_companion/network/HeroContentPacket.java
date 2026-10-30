package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.event.HeroContentLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HeroContentPacket(byte[] data) implements CustomPacketPayload {
    public static final Type<HeroContentPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "content_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, HeroContentPacket> STREAM_CODEC = StreamCodec.ofMember(HeroContentPacket::encode, HeroContentPacket::new);
    public HeroContentPacket { if (data.length > HeroContentLimits.MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Oversized Herobrine content"); data = data.clone(); }
    public HeroContentPacket(FriendlyByteBuf buffer) { this(buffer.readByteArray(HeroContentLimits.MAX_PAYLOAD_BYTES)); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByteArray(data); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(HeroContentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) com.whitecloud233.herobrine_companion.client.network.ClientHeroContent.accept(packet.data);
        });
    }
}
