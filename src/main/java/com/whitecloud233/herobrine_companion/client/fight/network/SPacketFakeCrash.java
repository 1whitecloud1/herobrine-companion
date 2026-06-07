package com.whitecloud233.herobrine_companion.client.fight.network;

import com.whitecloud233.herobrine_companion.network.ClientOnlyExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SPacketFakeCrash() implements CustomPacketPayload {

    public static final Type<SPacketFakeCrash> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "fake_crash"));
    public static final StreamCodec<FriendlyByteBuf, SPacketFakeCrash> CODEC =
            StreamCodec.unit(new SPacketFakeCrash());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                SPacketFakeCrash.ClientHandler.class.getName(),
                "handle",
                new Class<?>[0]
        ));
    }

    private static final class ClientHandler {
        private static void handle() {
            com.whitecloud233.herobrine_companion.client.gui.FakeCrashScreen.open();
        }
    }
}
