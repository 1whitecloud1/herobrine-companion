package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SendCrossChatMessagePacket {
    private final String message;

    public SendCrossChatMessagePacket(String message) {
        this.message = message == null ? "" : message;
    }

    public SendCrossChatMessagePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf(512);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message, 512);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.sendPlayerMessage(sender, this.message);
            }
        });
        context.setPacketHandled(true);
    }
}

