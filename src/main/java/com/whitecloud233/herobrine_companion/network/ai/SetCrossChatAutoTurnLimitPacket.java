package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class SetCrossChatAutoTurnLimitPacket implements CustomPacketPayload {
    public static final Type<SetCrossChatAutoTurnLimitPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "set_cross_chat_auto_turn_limit"));
    public static final StreamCodec<FriendlyByteBuf, SetCrossChatAutoTurnLimitPacket> STREAM_CODEC = StreamCodec.ofMember(SetCrossChatAutoTurnLimitPacket::encode, SetCrossChatAutoTurnLimitPacket::new);
    private final int turnLimit;
    public SetCrossChatAutoTurnLimitPacket(int turnLimit) {
        this.turnLimit = turnLimit;
    }
    public SetCrossChatAutoTurnLimitPacket(FriendlyByteBuf buf) {
        this.turnLimit = buf.readVarInt();
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.turnLimit);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(SetCrossChatAutoTurnLimitPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.setAutoHbTurnLimit(sender, packet.turnLimit);
            }
        });
    }
}
