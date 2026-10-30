package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.event.HeroContentLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record HeroContentPacket(byte[] data) {
    public HeroContentPacket { if (data.length > HeroContentLimits.MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Oversized Herobrine content"); data = data.clone(); }
    public HeroContentPacket(FriendlyByteBuf buffer) { this(buffer.readByteArray(HeroContentLimits.MAX_PAYLOAD_BYTES)); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByteArray(data); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.acceptHeroContent(data));
    }
}
