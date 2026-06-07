package com.whitecloud233.herobrine_companion.entity.dialogue;

import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.ActorDialoguePromptPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ActorDialogueManager {
    public static final ActorDialogueManager INSTANCE = new ActorDialogueManager();

    private static final double GENERATOR_RANGE = 32.0D;
    private static final int MIN_SPEECH_TICKS = 60;
    private static final int MAX_SPEECH_TICKS = 120;
    private static final int MAX_TEXT_LENGTH = 160;
    private static final long JOB_TIMEOUT_MS = 60_000L;

    private final Map<UUID, PendingJob> jobsById = new HashMap<>();

    private ActorDialogueManager() {
    }

    public synchronized void requestDialogue(ActorDialogueSpec spec) {
        pruneExpiredJobs();
        if (spec == null || spec.speaker() == null || spec.persona() == null || spec.speaker().level().isClientSide) {
            return;
        }
        if (!(spec.speaker().level() instanceof ServerLevel serverLevel) || !spec.speaker().isAlive()) {
            return;
        }

        String fallbackText = buildFallbackText(spec);
        ServerPlayer generator = selectGenerator(serverLevel, spec.speaker(), spec.preferredAudience());
        if (generator == null) {
            deliverLine(spec.speaker(), fallbackText);
            triggerFollowUp(spec, fallbackText);
            return;
        }

        String outputLanguageCode = resolveLanguageCode(generator);
        UUID jobId = UUID.randomUUID();
        jobsById.put(jobId, new PendingJob(jobId, generator.getUUID(), spec, System.currentTimeMillis()));
        PacketHandler.sendToPlayer(new ActorDialoguePromptPacket(
                jobId,
                spec.conversationScopeId(),
                spec.persona().buildSystemPrompt(outputLanguageCode),
                spec.userPrompt(),
                spec.seedText().isBlank() ? fallbackText : spec.seedText(),
                spec.fallbackKey(),
                spec.fallbackArgs(),
                outputLanguageCode
        ), generator);
    }

    public synchronized void handleGeneratedReply(ServerPlayer generator, UUID jobId, String rawReply) {
        pruneExpiredJobs();
        if (generator == null || jobId == null) {
            return;
        }

        PendingJob job = jobsById.remove(jobId);
        if (job == null || job.isExpired()) {
            return;
        }
        if (!job.generatorId().equals(generator.getUUID())) {
            return;
        }

        String fallbackText = buildFallbackText(job.spec());
        String reply = sanitizeReply(rawReply, fallbackText);
        deliverLine(job.spec().speaker(), reply);
        triggerFollowUp(job.spec(), reply);
    }

    public static UUID createScopeId(String seed) {
        String normalized = seed == null ? "" : seed.trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private void triggerFollowUp(ActorDialogueSpec spec, String previousReply) {
        if (spec.followUp() == null) {
            return;
        }
        ActorDialogueSpec next = spec.followUp().create(previousReply);
        if (next != null) {
            requestDialogue(next);
        }
    }

    private void deliverLine(LivingEntity speaker, String reply) {
        if (!(speaker instanceof SpeechBubbleAccessor accessor)) {
            return;
        }
        Component component = Component.literal(sanitizeReply(reply, "..."));
        accessor.herobrineCompanion$showSpeechBubble(component, computeSpeechDuration(component.getString()));
    }

    private int computeSpeechDuration(String text) {
        int duration = MIN_SPEECH_TICKS + (text == null ? 0 : text.length() * 2);
        return Math.max(MIN_SPEECH_TICKS, Math.min(MAX_SPEECH_TICKS, duration));
    }

    private String buildFallbackText(ActorDialogueSpec spec) {
        if (spec == null) {
            return "...";
        }
        if (!spec.fallbackKey().isBlank()) {
            Object[] args = spec.fallbackArgs().toArray(new Object[0]);
            String resolved = Component.translatable(spec.fallbackKey(), args).getString();
            if (!resolved.isBlank()) {
                return sanitizeReply(resolved, "...");
            }
        }
        return sanitizeReply(spec.seedText(), "...");
    }

    private String sanitizeReply(String rawReply, String fallback) {
        String sanitized = rawReply == null ? "" : rawReply
                .replaceAll("<[^>]*>", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        if (sanitized.isEmpty()) {
            sanitized = fallback == null || fallback.isBlank() ? "..." : fallback.trim();
        }
        if (sanitized.length() > MAX_TEXT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TEXT_LENGTH - 3).trim() + "...";
        }
        return sanitized;
    }

    private ServerPlayer selectGenerator(ServerLevel level, LivingEntity speaker, ServerPlayer preferredAudience) {
        if (preferredAudience != null) {
            if (preferredAudience.serverLevel().getServer() == level.getServer()
                    && !preferredAudience.isChangingDimension()
                    && !preferredAudience.isRemoved()) {
                return preferredAudience;
            }
            return null;
        }

        List<ServerPlayer> candidates = level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(GENERATOR_RANGE),
                player -> player.isAlive() && !player.isSpectator() && !player.isChangingDimension());
        return candidates.stream()
                .min(Comparator.comparingDouble(speaker::distanceToSqr))
                .orElse(null);
    }

    private String resolveLanguageCode(ServerPlayer player) {
        if (player == null) {
            return "en_us";
        }
        return HeroWorldData.get(player.serverLevel()).getClientLanguageCode(player.getUUID());
    }

    private void pruneExpiredJobs() {
        long now = System.currentTimeMillis();
        jobsById.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > JOB_TIMEOUT_MS);
    }

    private record PendingJob(UUID jobId, UUID generatorId, ActorDialogueSpec spec, long createdAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > JOB_TIMEOUT_MS;
        }
    }
}
