package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncCrossChatStatePacket {
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.syncCrossChatState(
                this.allowIncoming, this.activeSession, this.peerName, this.autoChatEnabled, this.autoHbTurnLimit));
        context.setPacketHandled(true);
    }
}
