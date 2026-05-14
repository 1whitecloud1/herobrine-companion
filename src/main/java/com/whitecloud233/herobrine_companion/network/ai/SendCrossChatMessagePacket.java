package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class SendCrossChatMessagePacket implements CustomPacketPayload {
    public static final Type<SendCrossChatMessagePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "send_cross_chat_message"));
    public static final StreamCodec<FriendlyByteBuf, SendCrossChatMessagePacket> STREAM_CODEC = StreamCodec.ofMember(SendCrossChatMessagePacket::encode, SendCrossChatMessagePacket::new);
    private final String message;
    public SendCrossChatMessagePacket(String message) {
        this.message = message == null ? "" : message;
    }
    public SendCrossChatMessagePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf(512);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message, 512);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(SendCrossChatMessagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.sendPlayerMessage(sender, packet.message);
            }
        });
    }
}
