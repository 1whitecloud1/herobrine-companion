package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.herobrine_companion.event.HeroRewards;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ClaimRewardPacket(int entityId, int rewardId) implements CustomPacketPayload {

    // 定义数据包的唯一类型 ID
    public static final Type<ClaimRewardPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "claim_reward"));

    // 组合式流编解码器 (用于自动序列化和反序列化)
    public static final StreamCodec<ByteBuf, ClaimRewardPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClaimRewardPacket::entityId,
            ByteBufCodecs.INT, ClaimRewardPacket::rewardId,
            ClaimRewardPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // NeoForge 1.21.1 处理方法
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 获取发包的玩家并确认在服务端
            if (context.player() instanceof ServerPlayer player && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(this.entityId());

                if (entity instanceof HeroEntity hero) {
                    HeroRewards.Reward reward = HeroRewards.getReward(this.rewardId());

                    if (reward != null && hero.getTrustLevel() >= reward.requiredTrust) {
                        if (!hero.hasClaimedReward(this.rewardId())) {

                            // 1. 给予所有物品
                            for (ItemStack item : reward.items) {
                                ItemStack copy = item.copy();
                                if (!player.getInventory().add(copy)) {
                                    player.drop(copy, false);
                                }
                            }

                            // 2. 更新实体本地记忆
                            hero.claimReward(this.rewardId());

                            // 3. 同步信任度
                            HeroDataHandler.updateGlobalTrust(hero);

                            // 4. 【核心修复】：将已领取的奖励永久写入全局存档！
                            UUID ownerUUID = hero.getOwnerUUID();
                            if (ownerUUID != null) {
                                com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData data =
                                        com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData.get(serverLevel);

                                data.addClaimedReward(ownerUUID, this.rewardId());

                                // 5. 【核心修复】：向客户端发送数据包，强行同步奖励列表，让按钮彻底灰掉！
                                com.whitecloud233.herobrine_companion.network.PacketHandler.sendToPlayer(
                                        new com.whitecloud233.herobrine_companion.network.SyncRewardsPacket(hero.getId(), data.getClaimedRewards(ownerUUID)),
                                        player
                                );
                            }
                        }
                    }
                }
            }
        });
    }
}