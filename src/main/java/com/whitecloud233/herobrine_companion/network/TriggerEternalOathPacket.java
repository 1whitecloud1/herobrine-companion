package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.gui.EternalOathScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TriggerEternalOathPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TriggerEternalOathPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "trigger_eternal_oath"));

    public static final StreamCodec<ByteBuf, TriggerEternalOathPacket> STREAM_CODEC = StreamCodec.unit(new TriggerEternalOathPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TriggerEternalOathPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                CompoundTag data = player.getPersistentData();
                // 检查客户端临时 NBT 标记，防止同一局游戏内重复触发
                if (!data.getBoolean("HasSeenEternalOath_Client")) {
                    data.putBoolean("HasSeenEternalOath_Client", true);
                    Minecraft.getInstance().setScreen(new EternalOathScreen());
                }
            } else {
                // 兜底逻辑
                Minecraft.getInstance().setScreen(new EternalOathScreen());
            }
        });
    }
}
