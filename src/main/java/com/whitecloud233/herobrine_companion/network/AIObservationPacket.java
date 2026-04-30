package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.service.AIService;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.config.Config; // 请根据实际 Config 导入路径确认
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AIObservationPacket(int heroId, String observationDesc, String fallbackKey, int fallbackVariants) implements CustomPacketPayload {

    // 定义数据包的唯一类型 ID
    public static final Type<AIObservationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "ai_observation"));

    // 组合式流编解码器
    public static final StreamCodec<ByteBuf, AIObservationPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, AIObservationPacket::heroId,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::observationDesc,
            ByteBufCodecs.STRING_UTF8, AIObservationPacket::fallbackKey,
            ByteBufCodecs.INT, AIObservationPacket::fallbackVariants,
            AIObservationPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // NeoForge 1.21.1 处理方法 (客户端执行)
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 1. 检查客户端是否配置了有效的 API Key 以及是否开启了视觉
                // 注意：由于旧代码中有 LLMConfig，你需要确保它的引用有效，这里沿用你的逻辑
                boolean isAiReady = !LLMConfig.isKeyMissingOrInvalid() && Config.aiVisionEnabled;

                if (isAiReady) {
                    // 发起大模型请求（消耗当前玩家的 API）
                    AIService.observeEnvironment(this.observationDesc(), mc.player.getUUID())
                            .thenAccept(reply -> {
                                mc.tell(() -> {
                                    if (reply != null && !reply.isEmpty() && !reply.startsWith("§c")) {
                                        // 成功获取 AI 回复
                                        mc.player.sendSystemMessage(Component.literal("§e<Herobrine> §f" + reply));
                                    } else {
                                        // AI 请求失败、报错或被拦截，执行原版台词保底
                                        showFallbackDialogue(mc.player);
                                    }
                                });
                            });
                } else {
                    // 玩家没填 Key 或没开视觉，直接显示原版台词保底
                    showFallbackDialogue(mc.player);
                }
            }
        });
    }

    // 显示原版备用台词的方法
    private void showFallbackDialogue(net.minecraft.client.player.LocalPlayer player) {
        if (this.fallbackKey() == null || this.fallbackKey().isEmpty()) return;

        if (this.fallbackVariants() <= 1) {
            player.sendSystemMessage(Component.translatable(this.fallbackKey()));
        } else {
            int rand = player.getRandom().nextInt(this.fallbackVariants()) + 1;
            // 👇 修改为下划线 "_"
            player.sendSystemMessage(Component.translatable(this.fallbackKey() + "_" + rand));
        }
    }
}