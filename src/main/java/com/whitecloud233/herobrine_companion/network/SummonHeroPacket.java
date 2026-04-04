package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SummonHeroPacket() implements CustomPacketPayload {

    // 定义数据包的唯一类型 ID
    public static final Type<SummonHeroPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "summon_hero"));

    // 空数据包的无参数编解码器
    public static final StreamCodec<ByteBuf, SummonHeroPacket> STREAM_CODEC = StreamCodec.unit(new SummonHeroPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // NeoForge 1.21.1 的新处理方法
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 从 context 中获取发送此数据包的玩家
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
                // 直接调用你写好的公共召唤/跨维度传送逻辑
                HeroSummonItem.performSummonOrTeleport(serverLevel, player, player.position());
            }
        });
    }
}