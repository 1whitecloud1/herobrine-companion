package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAiPrompts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.util.UUID;

public class HeroCrossChatPromptPacket implements CustomPacketPayload {
    public static final byte KIND_REMOTE_HB_REPLY = 0;
    public static final byte KIND_HB_OPENING = 1;
    public static final byte KIND_HB_REPLY = 2;

    public static final Type<HeroCrossChatPromptPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_cross_chat_prompt"));
    public static final StreamCodec<FriendlyByteBuf, HeroCrossChatPromptPacket> STREAM_CODEC = StreamCodec.ofMember(HeroCrossChatPromptPacket::encode, HeroCrossChatPromptPacket::new);

    private final UUID jobId;
    private final UUID sessionId;
    private final byte kind;
    private final String prompt;
    private final String seedText;
    private final String outputLanguageCode;

    public HeroCrossChatPromptPacket(UUID jobId, UUID sessionId, byte kind, String prompt, String seedText, String outputLanguageCode) {
        this.jobId = jobId == null ? new UUID(0L, 0L) : jobId;
        this.sessionId = sessionId == null ? new UUID(0L, 0L) : sessionId;
        this.kind = kind;
        this.prompt = prompt == null ? "" : prompt;
        this.seedText = seedText == null ? "" : seedText;
        this.outputLanguageCode = normalizeLanguageCode(outputLanguageCode);
    }

    public HeroCrossChatPromptPacket(FriendlyByteBuf buf) {
        this.jobId = buf.readUUID();
        this.sessionId = buf.readUUID();
        this.kind = buf.readByte();
        this.prompt = buf.readUtf(16384);
        this.seedText = buf.readUtf(2048);
        this.outputLanguageCode = normalizeLanguageCode(buf.readUtf(32));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.jobId);
        buf.writeUUID(this.sessionId);
        buf.writeByte(this.kind);
        buf.writeUtf(this.prompt, 16384);
        buf.writeUtf(this.seedText, 2048);
        buf.writeUtf(this.outputLanguageCode, 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeroCrossChatPromptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAiPrompts.handleHeroCrossChatPrompt(
                packet.jobId, packet.sessionId, packet.kind, packet.prompt, packet.seedText, packet.outputLanguageCode));
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}
