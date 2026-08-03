package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SyncRewardsPacket {
    private final int entityId;
    private final Set<Integer> claimedRewards;

    public SyncRewardsPacket(int entityId, Set<Integer> claimedRewards) {
        this.entityId = entityId;
        this.claimedRewards = claimedRewards;
    }

    public SyncRewardsPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        int size = buf.readInt();
        this.claimedRewards = new HashSet<>();
        for (int i = 0; i < size; i++) {
            this.claimedRewards.add(buf.readInt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.claimedRewards.size());
        for (int id : this.claimedRewards) {
            buf.writeInt(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.applySyncRewards(this.entityId, this.claimedRewards));
        context.setPacketHandled(true);
    }
}
