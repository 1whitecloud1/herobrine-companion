package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetCrossChatAutoChatPacket {
    private final boolean enabled;

    public SetCrossChatAutoChatPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public SetCrossChatAutoChatPacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.setAutoHbConversation(sender, this.enabled);
            }
        });
        context.setPacketHandled(true);
    }
}

