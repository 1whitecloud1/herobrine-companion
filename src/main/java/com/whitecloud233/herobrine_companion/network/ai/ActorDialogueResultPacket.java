package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.dialogue.ActorDialogueManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class ActorDialogueResultPacket implements CustomPacketPayload {
    public static final Type<ActorDialogueResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "actor_dialogue_result"));
    public static final StreamCodec<FriendlyByteBuf, ActorDialogueResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(ActorDialogueResultPacket::encode, ActorDialogueResultPacket::new);

    private final UUID jobId;
    private final String reply;

    public ActorDialogueResultPacket(UUID jobId, String reply) {
        this.jobId = jobId == null ? new UUID(0L, 0L) : jobId;
        this.reply = reply == null ? "" : reply;
    }

    public ActorDialogueResultPacket(FriendlyByteBuf buf) {
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

    public static void handle(ActorDialogueResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                ActorDialogueManager.INSTANCE.handleGeneratedReply(sender, packet.jobId, packet.reply);
            }
        });
    }
}
