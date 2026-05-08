package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class HeroPunishmentPacket {
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

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            switch (this.action) {
                case ACTION_KILL_PLAYER -> {
                    if (player.isAlive()) {
                        player.kill();
                    }
                }
                case ACTION_KICK_PLAYER -> player.connection.disconnect(Component.literal("Herobrine has cast you out."));
                default -> {
                }
            }
        });
        context.setPacketHandled(true);
    }
}


