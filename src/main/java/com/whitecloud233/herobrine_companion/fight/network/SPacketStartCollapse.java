package com.whitecloud233.herobrine_companion.fight.network;

import com.whitecloud233.herobrine_companion.client.fight.event.ClientCollapseHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SPacketStartCollapse() implements CustomPacketPayload {

    public static final Type<SPacketStartCollapse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "start_collapse"));
    public static final StreamCodec<FriendlyByteBuf, SPacketStartCollapse> STREAM_CODEC =
            StreamCodec.unit(new SPacketStartCollapse());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(ClientCollapseHandler::startCollapse);
    }
}
