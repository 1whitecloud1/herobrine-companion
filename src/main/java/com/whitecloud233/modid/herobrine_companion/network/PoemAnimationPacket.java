package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PoemAnimationPacket(int entityId, UUID playerId, int slot, int mode, int step, long startTick, long serverTick) {
    public PoemAnimationPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readUUID(), b.readByte(), b.readByte(), b.readByte(), b.readLong(), b.readLong());
    }
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId); b.writeUUID(playerId); b.writeByte(slot); b.writeByte(mode);
        b.writeByte(step); b.writeLong(startTick); b.writeLong(serverTick);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> StandalonePoemAnimation.receive(this));
        context.setPacketHandled(true);
    }
}
