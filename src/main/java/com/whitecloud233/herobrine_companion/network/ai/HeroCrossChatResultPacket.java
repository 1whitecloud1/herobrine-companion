package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;
public class HeroCrossChatResultPacket implements CustomPacketPayload {
    public static final Type<HeroCrossChatResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_cross_chat_result"));
    public static final StreamCodec<FriendlyByteBuf, HeroCrossChatResultPacket> STREAM_CODEC = StreamCodec.ofMember(HeroCrossChatResultPacket::encode, HeroCrossChatResultPacket::new);
    private final UUID jobId;
    private final String reply;
    public HeroCrossChatResultPacket(UUID jobId, String reply) {
        this.jobId = jobId == null ? new UUID(0L, 0L) : jobId;
        this.reply = reply == null ? "" : reply;
    }
    public HeroCrossChatResultPacket(FriendlyByteBuf buf) {
        this.jobId = buf.readUUID();
        this.reply = buf.readUtf(4096);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.jobId);
        buf.writeUtf(this.reply, 4096);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(HeroCrossChatResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.handleGeneratedReply(sender, packet.jobId, packet.reply);
            }
        });
    }
}
