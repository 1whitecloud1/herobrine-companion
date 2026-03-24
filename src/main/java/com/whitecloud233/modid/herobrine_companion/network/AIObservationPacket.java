package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AIObservationPacket {
    public final int heroId;
    public final String observationDesc;

    // 1. 常规构造函数 (发包时使用)
    public AIObservationPacket(int heroId, String observationDesc) {
        this.heroId = heroId;
        this.observationDesc = observationDesc;
    }

    // 2. 解码构造函数 (收包时使用)
    public AIObservationPacket(FriendlyByteBuf buffer) {
        this.heroId = buffer.readInt();
        this.observationDesc = buffer.readUtf();
    }

    // 3. 编码方法
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.heroId);
        buffer.writeUtf(this.observationDesc);
    }

    // 4. 处理逻辑
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 【调试代码】：如果能在游戏里看到这条灰字，说明服务端成功看到了你的行为，数据包也成功发到了客户端！
                // mc.player.sendSystemMessage(Component.literal("§7[Debug] 客户端已收到观察事件，正在呼叫大模型..."));

                AIService.observeEnvironment(this.observationDesc, mc.player.getUUID())
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
        context.setPacketHandled(true);
    }
}