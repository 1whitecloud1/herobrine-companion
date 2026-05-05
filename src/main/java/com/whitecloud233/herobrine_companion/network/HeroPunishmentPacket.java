package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class HeroPunishmentPacket implements CustomPacketPayload {
    public static final Type<HeroPunishmentPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_punishment"));

    public static final StreamCodec<FriendlyByteBuf, HeroPunishmentPacket> STREAM_CODEC =
            StreamCodec.ofMember(HeroPunishmentPacket::encode, HeroPunishmentPacket::new);

    public static final String ACTION_KILL_PLAYER = "action:punishment_kill_player";
    public static final String ACTION_KICK_PLAYER = "action:punishment_kick_player";

    private final String action;

    public HeroPunishmentPacket(String action) {
        this.action = action;
    }

    public HeroPunishmentPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.action, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeroPunishmentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (packet.action) {
                    case ACTION_KILL_PLAYER -> {
                        if (player.isAlive()) {
                            player.kill();
                        }
                    }
                    case ACTION_KICK_PLAYER -> player.connection.disconnect(Component.literal("Herobrine has cast you out."));
                    default -> {
                    }
                }
            }
        });
    }
}


