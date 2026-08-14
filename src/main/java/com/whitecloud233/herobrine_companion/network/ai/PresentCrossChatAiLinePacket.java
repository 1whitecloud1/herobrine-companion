package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAiPrompts;
import com.whitecloud233.herobrine_companion.client.service.CrossChatHistoryStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PresentCrossChatAiLinePacket implements CustomPacketPayload {
    public static final byte TYPE_REMOTE_HB_REPLY = 0;
    public static final byte TYPE_HB_TO_HB_OPENING = 1;
    public static final byte TYPE_HB_ECHO = 2;

    public static final Type<PresentCrossChatAiLinePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "present_cross_chat_ai_line"));
    public static final StreamCodec<FriendlyByteBuf, PresentCrossChatAiLinePacket> STREAM_CODEC = StreamCodec.ofMember(PresentCrossChatAiLinePacket::encode, PresentCrossChatAiLinePacket::new);

    private final String peerName;
    private final boolean hbMode;
    private final String speaker;
    private final String content;
    private final String kind;
    private final byte displayType;
    private final String primaryName;
    private final String secondaryName;
    private final boolean translateForViewer;

    public PresentCrossChatAiLinePacket(String peerName, boolean hbMode, String speaker, String content, String kind, byte displayType, String primaryName, String secondaryName, boolean translateForViewer) {
        this.peerName = peerName == null ? "" : peerName;
        this.hbMode = hbMode;
        this.speaker = speaker == null ? "" : speaker;
        this.content = content == null ? "" : content;
        this.kind = kind == null ? CrossChatHistoryStore.KIND_HB : kind;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PresentCrossChatAiLinePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAiPrompts.presentCrossChatAiLine(
                packet.peerName,
                packet.hbMode,
                packet.speaker,
                packet.content,
                packet.kind,
                packet.displayType,
                packet.primaryName,
                packet.secondaryName,
                packet.translateForViewer
        ));
    }
}
