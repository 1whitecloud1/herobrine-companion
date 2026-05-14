package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Locale;
public class UpdateClientLanguagePacket implements CustomPacketPayload {
    public static final Type<UpdateClientLanguagePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "update_client_language"));
    public static final StreamCodec<FriendlyByteBuf, UpdateClientLanguagePacket> STREAM_CODEC = StreamCodec.ofMember(UpdateClientLanguagePacket::encode, UpdateClientLanguagePacket::new);
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
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(UpdateClientLanguagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                HeroCrossChatManager.INSTANCE.updatePlayerLanguage(sender, packet.languageCode);
            }
        });
    }
    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}
