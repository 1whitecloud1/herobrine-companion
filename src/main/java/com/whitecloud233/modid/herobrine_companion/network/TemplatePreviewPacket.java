package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 建筑预览线框（服务端 → 客户端）。
 *
 * <p><b>为什么只有 6 个 int</b>：预览只需要"多大、在哪"，用包围盒的两个角点即可表达。
 * 463×141×323 的建筑有 143 万方块，把方块逐个发过去是几十 MB；
 * 这个包固定 <b>28 字节</b>，与建筑体积无关。</p>
 *
 * <p>客户端落地经 {@link NetworkClientBridge}（唯一 DistExecutor 边界）。</p>
 */
public class TemplatePreviewPacket {

    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final String label;

    public TemplatePreviewPacket(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String label) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.label = label == null ? "" : label;
    }

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
        buffer.writeUtf(label, 80);
    }

    /** 全 0 且无标签表示清空预览。 */
    public boolean isClear() {
        return minX == 0 && minY == 0 && minZ == 0 && maxX == 0 && maxY == 0 && maxZ == 0;
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> {
            if (isClear()) {
                NetworkClientBridge.clearTemplatePreview();
            } else {
                NetworkClientBridge.acceptTemplatePreview(minX, minY, minZ, maxX, maxY, maxZ, label);
            }
        });
    }
}
