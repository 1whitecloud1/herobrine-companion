package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.combat.poem.StandalonePoemController;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PoemAnimationRequestPacket(int slot, int mode) {
    public PoemAnimationRequestPacket(FriendlyByteBuf buffer) { this(buffer.readByte(), buffer.readByte()); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(slot); buffer.writeByte(mode); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            if (context.getSender() != null) {
                if (slot == -1) StandalonePoemController.stop(context.getSender());
                else StandalonePoemController.request(context.getSender(), slot, mode);
            }
        });
    }
}
