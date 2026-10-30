package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Authoritative protection state sent only to the affected player. */
public final class SyncPeacefulPacket {
    private final boolean peaceful;

    public SyncPeacefulPacket(boolean peaceful) {
        this.peaceful = peaceful;
    }

    public SyncPeacefulPacket(FriendlyByteBuf buffer) {
        this.peaceful = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.peaceful);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.setPeacefulProtection(this.peaceful));
    }
}
