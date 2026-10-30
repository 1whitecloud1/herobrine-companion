package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.structure.template.ServerTemplateLibrary;
import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record TemplateUploadPacket(int requestId, String filename, String name, int total, int chunk, byte[] data) {
    public TemplateUploadPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(255), buf.readUtf(TemplateLimits.MAX_NAME),
                buf.readVarInt(), buf.readVarInt(), buf.readByteArray(TemplateLimits.CHUNK_BYTES));
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId); buf.writeUtf(filename, 255); buf.writeUtf(name, TemplateLimits.MAX_NAME);
        buf.writeVarInt(total); buf.writeVarInt(chunk); buf.writeByteArray(data);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> ServerTemplateLibrary.upload(context.getSender(), this));
    }
}
