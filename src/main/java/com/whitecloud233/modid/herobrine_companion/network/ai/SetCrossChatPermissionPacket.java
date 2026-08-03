package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetCrossChatPermissionPacket {
    private final boolean allowIncoming;

    public SetCrossChatPermissionPacket(boolean allowIncoming) {
        this.allowIncoming = allowIncoming;
    }

    public SetCrossChatPermissionPacket(FriendlyByteBuf buf) {
        this.allowIncoming = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.allowIncoming);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.setAllowIncomingRequests(sender, this.allowIncoming);
            }
        });
    }
}

