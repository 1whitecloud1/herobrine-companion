package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class SendCrossChatHbMessagePacket implements CustomPacketPayload {
    public static final Type<SendCrossChatHbMessagePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "send_cross_chat_hb_message"));
    public static final StreamCodec<FriendlyByteBuf, SendCrossChatHbMessagePacket> STREAM_CODEC = StreamCodec.ofMember(SendCrossChatHbMessagePacket::encode, SendCrossChatHbMessagePacket::new);
    private final String message;
    public SendCrossChatHbMessagePacket(String message) {
        this.message = message == null ? "" : message;
    }
    public SendCrossChatHbMessagePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf(512);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message, 512);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(SendCrossChatHbMessagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.sendHbToHbMessage(sender, packet.message);
            }
        });
    }
}
