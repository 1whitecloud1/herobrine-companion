package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.ModContentIndex;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 结构清单响应（S→C）：服务器回传全部结构 ID，客户端回填本地索引
 * {@link ModContentIndex}（category=structure）。与请求包分成两个 payload 类型，
 * 因为 NeoForge 不允许同一 TYPE 同时注册 playToServer 与 playToClient。
 */
public record StructureIndexResponsePacket(List<String> structureIds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StructureIndexResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "structure_index_response"));

    public static final StreamCodec<ByteBuf, StructureIndexResponsePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
            StructureIndexResponsePacket::structureIds,
            StructureIndexResponsePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructureIndexResponsePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ModContentIndex.ingestStructures(payload.structureIds());
        });
    }
}
