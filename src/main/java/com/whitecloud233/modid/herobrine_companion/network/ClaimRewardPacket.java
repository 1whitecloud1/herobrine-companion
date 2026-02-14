package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroRewards;
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
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(packet.entityId);
                if (entity instanceof HeroEntity hero) {
                    HeroRewards.Reward reward = HeroRewards.getReward(packet.rewardId);
                    if (reward != null && hero.getTrustLevel() >= reward.requiredTrust) {
                        if (!hero.hasClaimedReward(packet.rewardId)) {
                            // 给予所有物品
                            for (ItemStack item : reward.items) {
                                ItemStack copy = item.copy();
                                if (!player.getInventory().add(copy)) {
                                    player.drop(copy, false);
                                }
                            }

                            hero.claimReward(packet.rewardId);
                            // [新增] 立即同步到全局存档，防止重启后回档
                            HeroDataHandler.updateGlobalTrust(hero);
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
