package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AppendCrossChatHistoryPacket {
    private static final String DEFAULT_KIND_PLAYER = "player";

    private final String peerName;
    private final boolean hbMode;
    private final String speaker;
    private final String content;
    private final String kind;

    public AppendCrossChatHistoryPacket(String peerName, boolean hbMode, String speaker, String content, String kind) {
        this.peerName = peerName == null ? "" : peerName;
        this.hbMode = hbMode;
        this.speaker = speaker == null ? "" : speaker;
        this.content = content == null ? "" : content;
        this.kind = kind == null ? DEFAULT_KIND_PLAYER : kind;
    }

    public AppendCrossChatHistoryPacket(FriendlyByteBuf buf) {
        this.peerName = buf.readUtf(256);
        this.hbMode = buf.readBoolean();
        this.speaker = buf.readUtf(256);
        this.content = buf.readUtf(2048);
        this.kind = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.peerName, 256);
        buf.writeBoolean(this.hbMode);
        buf.writeUtf(this.speaker, 256);
        buf.writeUtf(this.content, 2048);
        buf.writeUtf(this.kind, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NetworkClientBridge.appendCrossChatHistory(
                this.peerName, this.hbMode, this.speaker, this.content, this.kind));
        context.setPacketHandled(true);
    }
}
