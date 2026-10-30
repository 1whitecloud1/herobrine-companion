package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record TemplatePlacementReply(UUID requestId, boolean ok, boolean complete) {
    public TemplatePlacementReply(UUID requestId, boolean ok) { this(requestId, ok, true); }
    public TemplatePlacementReply(FriendlyByteBuf buf) { this(buf.readUUID(), buf.readBoolean(), buf.readBoolean()); }
    public void encode(FriendlyByteBuf buf) { buf.writeUUID(requestId); buf.writeBoolean(ok); buf.writeBoolean(complete); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.acceptTemplatePlacement(context.getNetworkManager(), requestId, ok, complete));
    }
}
