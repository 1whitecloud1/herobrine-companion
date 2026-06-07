package com.whitecloud233.herobrine_companion.client.fight.network;

import com.whitecloud233.herobrine_companion.network.ClientOnlyExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SPacketStartCollapse() implements CustomPacketPayload {

    public static final Type<SPacketStartCollapse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "start_collapse"));
    public static final StreamCodec<FriendlyByteBuf, SPacketStartCollapse> CODEC =
            StreamCodec.unit(new SPacketStartCollapse());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                SPacketStartCollapse.ClientHandler.class.getName(),
                "handle",
                new Class<?>[0]
        ));
    }

    private static final class ClientHandler {
        private static void handle() {
            com.whitecloud233.herobrine_companion.client.fight.event.ClientCollapseHandler.startCollapse();
        }
    }
}
