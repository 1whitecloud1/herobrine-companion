package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.structure.template.ServerTemplateLibrary;
import com.whitecloud233.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TemplateUploadPacket(int requestId, String filename, String name, int total, int chunk, byte[] data)
        implements CustomPacketPayload {

    public static final Type<TemplateUploadPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_upload"));

    public static final StreamCodec<FriendlyByteBuf, TemplateUploadPacket> STREAM_CODEC =
            StreamCodec.ofMember(TemplateUploadPacket::encode, TemplateUploadPacket::new);

    public TemplateUploadPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(255), buffer.readUtf(TemplateLimits.MAX_NAME),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(TemplateLimits.CHUNK_BYTES));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeUtf(filename, 255);
        buffer.writeUtf(name, TemplateLimits.MAX_NAME);
        buffer.writeVarInt(total);
        buffer.writeVarInt(chunk);
        buffer.writeByteArray(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemplateUploadPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerTemplateLibrary.upload(player, payload);
            }
        });
    }
}
