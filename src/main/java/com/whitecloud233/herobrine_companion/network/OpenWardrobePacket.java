package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.inventory.HeroWardrobeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenWardrobePacket(int entityId) implements CustomPacketPayload {

    public static final Type<OpenWardrobePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_wardrobe"));

    public static final StreamCodec<ByteBuf, OpenWardrobePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OpenWardrobePacket::entityId,
            OpenWardrobePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWardrobePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // 在服务端获取对应的实体
                Entity entity = player.level().getEntity(packet.entityId());
                if (entity instanceof HeroEntity hero) {
                    // 1.21.1: 移除 NetworkHooks，原版 ServerPlayer 原生支持携带 Buf 打开菜单
                    player.openMenu(
                            new SimpleMenuProvider(
                                    (containerId, playerInv, p) -> new HeroWardrobeMenu(containerId, playerInv, hero),
                                    Component.translatable("gui.herobrine_companion.wardrobe")
                            ),
                            buf -> buf.writeInt(hero.getId()) // 附带实体 ID 给客户端 Screen 构造器
                    );
                }
            }
        });
    }
}