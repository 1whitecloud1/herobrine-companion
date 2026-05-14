package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;
public class RespondCrossChatInvitePacket implements CustomPacketPayload {
    public static final Type<RespondCrossChatInvitePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "respond_cross_chat_invite"));
    public static final StreamCodec<FriendlyByteBuf, RespondCrossChatInvitePacket> STREAM_CODEC = StreamCodec.ofMember(RespondCrossChatInvitePacket::encode, RespondCrossChatInvitePacket::new);
    private final UUID requesterId;
    private final boolean accept;
    public RespondCrossChatInvitePacket(UUID requesterId, boolean accept) {
        this.requesterId = requesterId == null ? new UUID(0L, 0L) : requesterId;
        this.accept = accept;
    }
    public RespondCrossChatInvitePacket(FriendlyByteBuf buf) {
        this.requesterId = buf.readUUID();
        this.accept = buf.readBoolean();
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requesterId);
        buf.writeBoolean(this.accept);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(RespondCrossChatInvitePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer target) || target.server == null) {
                return;
            }
            ServerPlayer requester = target.server.getPlayerList().getPlayer(packet.requesterId);
            if (requester == null) {
                target.sendSystemMessage(Component.literal("[Cross HB] The requesting player is already offline."));
                return;
            }
            HeroCrossChatManager.INSTANCE.respondToRequest(target, requester, packet.accept);
        });
    }
}
