package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public class HeroCrossChatPromptPacket {
    public static final byte KIND_REMOTE_HB_REPLY = 0;
    public static final byte KIND_HB_OPENING = 1;
    public static final byte KIND_HB_REPLY = 2;

    private final UUID jobId;
    private final UUID sessionId;
    private final byte kind;
    private final String prompt;
    private final String seedText;
    private final String outputLanguageCode;

    public HeroCrossChatPromptPacket(UUID jobId, UUID sessionId, byte kind, String prompt, String seedText, String outputLanguageCode) {
        this.jobId = jobId;
        this.sessionId = sessionId;
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NetworkClientBridge.handleHeroCrossChatPrompt(
                this.jobId, this.sessionId, this.kind, this.prompt, this.seedText, this.outputLanguageCode));
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
