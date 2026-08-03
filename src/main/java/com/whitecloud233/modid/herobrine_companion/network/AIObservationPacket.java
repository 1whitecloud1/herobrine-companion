package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AIObservationPacket {
    public final int heroId;
    public final String observationDesc;
    public final String fallbackKey;
    public final int fallbackVariants;
    public final String contextTranslationKey;
    public final String contextFallbackName;

    public AIObservationPacket(int heroId, String observationDesc, String fallbackKey, int fallbackVariants) {
        this(heroId, observationDesc, fallbackKey, fallbackVariants, "", "");
    }

    public AIObservationPacket(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                               String contextTranslationKey, String contextFallbackName) {
        this.heroId = heroId;
        this.observationDesc = observationDesc == null ? "" : observationDesc;
        this.fallbackKey = fallbackKey == null ? "" : fallbackKey;
        this.fallbackVariants = fallbackVariants;
        this.contextTranslationKey = contextTranslationKey == null ? "" : contextTranslationKey;
        this.contextFallbackName = contextFallbackName == null ? "" : contextFallbackName;
    }

    public AIObservationPacket(FriendlyByteBuf buffer) {
        this.heroId = buffer.readInt();
        this.observationDesc = buffer.readUtf();
        this.fallbackKey = buffer.readUtf();
        this.fallbackVariants = buffer.readInt();
        this.contextTranslationKey = buffer.readUtf();
        this.contextFallbackName = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.heroId);
        buffer.writeUtf(this.observationDesc);
        buffer.writeUtf(this.fallbackKey);
        buffer.writeInt(this.fallbackVariants);
        buffer.writeUtf(this.contextTranslationKey);
        buffer.writeUtf(this.contextFallbackName);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.handleAIObservation(
                this.heroId, this.observationDesc, this.fallbackKey, this.fallbackVariants,
                this.contextTranslationKey, this.contextFallbackName));
        context.setPacketHandled(true);
    }
}
