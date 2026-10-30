package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.structure.template.TemplatePlacementService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Typed request for tick-budgeted placement and its completion acknowledgement. */
public record TemplatePlacementRequest(UUID requestId, String templateId, int x, int y, int z) {
    public TemplatePlacementRequest(FriendlyByteBuf buf) { this(buf.readUUID(), buf.readUtf(200), buf.readInt(), buf.readInt(), buf.readInt()); }
    public void encode(FriendlyByteBuf buf) { buf.writeUUID(requestId); buf.writeUtf(templateId, 200); buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            var player = context.getSender();
            if (player == null) return;
            TemplatePlacementService.start(player, requestId, templateId, x, y, z);
        });
    }
}
