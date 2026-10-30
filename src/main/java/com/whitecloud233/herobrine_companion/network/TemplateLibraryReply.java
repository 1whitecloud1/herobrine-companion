package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientTemplateLibrary;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record TemplateLibraryReply(int requestId, boolean ok, boolean editable, String status, String detail,
                                   List<TemplateEntry> entries) implements CustomPacketPayload {

    public static final Type<TemplateLibraryReply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_library_reply"));

    public static final StreamCodec<FriendlyByteBuf, TemplateLibraryReply> STREAM_CODEC =
            StreamCodec.ofMember(TemplateLibraryReply::encode, TemplateLibraryReply::new);

    public TemplateLibraryReply(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(80), buffer.readUtf(512), readEntries(buffer));
    }

    private static List<TemplateEntry> readEntries(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > TemplateLimits.MAX_TEMPLATES) {
            throw new IllegalArgumentException("Oversized template catalog");
        }
        List<TemplateEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new TemplateEntry(buffer.readUtf(200), buffer.readUtf(80), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        return List.copyOf(entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeBoolean(ok);
        buffer.writeBoolean(editable);
        buffer.writeUtf(status, 80);
        buffer.writeUtf(detail, 512);
        buffer.writeVarInt(entries.size());
        for (TemplateEntry entry : entries) {
            buffer.writeUtf(entry.id(), 200);
            buffer.writeUtf(entry.name(), 80);
            buffer.writeUtf(entry.format(), 32);
            buffer.writeVarInt(entry.x());
            buffer.writeVarInt(entry.y());
            buffer.writeVarInt(entry.z());
            buffer.writeVarInt(entry.blocks());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemplateLibraryReply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientTemplateLibrary.accept(context.connection(), payload);
        });
    }
}
