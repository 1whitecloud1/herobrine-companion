package com.whitecloud233.herobrine_companion.compat.cooking;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

public final class CookingPacketChecks {
    public static void main(String[] args) {
        int checks = 0;
        for (int count : new int[]{1, 64, 128, Integer.MAX_VALUE}) {
            RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            RegistryFriendlyByteBuf expected = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            RegistryFriendlyByteBuf decoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(3, 70, -8);
                ResourceLocation recipe = ResourceLocation.tryParse("test:cooking");
                SelectCookOptionPacket packet = new SelectCookOptionPacket(37, mutable, recipe, count);
                mutable.set(99, 99, 99);
                packet.encode(encoded);
                expected.writeInt(37);
                expected.writeBlockPos(new BlockPos(3, 70, -8));
                expected.writeResourceLocation(recipe);
                expected.writeVarInt(count);
                if (!Arrays.equals(bytes(encoded), bytes(expected))) throw new AssertionError("Cooking request wire layout changed or mutable position leaked");
                new SelectCookOptionPacket(encoded).encode(decoded);
                if (encoded.isReadable() || !Arrays.equals(bytes(decoded), bytes(expected))) throw new AssertionError("Cooking request round trip changed");
                checks += 2;
            } finally {
                encoded.release();
                expected.release();
                decoded.release();
            }
        }
        System.out.println("CookingPacketChecks: " + checks + " checks passed");
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        byte[] result = new byte[buf.writerIndex()];
        buf.getBytes(0, result);
        return result;
    }
}
