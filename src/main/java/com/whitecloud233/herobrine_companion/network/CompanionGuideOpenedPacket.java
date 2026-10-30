package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.guide.CompanionGuideService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端回执：与 Herobrine 的交互确实把主页面打开成功了。
 *
 * <p>1.20.1 Forge 版是 {@code FriendlyByteBuf} 手写读写 + {@code SimpleChannel}；
 * 1.21.1 NeoForge 统一成 {@link CustomPacketPayload} + {@link StreamCodec}，
 * 主通道协议号仍为 3。</p>
 */
public record CompanionGuideOpenedPacket(int heroId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CompanionGuideOpenedPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "companion_guide_opened"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionGuideOpenedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CompanionGuideOpenedPacket::heroId,
                    CompanionGuideOpenedPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompanionGuideOpenedPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                CompanionGuideService.grantOnFirstOpen(serverPlayer, payload.heroId());
            }
        });
    }
}
