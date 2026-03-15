package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public class SyncRewardsPacket implements CustomPacketPayload {

    // 定义数据包的唯一类型 (Type)
    public static final CustomPacketPayload.Type<SyncRewardsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "sync_rewards"));

    // 定义编解码器 (StreamCodec)
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

    // 1.21.1 的处理逻辑：接收 IPayloadContext
    public void handle(IPayloadContext context) {
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
    }
}