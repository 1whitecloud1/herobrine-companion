package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.structure.template.ServerTemplateLibrary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Empty id queries the catalog; a nonempty id deletes one of the sender's own templates. */
public record TemplateLibraryRequest(int requestId, String deleteId) {
    public TemplateLibraryRequest(FriendlyByteBuf buf) { this(buf.readVarInt(), buf.readUtf(200)); }
    public void encode(FriendlyByteBuf buf) { buf.writeVarInt(requestId); buf.writeUtf(deleteId, 200); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> ServerTemplateLibrary.request(context.getSender(), this));
    }
}
