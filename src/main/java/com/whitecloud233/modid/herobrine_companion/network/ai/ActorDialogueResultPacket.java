package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialogueManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ActorDialogueResultPacket {
    private final UUID jobId;
    private final String reply;

    public ActorDialogueResultPacket(UUID jobId, String reply) {
        this.jobId = jobId;
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ActorDialogueManager.INSTANCE.handleGeneratedReply(sender, this.jobId, this.reply);
            }
        });
        context.setPacketHandled(true);
    }
}
