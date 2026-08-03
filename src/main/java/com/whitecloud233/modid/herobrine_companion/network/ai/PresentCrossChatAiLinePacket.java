package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PresentCrossChatAiLinePacket {
    private static final String DEFAULT_KIND_HB = "hb";

    public static final byte TYPE_REMOTE_HB_REPLY = 0;
    public static final byte TYPE_HB_TO_HB_OPENING = 1;
    public static final byte TYPE_HB_ECHO = 2;

    private final String peerName;
    private final boolean hbMode;
    private final String speaker;
    private final String content;
    private final String kind;
    private final byte displayType;
    private final String primaryName;
    private final String secondaryName;
    private final boolean translateForViewer;

    public PresentCrossChatAiLinePacket(String peerName,
                                        boolean hbMode,
                                        String speaker,
                                        String content,
                                        String kind,
                                        byte displayType,
                                        String primaryName,
                                        String secondaryName,
                                        boolean translateForViewer) {
        this.peerName = peerName == null ? "" : peerName;
        this.hbMode = hbMode;
        this.speaker = speaker == null ? "" : speaker;
        this.content = content == null ? "" : content;
        this.kind = kind == null ? DEFAULT_KIND_HB : kind;
        this.displayType = displayType;
        this.primaryName = primaryName == null ? "" : primaryName;
        this.secondaryName = secondaryName == null ? "" : secondaryName;
        this.translateForViewer = translateForViewer;
    }

    public PresentCrossChatAiLinePacket(FriendlyByteBuf buf) {
        this.peerName = buf.readUtf(256);
        this.hbMode = buf.readBoolean();
        this.speaker = buf.readUtf(256);
        this.content = buf.readUtf(4096);
        this.kind = buf.readUtf(32);
        this.displayType = buf.readByte();
        this.primaryName = buf.readUtf(256);
        this.secondaryName = buf.readUtf(256);
        this.translateForViewer = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.peerName, 256);
        buf.writeBoolean(this.hbMode);
        buf.writeUtf(this.speaker, 256);
        buf.writeUtf(this.content, 4096);
        buf.writeUtf(this.kind, 32);
        buf.writeByte(this.displayType);
        buf.writeUtf(this.primaryName, 256);
        buf.writeUtf(this.secondaryName, 256);
        buf.writeBoolean(this.translateForViewer);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.presentCrossChatAiLine(
                this.peerName,
                this.hbMode,
                this.speaker,
                this.content,
                this.kind,
                this.displayType,
                this.primaryName,
                this.secondaryName,
                this.translateForViewer));
        context.setPacketHandled(true);
    }
}
