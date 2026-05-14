package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public class SetCrossChatAutoChatPacket implements CustomPacketPayload {
    public static final Type<SetCrossChatAutoChatPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "set_cross_chat_auto_chat"));
    public static final StreamCodec<FriendlyByteBuf, SetCrossChatAutoChatPacket> STREAM_CODEC = StreamCodec.ofMember(SetCrossChatAutoChatPacket::encode, SetCrossChatAutoChatPacket::new);
    private final boolean enabled;
    public SetCrossChatAutoChatPacket(boolean enabled) {
        this.enabled = enabled;
    }
    public SetCrossChatAutoChatPacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(SetCrossChatAutoChatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.setAutoHbConversation(sender, packet.enabled);
            }
        });
    }
}
