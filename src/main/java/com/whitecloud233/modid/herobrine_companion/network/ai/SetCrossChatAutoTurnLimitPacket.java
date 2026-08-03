package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetCrossChatAutoTurnLimitPacket {
    private final int turnLimit;

    public SetCrossChatAutoTurnLimitPacket(int turnLimit) {
        this.turnLimit = turnLimit;
    }

    public SetCrossChatAutoTurnLimitPacket(FriendlyByteBuf buf) {
        this.turnLimit = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.turnLimit);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.setAutoHbTurnLimit(sender, this.turnLimit);
            }
        });
    }
}

