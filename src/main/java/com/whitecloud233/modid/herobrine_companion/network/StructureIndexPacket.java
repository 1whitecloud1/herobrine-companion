package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.service.ModContentIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 结构清单同步包：1.20.1 客户端不同步 worldgen 注册表（结构是服务端权威数据），
 * 所以客户端在进服后发一个 request，服务器从自身 {@code registryAccess()} 的结构注册表
 * 收集全部结构 ID 回传，客户端回填本地索引 {@link ModContentIndex}（category=structure）。
 *
 * <p>单机同样走此包（内存连接），统一一条链路。request=true 为 C→S 请求（无负载），
 * request=false 为 S→C 响应（负载为结构 ID 列表）。</p>
 */
public class StructureIndexPacket {
    private final boolean request;
    private final List<String> structureIds;

    public StructureIndexPacket(boolean request) {
        this.request = request;
        this.structureIds = List.of();
    }

    public StructureIndexPacket(List<String> structureIds) {
        this.request = false;
        this.structureIds = structureIds;
    }

    public StructureIndexPacket(FriendlyByteBuf buf) {
        this.request = buf.readBoolean();
        this.structureIds = buf.readList(FriendlyByteBuf::readUtf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.request);
        buf.writeCollection(this.structureIds, FriendlyByteBuf::writeUtf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (PacketDispatch.isServer(context)) {
            PacketDispatch.enqueueServer(context, () -> {
                ServerPlayer serverPlayer = context.getSender();
                if (serverPlayer == null || !this.request) {
                    return;
                }
                List<String> ids = new ArrayList<>();
                serverPlayer.server.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
                    for (var value : registry) {
                        ResourceLocation key = registry.getKey(value);
                        if (key != null) {
                            ids.add(key.toString());
                        }
                    }
                });
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new StructureIndexPacket(ids));
            });
        } else if (PacketDispatch.isClient(context)) {
            context.enqueueWork(() -> {
                if (!this.request) {
                    ModContentIndex.ingestStructures(this.structureIds);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
