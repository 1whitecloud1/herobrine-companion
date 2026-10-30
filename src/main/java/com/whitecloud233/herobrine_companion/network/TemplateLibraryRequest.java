package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.structure.template.ServerTemplateLibrary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empty id queries the catalog; a nonempty id deletes one of the sender's own templates. */
public record TemplateLibraryRequest(int requestId, String deleteId) implements CustomPacketPayload {

    public static final Type<TemplateLibraryRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_library_request"));

    public static final StreamCodec<FriendlyByteBuf, TemplateLibraryRequest> STREAM_CODEC =
            StreamCodec.ofMember(TemplateLibraryRequest::encode, TemplateLibraryRequest::new);

    public TemplateLibraryRequest(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(200));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeUtf(deleteId, 200);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemplateLibraryRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerTemplateLibrary.request(player, payload);
            }
        });
    }
}
