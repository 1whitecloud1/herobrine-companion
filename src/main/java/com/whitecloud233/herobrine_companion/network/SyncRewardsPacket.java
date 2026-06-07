package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public class SyncRewardsPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncRewardsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "sync_rewards"));

    public static final StreamCodec<FriendlyByteBuf, SyncRewardsPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncRewardsPacket::encode, SyncRewardsPacket::new);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                SyncRewardsPacket.ClientHandler.class.getName(),
                "handle",
                new Class<?>[]{SyncRewardsPacket.class},
                this
        ));
    }

    private static final class ClientHandler {
        private static void handle(SyncRewardsPacket packet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }

            Entity entity = minecraft.level.getEntity(packet.entityId);
            if (entity instanceof HeroEntity hero) {
                for (int id : packet.claimedRewards) {
                    hero.claimReward(id);
                }
            }
        }
    }
}
