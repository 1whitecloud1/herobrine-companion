package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroRewards;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClaimRewardPacket(int entityId, int rewardId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimRewardPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "claim_reward"));

    public static final StreamCodec<ByteBuf, ClaimRewardPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClaimRewardPacket::entityId,
            ByteBufCodecs.INT, ClaimRewardPacket::rewardId,
            ClaimRewardPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClaimRewardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
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
        });
    }
}
