package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.guide.CompanionGuideService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client acknowledgement that an interaction successfully opened the Herobrine panel. */
public record CompanionGuideOpenedPacket(int heroId) {
    public CompanionGuideOpenedPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(heroId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueServer(context,
                () -> CompanionGuideService.grantOnFirstOpen(context.getSender(), heroId));
    }
}
