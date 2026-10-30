package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.structure.template.TemplatePlacementService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Typed request for tick-budgeted placement and its completion acknowledgement. */
public record TemplatePlacementRequest(UUID requestId, String templateId, int x, int y, int z)
        implements CustomPacketPayload {

    public static final Type<TemplatePlacementRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_placement_request"));

    public static final StreamCodec<FriendlyByteBuf, TemplatePlacementRequest> STREAM_CODEC =
            StreamCodec.ofMember(TemplatePlacementRequest::encode, TemplatePlacementRequest::new);

    public TemplatePlacementRequest(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(200), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(requestId);
        buffer.writeUtf(templateId, 200);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemplatePlacementRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TemplatePlacementService.start(player, payload.requestId(), payload.templateId(),
                        payload.x(), payload.y(), payload.z());
            }
        });
    }
}
