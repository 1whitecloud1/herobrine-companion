package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 建筑预览线框（服务端 → 客户端）。
 *
 * <p><b>为什么只有 6 个 int</b>：预览只需要"多大、在哪"，用包围盒的两个角点即可表达。
 * 463×141×323 的建筑有 143 万方块，把方块逐个发过去是几十 MB；
 * 这个包固定约 <b>28 字节</b>，与建筑体积无关。</p>
 *
 * <p>客户端落地沿用本版本既有的载荷分发约定（{@code FMLEnvironment.dist} 判定后
 * 调用 {@code client} 包下的处理器），与 {@link TemplatePlacementReply} 一致。</p>
 */
public record TemplatePreviewPacket(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String label)
        implements CustomPacketPayload {

    public static final Type<TemplatePreviewPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "template_preview"));

    public static final StreamCodec<FriendlyByteBuf, TemplatePreviewPacket> STREAM_CODEC =
            StreamCodec.ofMember(TemplatePreviewPacket::encode, TemplatePreviewPacket::new);

    public TemplatePreviewPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(80));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(minX);
        buffer.writeVarInt(minY);
        buffer.writeVarInt(minZ);
        buffer.writeVarInt(maxX);
        buffer.writeVarInt(maxY);
        buffer.writeVarInt(maxZ);
        buffer.writeUtf(label == null ? "" : label, 80);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 全 0 且无标签表示清空预览。 */
    public boolean isClear() {
        return minX == 0 && minY == 0 && minZ == 0 && maxX == 0 && maxY == 0 && maxZ == 0;
    }

    public static void handle(TemplatePreviewPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            com.whitecloud233.herobrine_companion.client.structure.TemplatePreviewBox.accept(
                    payload.minX(), payload.minY(), payload.minZ(),
                    payload.maxX(), payload.maxY(), payload.maxZ(), payload.label());
        });
    }
}
