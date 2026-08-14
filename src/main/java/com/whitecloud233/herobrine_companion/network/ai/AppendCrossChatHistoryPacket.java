package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientStateSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AppendCrossChatHistoryPacket implements CustomPacketPayload {
    public static final Type<AppendCrossChatHistoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "append_cross_chat_history"));
    public static final StreamCodec<FriendlyByteBuf, AppendCrossChatHistoryPacket> STREAM_CODEC =
            StreamCodec.ofMember(AppendCrossChatHistoryPacket::encode, AppendCrossChatHistoryPacket::new);

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
        this.kind = kind == null ? "player" : kind;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AppendCrossChatHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStateSync.appendCrossChatHistory(
                packet.peerName, packet.hbMode, packet.speaker, packet.content, packet.kind));
    }
}
