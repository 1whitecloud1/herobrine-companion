package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateLimits;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import java.util.function.Consumer;

public final class TemplatePacketChecks {
    private static int checks;
    public static void main(String[] args) {
        String id = "herobrine_companion:uploaded/" + "1".repeat(32) + "/" + "a".repeat(64);
        for (String delete : List.of("", id)) {
            TemplateLibraryRequest original = new TemplateLibraryRequest(42, delete);
            roundTrip(original::encode, buf -> {
                var decoded = new TemplateLibraryRequest(buf);
                check(decoded.equals(original), "Library query/delete codec");
            });
        }
        byte[] bytes = new byte[TemplateLimits.CHUNK_BYTES]; new Random(1).nextBytes(bytes);
        var upload = new TemplateUploadPacket(53, "中文城堡.litematic", "城堡", 2 * bytes.length, 1, bytes);
        roundTrip(upload::encode, buf -> {
            var decoded = new TemplateUploadPacket(buf);
            check(decoded.requestId() == 53 && decoded.filename().equals(upload.filename()) && decoded.name().equals(upload.name())
                    && decoded.total() == upload.total() && decoded.chunk() == 1 && Arrays.equals(decoded.data(), bytes), "Upload chunk codec");
        });
        List<TemplateEntry> entries = new ArrayList<>();
        for (int i = 0; i < TemplateLimits.MAX_TEMPLATES; i++) entries.add(new TemplateEntry(id, "中文模板" + i, "schem v3", 12, 23, 34, 3456));
        var reply = new TemplateLibraryReply(73, false, true, "error", "Missing block: test:missing", entries);
        roundTrip(reply::encode, buf -> check(new TemplateLibraryReply(buf).equals(reply), "Bounded catalog codec"));
        UUID request = UUID.randomUUID();
        var placement = new TemplatePlacementRequest(request, id, -16, 0, 16);
        roundTrip(placement::encode, buf -> check(new TemplatePlacementRequest(buf).equals(placement), "Placement coordinates codec"));
        for (boolean ok : new boolean[]{true, false}) {
            var placed = new TemplatePlacementReply(request, ok);
            roundTrip(placed::encode, buf -> check(new TemplatePlacementReply(buf).equals(placed), "Placement result codec"));
        }
        var progress = new TemplatePlacementReply(request, true, false);
        roundTrip(progress::encode, buf -> check(new TemplatePlacementReply(buf).equals(progress), "Placement heartbeat must not complete the request"));
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oversized.writeVarInt(1); oversized.writeBoolean(true); oversized.writeBoolean(true); oversized.writeUtf("ready"); oversized.writeUtf("");
            oversized.writeVarInt(TemplateLimits.MAX_TEMPLATES + 1);
            try { new TemplateLibraryReply(oversized); throw new AssertionError("Oversized catalog accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        } finally { oversized.release(); }
        System.out.println("TemplatePacketChecks: " + checks + " checks passed");
    }

    private static void roundTrip(Consumer<FriendlyByteBuf> encode, Consumer<FriendlyByteBuf> check) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { encode.accept(buffer); check.accept(buffer); if (buffer.isReadable()) throw new AssertionError("Trailing bytes"); }
        finally { buffer.release(); }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
