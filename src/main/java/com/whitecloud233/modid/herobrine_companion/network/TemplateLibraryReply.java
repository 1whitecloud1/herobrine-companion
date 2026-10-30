package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record TemplateLibraryReply(int requestId, boolean ok, boolean editable, String status, String detail, List<TemplateEntry> entries) {
    public TemplateLibraryReply(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(80), buf.readUtf(512), readEntries(buf));
    }
    private static List<TemplateEntry> readEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > TemplateLimits.MAX_TEMPLATES) throw new IllegalArgumentException("Oversized template catalog");
        List<TemplateEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new TemplateEntry(buf.readUtf(200), buf.readUtf(80), buf.readUtf(32),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        return List.copyOf(entries);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId); buf.writeBoolean(ok); buf.writeBoolean(editable); buf.writeUtf(status, 80); buf.writeUtf(detail, 512);
        buf.writeVarInt(entries.size());
        for (TemplateEntry e : entries) {
            buf.writeUtf(e.id(), 200); buf.writeUtf(e.name(), 80); buf.writeUtf(e.format(), 32);
            buf.writeVarInt(e.x()); buf.writeVarInt(e.y()); buf.writeVarInt(e.z()); buf.writeVarInt(e.blocks());
        }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.acceptTemplates(context.getNetworkManager(), this));
    }
}
