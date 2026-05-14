package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class CloseCrossChatSessionPacket implements CustomPacketPayload {
    public static final Type<CloseCrossChatSessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "close_cross_chat_session"));
    public static final StreamCodec<FriendlyByteBuf, CloseCrossChatSessionPacket> STREAM_CODEC = StreamCodec.ofMember(CloseCrossChatSessionPacket::encode, CloseCrossChatSessionPacket::new);
    public CloseCrossChatSessionPacket() {
    }
    public CloseCrossChatSessionPacket(FriendlyByteBuf buf) {
    }
    public void encode(FriendlyByteBuf buf) {
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(CloseCrossChatSessionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.closeSession(sender);
            }
        });
    }
}
