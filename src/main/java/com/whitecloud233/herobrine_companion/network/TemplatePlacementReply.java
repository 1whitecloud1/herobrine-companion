package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientTemplatePlacement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record TemplatePlacementReply(UUID requestId, boolean ok, boolean complete) implements CustomPacketPayload {

    public static final Type<TemplatePlacementReply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_placement_reply"));

    public static final StreamCodec<FriendlyByteBuf, TemplatePlacementReply> STREAM_CODEC =
            StreamCodec.ofMember(TemplatePlacementReply::encode, TemplatePlacementReply::new);

    public TemplatePlacementReply(UUID requestId, boolean ok) {
        this(requestId, ok, true);
    }

    public TemplatePlacementReply(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(requestId);
        buffer.writeBoolean(ok);
        buffer.writeBoolean(complete);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemplatePlacementReply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientTemplatePlacement.accept(context.connection(), payload.requestId(), payload.ok(), payload.complete());
        });
    }
}
