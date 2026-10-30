package com.whitecloud233.modid.herobrine_companion.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

public final class PacketCodecChecks {
    private static int checks;

    public static void main(String[] args) {
        for (int interval : new int[]{5, 127, 128, 600}) {
            for (boolean enabled : new boolean[]{false, true}) {
                FriendlyByteBuf current = new FriendlyByteBuf(Unpooled.buffer());
                FriendlyByteBuf roundTrip = new FriendlyByteBuf(Unpooled.buffer());
                FriendlyByteBuf legacy = null;
                FriendlyByteBuf legacyRoundTrip = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new SyncServerConfigPacket(true, false, true, false, true, false, true, false,
                            interval, true, false, enabled).encode(current);
                    byte[] expected = bytes(current);
                    SyncServerConfigPacket decoded = new SyncServerConfigPacket(current);
                    if (current.isReadable()) throw new AssertionError("Current config packet was not consumed");
                    decoded.encode(roundTrip);
                    if (!Arrays.equals(expected, bytes(roundTrip))) throw new AssertionError("Current config round trip changed bytes");
                    checks++;

                    byte[] oldPayload = Arrays.copyOf(expected, expected.length - 1);
                    legacy = new FriendlyByteBuf(Unpooled.wrappedBuffer(oldPayload));
                    new SyncServerConfigPacket(legacy).encode(legacyRoundTrip);
                    if (legacy.isReadable()) throw new AssertionError("Legacy config packet was not consumed");
                    if (!Arrays.equals(oldPayload, bytes(legacyRoundTrip))) throw new AssertionError("Missing legacy flag must stay absent");
                    checks++;
                } finally {
                    current.release();
                    roundTrip.release();
                    if (legacy != null) legacy.release();
                    legacyRoundTrip.release();
                }
            }
        }
        System.out.println("PacketCodecChecks: " + checks + " checks passed");
    }

    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.writerIndex()];
        buffer.getBytes(0, bytes);
        return bytes;
    }
}
