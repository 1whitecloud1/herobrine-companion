package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenCrossChatInvitePacket {
    private final UUID requesterId;
    private final String requesterName;

    public OpenCrossChatInvitePacket(UUID requesterId, String requesterName) {
        this.requesterId = requesterId;
        this.requesterName = requesterName == null ? "" : requesterName;
    }

    public OpenCrossChatInvitePacket(FriendlyByteBuf buf) {
        this.requesterId = buf.readUUID();
        this.requesterName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requesterId);
        buf.writeUtf(this.requesterName, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientHooks.openCrossChatInvite(this.requesterId, this.requesterName)));
        context.setPacketHandled(true);
    }
}

