package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
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
        context.enqueueWork(() -> handleOnClient(this));
        context.setPacketHandled(true);
    }

    private static void handleOnClient(HeroCrossChatPromptPacket packet) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        UUID scopeId = UUID.nameUUIDFromBytes(("hb-cross-session:" + packet.sessionId).getBytes(StandardCharsets.UTF_8));
        String fallback = buildFallbackReply(packet.seedText, packet.kind, packet.outputLanguageCode);

        if (LLMConfig.isKeyMissingOrInvalid()) {
            PacketHandler.sendToServer(new HeroCrossChatResultPacket(packet.jobId, fallback));
            return;
        }

        AIService.chatForCrossSession(packet.prompt, packet.seedText, scopeId, mc.player.getUUID(), packet.outputLanguageCode)
                .thenApply(reply -> normalizeReply(reply, fallback))
                .exceptionally(ignored -> fallback)
                .thenAccept(reply -> PacketHandler.sendToServer(new HeroCrossChatResultPacket(packet.jobId, reply)));
    }

    private static String normalizeReply(String reply, String fallback) {
        if (reply == null) {
            return fallback;
        }
        String normalized = reply.trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("§c") || lowered.contains("network error") || lowered.contains("api error") || lowered.contains("connection to reality fading")) {
            return fallback;
        }
        return normalized;
    }

    private static String buildFallbackReply(String seedText, byte kind, String outputLanguageCode) {
        try {
            LocalChatService.CachedRule rule = LocalChatService.getInstance().getChatResponse(seedText == null ? "" : seedText);
            if (rule != null && rule.response() != null && !rule.response().isBlank()) {
                return rule.response().trim();
            }
        } catch (Exception ignored) {
        }

        boolean chinese = isChineseLocale(outputLanguageCode);
        return switch (kind) {
            case KIND_HB_OPENING -> chinese ? "……我在听。" : "...I'm listening.";
            case KIND_HB_REPLY -> chinese ? "……那就继续说。" : "...Then keep speaking.";
            default -> chinese ? "……" : "...";
        };
    }

    private static boolean isChineseLocale(String languageCode) {
        return normalizeLanguageCode(languageCode).startsWith("zh");
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}


