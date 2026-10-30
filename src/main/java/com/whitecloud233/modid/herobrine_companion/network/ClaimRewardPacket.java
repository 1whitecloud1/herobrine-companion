package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.event.HeroRewards;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClaimRewardPacket {
    private final int entityId;
    private final int rewardId;

    public ClaimRewardPacket(int entityId, int rewardId) {
        this.entityId = entityId;
        this.rewardId = rewardId;
    }

    public ClaimRewardPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.rewardId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.rewardId);
    }

    public static void handle(ClaimRewardPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();

            // 确保玩家和服务器维度存在
            if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(packet.entityId);

                if (entity instanceof HeroEntity hero) {
                    int requestedReward = packet.rewardId;
                    HeroRewards.Reward reward = HeroRewards.getReward(requestedReward);
                    boolean granted = reward != null && hero.getTrustLevel() >= reward.requiredTrust
                            && !hero.hasClaimedReward(requestedReward);
                    if (granted) {
                        for (ItemStack item : reward.items) {
                            ItemStack copy = item.copy();
                            if (!player.getInventory().add(copy)) player.drop(copy, false);
                        }
                        hero.claimReward(requestedReward);
                        HeroDataHandler.updateGlobalTrust(hero);
                    }

                    // Always reply, including a rejected or already-claimed request.
                    // Other observers also need the newly claimed state.
                    SyncRewardsPacket snapshot = new SyncRewardsPacket(hero.getId(), hero.getClaimedRewards());
                    if (granted) PacketHandler.sendToTracking(snapshot, hero);
                    PacketHandler.sendToPlayer(snapshot, player);
                }
            }
        });
    }
}
