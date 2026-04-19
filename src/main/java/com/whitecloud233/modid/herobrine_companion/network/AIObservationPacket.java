package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AIObservationPacket {
    public final int heroId;
    public final String observationDesc;
    public final String fallbackKey;       // 备用翻译键
    public final int fallbackVariants;     // 备用翻译键的随机变体数量

    public AIObservationPacket(int heroId, String observationDesc, String fallbackKey, int fallbackVariants) {
        this.heroId = heroId;
        this.observationDesc = observationDesc;
        this.fallbackKey = fallbackKey;
        this.fallbackVariants = fallbackVariants;
    }

    public AIObservationPacket(FriendlyByteBuf buffer) {
        this.heroId = buffer.readInt();
        this.observationDesc = buffer.readUtf();
        this.fallbackKey = buffer.readUtf();
        this.fallbackVariants = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.heroId);
        buffer.writeUtf(this.observationDesc);
        buffer.writeUtf(this.fallbackKey);
        buffer.writeInt(this.fallbackVariants);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 1. 检查客户端是否配置了有效的 API Key 以及是否开启了视觉
                boolean isAiReady = !LLMConfig.isKeyMissingOrInvalid() && com.whitecloud233.modid.herobrine_companion.config.Config.aiVisionEnabled;

                if (isAiReady) {
                    // 发起大模型请求（消耗当前玩家的 API）
                    AIService.observeEnvironment(this.observationDesc, mc.player.getUUID())
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
        context.setPacketHandled(true);
    }

    // 显示原版备用台词的方法
    private void showFallbackDialogue(net.minecraft.client.player.LocalPlayer player) {
        if (this.fallbackKey == null || this.fallbackKey.isEmpty()) return;

        if (this.fallbackVariants <= 1) {
            player.sendSystemMessage(Component.translatable(this.fallbackKey));
        } else {
            int rand = player.getRandom().nextInt(this.fallbackVariants) + 1;
            player.sendSystemMessage(Component.translatable(this.fallbackKey + "_" + rand));
        }
    }
}