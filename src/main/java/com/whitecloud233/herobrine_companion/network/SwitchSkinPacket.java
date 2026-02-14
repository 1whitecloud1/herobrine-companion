package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwitchSkinPacket(int entityId, int skinVariant) implements CustomPacketPayload {
    public static final Type<SwitchSkinPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "switch_skin"));

    public static final StreamCodec<ByteBuf, SwitchSkinPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SwitchSkinPacket::entityId,
            ByteBufCodecs.INT, SwitchSkinPacket::skinVariant,
            SwitchSkinPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchSkinPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(packet.entityId());
                if (entity instanceof HeroEntity hero) {
                    hero.setSkinVariant(packet.skinVariant());
                }
            }
        });
    }
}