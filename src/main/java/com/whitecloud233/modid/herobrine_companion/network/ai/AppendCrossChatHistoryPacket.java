package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.client.service.CrossChatHistoryStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AppendCrossChatHistoryPacket {
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
        this.kind = kind == null ? CrossChatHistoryStore.KIND_PLAYER : kind;
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
        // The server only relays the event. Actual persistence happens exclusively on each
        // receiving client, so both participants keep their own local archive on disk.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                CrossChatHistoryStore.getInstance().appendEntry(this.peerName, this.hbMode, this.speaker, this.content, this.kind)));
        context.setPacketHandled(true);
    }
}
