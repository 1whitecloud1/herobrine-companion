package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构清单请求（C→S，无负载）：客户端进服后发送，服务器从自身结构注册表收集全部结构 ID，
 * 用 {@link StructureIndexResponsePacket} 回传。与响应包分成两个 payload 类型，
 * 因为 NeoForge 不允许同一 TYPE 同时注册 playToServer 与 playToClient。
 */
public record StructureIndexRequestPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StructureIndexRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "structure_index_request"));

    public static final StreamCodec<ByteBuf, StructureIndexRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new StructureIndexRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructureIndexRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                List<String> ids = new ArrayList<>();
                serverPlayer.server.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
                    for (var value : registry) {
                        ResourceLocation key = registry.getKey(value);
                        if (key != null) {
                            ids.add(key.toString());
                        }
                    }
                });
                com.whitecloud233.herobrine_companion.network.PacketHandler.sendToPlayer(
                        new StructureIndexResponsePacket(ids), serverPlayer);
            }
        });
    }
}
