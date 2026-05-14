package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class SetCrossChatPermissionPacket implements CustomPacketPayload {
    public static final Type<SetCrossChatPermissionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "set_cross_chat_permission"));
    public static final StreamCodec<FriendlyByteBuf, SetCrossChatPermissionPacket> STREAM_CODEC = StreamCodec.ofMember(SetCrossChatPermissionPacket::encode, SetCrossChatPermissionPacket::new);
    private final boolean allowIncoming;
    public SetCrossChatPermissionPacket(boolean allowIncoming) {
        this.allowIncoming = allowIncoming;
    }
    public SetCrossChatPermissionPacket(FriendlyByteBuf buf) {
        this.allowIncoming = buf.readBoolean();
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.allowIncoming);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(SetCrossChatPermissionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.setAllowIncomingRequests(sender, packet.allowIncoming);
            }
        });
    }
}
