package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
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
        context.enqueueWork(() -> {
            // Client side handling
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    for (int id : this.claimedRewards) {
                        hero.claimReward(id);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
