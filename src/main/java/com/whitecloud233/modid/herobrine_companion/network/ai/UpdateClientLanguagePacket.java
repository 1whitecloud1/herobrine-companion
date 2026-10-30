package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import com.whitecloud233.modid.herobrine_companion.util.LanguageCodes;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateClientLanguagePacket {
    private final String languageCode;

    public UpdateClientLanguagePacket(String languageCode) {
        this.languageCode = LanguageCodes.normalize(languageCode);
    }

    public UpdateClientLanguagePacket(FriendlyByteBuf buf) {
        this.languageCode = LanguageCodes.normalize(buf.readUtf(32));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.languageCode, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                HeroCrossChatManager.INSTANCE.updatePlayerLanguage(sender, this.languageCode);
            }
        });
    }

}

