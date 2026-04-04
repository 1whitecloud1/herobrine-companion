package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.item.SourceFlowItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TeleportToHeroPacket() implements CustomPacketPayload {

    // 定义数据包的唯一类型 ID
    public static final Type<TeleportToHeroPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "teleport_to_hero"));

    // 空数据包的无参数编解码器
    public static final StreamCodec<ByteBuf, TeleportToHeroPacket> STREAM_CODEC = StreamCodec.unit(new TeleportToHeroPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // NeoForge 1.21.1 的新处理方法
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // 直接调用 SourceFlowItem 里的传送逻辑
                SourceFlowItem.performTeleportToHero(player);
            }
        });
    }
}