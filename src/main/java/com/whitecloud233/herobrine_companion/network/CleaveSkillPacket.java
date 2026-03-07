package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 1.21.1 更新：使用 record 记录类，并实现 CustomPacketPayload 接口
public record CleaveSkillPacket() implements CustomPacketPayload {

    // 1.21.1 更新：每个数据包必须拥有一个唯一的 Type 标识符
    public static final CustomPacketPayload.Type<CleaveSkillPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "cleave_skill"));

    // 1.21.1 更新：使用 StreamCodec 替代旧版的 FriendlyByteBuf 读写
    // 由于这是一个空包（只传信号不传数据），直接使用 StreamCodec.unit 即可，极其方便
    public static final StreamCodec<ByteBuf, CleaveSkillPacket> STREAM_CODEC = StreamCodec.unit(new CleaveSkillPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 1.21.1 更新：使用 IPayloadContext 处理上下文，替代旧版的 NetworkEvent.Context
    public static void handle(CleaveSkillPacket payload, IPayloadContext context) {
        // 同样需要加入主线程队列执行，防止异步引发的并发修改异常
        context.enqueueWork(() -> {
            // 通过 context.player() 直接获取发送此包的玩家，并强转为 ServerPlayer
            if (context.player() instanceof ServerPlayer player) {
                // 【加入 Debug 提示 1】
                player.sendSystemMessage(Component.literal("§a[Debug] 服务端：已收到客户端的 R 键数据包！"));

                // 获取主手物品进行校验
                if (player.getMainHandItem().getItem() instanceof PoemOfTheEndItem poemItem) {
                    // 【加入 Debug 提示 2】
                    player.sendSystemMessage(Component.literal("§a[Debug] 服务端：武器校验通过，准备执行 triggerWorldCleave！"));
                    poemItem.triggerWorldCleave(player.serverLevel(), player);
                } else {
                    player.sendSystemMessage(Component.literal("§c[Debug] 服务端错误：你主手里拿的不是镰刀！"));
                }
            }
        });
    }
}