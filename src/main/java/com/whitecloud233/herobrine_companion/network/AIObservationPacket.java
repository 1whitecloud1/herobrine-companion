package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.AIService;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AIObservationPacket(int heroId, String observationDesc) implements CustomPacketPayload {

    // 1. 定义包的唯一标识符
    public static final Type<AIObservationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "ai_observation"));

    // 2. 定义序列化与反序列化规则
    public static final StreamCodec<ByteBuf, AIObservationPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, AIObservationPacket::heroId,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::observationDesc,
            AIObservationPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 3. 【核心修复】：使用 1.21.1 的 IPayloadContext 和静态 handle 方法
    public static void handle(final AIObservationPacket data, final IPayloadContext context) {
        // enqueueWork 确保任务在客户端主线程执行
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 【调试代码】：如果能在游戏里看到这条灰字，说明服务端成功看到了你的行为，数据包也成功发到了客户端！
                //mc.player.sendSystemMessage(Component.literal("§7[Debug] 客户端已收到观察事件，正在呼叫大模型..."));

                // 注意：这里使用 data.observationDesc() 来获取 record 中的数据
                AIService.observeEnvironment(data.observationDesc(), mc.player.getUUID())
                        .thenAccept(reply -> {
                            if (reply != null && !reply.isEmpty() && !reply.startsWith("§c")) {
                                mc.tell(() -> {
                                    mc.player.sendSystemMessage(Component.literal("§e<Herobrine> §f" + reply));
                                });
                            } else {
                                // 【调试代码】：如果大模型返回了空或者报错，打印出来
                                mc.tell(() -> {
                                    mc.player.sendSystemMessage(Component.literal("§c[Debug] 大模型返回了空数据或错误拦截！"));
                                });
                            }
                        });
            }
        });
    }
}