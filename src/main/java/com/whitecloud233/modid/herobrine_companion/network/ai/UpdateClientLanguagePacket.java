package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

public class UpdateClientLanguagePacket {
    private final String languageCode;

    public UpdateClientLanguagePacket(String languageCode) {
        this.languageCode = normalizeLanguageCode(languageCode);
    }

    public UpdateClientLanguagePacket(FriendlyByteBuf buf) {
        this.languageCode = normalizeLanguageCode(buf.readUtf(32));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.languageCode, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.updatePlayerLanguage(sender, this.languageCode);
            }
        });
        context.setPacketHandled(true);
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}

