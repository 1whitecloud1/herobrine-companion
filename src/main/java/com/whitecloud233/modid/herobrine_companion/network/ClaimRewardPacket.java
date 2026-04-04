package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.event.HeroRewards;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
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

            // 确保玩家和服务器维度存在
            if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(packet.entityId);

                if (entity instanceof HeroEntity hero) {
                    HeroRewards.Reward reward = HeroRewards.getReward(packet.rewardId);

                    if (reward != null && hero.getTrustLevel() >= reward.requiredTrust) {
                        if (!hero.hasClaimedReward(packet.rewardId)) {

                            // 1. 给予所有物品
                            for (ItemStack item : reward.items) {
                                ItemStack copy = item.copy();
                                if (!player.getInventory().add(copy)) {
                                    player.drop(copy, false);
                                }
                            }

                            // 2. 更新实体本地记忆
                            hero.claimReward(packet.rewardId);

                            // 3. 同步信任度
                            HeroDataHandler.updateGlobalTrust(hero);

                            // 4. 【核心修复】：将已领取的奖励永久写入全局存档！
                            UUID ownerUUID = hero.getOwnerUUID();
                            if (ownerUUID != null) {
                                com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData data =
                                        com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData.get(serverLevel);

                                data.addClaimedReward(ownerUUID, packet.rewardId);

                                // 5. 【核心修复】：向客户端发送数据包，强行同步奖励列表，让按钮彻底灰掉！
                                com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToPlayer(
                                        new com.whitecloud233.modid.herobrine_companion.network.SyncRewardsPacket(hero.getId(), data.getClaimedRewards(ownerUUID)),
                                        player
                                );
                            }
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
