package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RespondCrossChatInvitePacket {
    private final UUID requesterId;
    private final boolean accept;

    public RespondCrossChatInvitePacket(UUID requesterId, boolean accept) {
        this.requesterId = requesterId;
        this.accept = accept;
    }

    public RespondCrossChatInvitePacket(FriendlyByteBuf buf) {
        this.requesterId = buf.readUUID();
        this.accept = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requesterId);
        buf.writeBoolean(this.accept);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer target = context.getSender();
            if (target == null || target.server == null) {
                return;
            }
            ServerPlayer requester = target.server.getPlayerList().getPlayer(this.requesterId);
            if (requester == null) {
                target.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[跨HB] §f发起请求的玩家已经离线。"));
                return;
            }
            HeroCrossChatManager.INSTANCE.respondToRequest(target, requester, this.accept);
        });
        context.setPacketHandled(true);
    }
}

