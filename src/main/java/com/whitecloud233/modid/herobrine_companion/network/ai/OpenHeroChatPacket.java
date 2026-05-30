package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenHeroChatPacket {
    private final boolean hbInputMode;

    public OpenHeroChatPacket() {
        this(false);
    }

    public OpenHeroChatPacket(boolean hbInputMode) {
        this.hbInputMode = hbInputMode;
    }

    public OpenHeroChatPacket(FriendlyByteBuf buf) {
        this.hbInputMode = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.hbInputMode);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NetworkClientBridge.openHeroChat(this.hbInputMode));
        context.setPacketHandled(true);
    }
}
