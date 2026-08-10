package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

public class RequestCrossChatSessionPacket {
    private final String targetName;

    public RequestCrossChatSessionPacket(String targetName) {
        this.targetName = targetName == null ? "" : targetName;
    }

    public RequestCrossChatSessionPacket(FriendlyByteBuf buf) {
        this.targetName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.targetName, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }

            String requestedName = this.targetName == null ? "" : this.targetName.trim();
            if (requestedName.isEmpty()) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.herobrine_companion.cross_chat.target_name_empty"));
                return;
            }

            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(requestedName);
            if (target == null) {
                String lower = requestedName.toLowerCase(Locale.ROOT);
                for (Player player : sender.server.getPlayerList().getPlayers()) {
                    if (player.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(lower) && player instanceof ServerPlayer serverPlayer) {
                        target = serverPlayer;
                        break;
                    }
                }
            }

            if (target == null) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.herobrine_companion.cross_chat.target_not_found", requestedName));
                return;
            }

            HeroCrossChatManager.INSTANCE.requestSession(sender, target);
        });
    }
}

