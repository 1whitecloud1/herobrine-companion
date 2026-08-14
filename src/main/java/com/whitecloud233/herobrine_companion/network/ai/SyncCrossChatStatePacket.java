package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientStateSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SyncCrossChatStatePacket implements CustomPacketPayload {
    public static final Type<SyncCrossChatStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "sync_cross_chat_state"));
    public static final StreamCodec<FriendlyByteBuf, SyncCrossChatStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncCrossChatStatePacket::encode, SyncCrossChatStatePacket::new);

    private final boolean allowIncoming;
    private final boolean activeSession;
    private final String peerName;
    private final boolean autoChatEnabled;
    private final int autoHbTurnLimit;

    public SyncCrossChatStatePacket(boolean allowIncoming, boolean activeSession, String peerName, boolean autoChatEnabled, int autoHbTurnLimit) {
        this.allowIncoming = allowIncoming;
        this.activeSession = activeSession;
        this.peerName = peerName == null ? "" : peerName;
        this.autoChatEnabled = autoChatEnabled;
        this.autoHbTurnLimit = autoHbTurnLimit;
    }

    public SyncCrossChatStatePacket(FriendlyByteBuf buf) {
        this.allowIncoming = buf.readBoolean();
        this.activeSession = buf.readBoolean();
        this.peerName = buf.readUtf(256);
        this.autoChatEnabled = buf.readBoolean();
        this.autoHbTurnLimit = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.allowIncoming);
        buf.writeBoolean(this.activeSession);
        buf.writeUtf(this.peerName, 256);
        buf.writeBoolean(this.autoChatEnabled);
        buf.writeVarInt(this.autoHbTurnLimit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCrossChatStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStateSync.syncCrossChatState(
                packet.allowIncoming,
                packet.activeSession,
                packet.peerName,
                packet.autoChatEnabled,
                packet.autoHbTurnLimit
        ));
    }
}
