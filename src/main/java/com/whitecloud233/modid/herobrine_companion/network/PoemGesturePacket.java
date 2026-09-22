package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Intent only; animation, state and blade damage are selected on the server. */
public record PoemGesturePacket(int gesture, int slot, int mode) {
    public PoemGesturePacket(FriendlyByteBuf buffer) {
        this(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(gesture); buffer.writeByte(slot); buffer.writeByte(mode);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            if (context.getSender() != null) {
                HeroEpicFightCompat.queuePoemGesture(context.getSender(), gesture, slot, mode);
            }
        });
    }
}
