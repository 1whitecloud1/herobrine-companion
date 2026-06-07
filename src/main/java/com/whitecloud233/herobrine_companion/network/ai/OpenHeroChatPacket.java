package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.network.ClientOnlyExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenHeroChatPacket implements CustomPacketPayload {
    public static final Type<OpenHeroChatPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_hero_chat"));
    public static final StreamCodec<FriendlyByteBuf, OpenHeroChatPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenHeroChatPacket::encode, OpenHeroChatPacket::new);

    private final boolean hbInputMode;

    public OpenHeroChatPacket() {
        this(false);
    }

    public OpenHeroChatPacket(boolean hbInputMode) {
        this.hbInputMode = hbInputMode;
    }

    public OpenHeroChatPacket(FriendlyByteBuf buf) {
        this.hbInputMode = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.hbInputMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenHeroChatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                OpenHeroChatPacket.ClientHandler.class.getName(),
                "handle",
                new Class<?>[]{OpenHeroChatPacket.class},
                packet
        ));
    }

    private static final class ClientHandler {
        private static void handle(OpenHeroChatPacket packet) {
            com.whitecloud233.herobrine_companion.client.event.ClientHooks.openHeroChatFromCommand(packet.hbInputMode);
        }
    }
}
