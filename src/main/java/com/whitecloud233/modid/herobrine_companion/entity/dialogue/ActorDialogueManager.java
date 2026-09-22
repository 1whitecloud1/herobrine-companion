package com.whitecloud233.modid.herobrine_companion.entity.dialogue;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialoguePromptPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ActorDialogueManager {
    public static final ActorDialogueManager INSTANCE = new ActorDialogueManager();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double GENERATOR_RANGE = 32.0D;
    private static final int MIN_SPEECH_TICKS = 60;
    private static final int MAX_SPEECH_TICKS = 120;
    private static final int MAX_TEXT_LENGTH = 160;
    private static final long JOB_TIMEOUT_MS = 60_000L;

    /** 觉醒对话的兜底全局最小间隔（毫秒）；实际值来自 Config.awakenedDialogueMinIntervalSeconds。 */
    private static final long FALLBACK_MIN_DIALOGUE_INTERVAL_MS = 30_000L;

    private final Map<UUID, PendingJob> jobsById = new HashMap<>();
    /** 每个通道各自的上一次 LLM 对话时刻（毫秒）——通道之间互不挤占。 */
    private final Map<DialogueChannel, Long> lastDialogueAtByChannel = new EnumMap<>(DialogueChannel.class);

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

        // 分通道频率闸:赠礼通道独立计时(默认不限制),因此不会被觉醒对话挤掉。
        // 冷却期间改用预设台词（不调 API、不触发 followUp 链）。
        long now = System.currentTimeMillis();
        long minIntervalMs = minDialogueIntervalMs(spec.channel());
        if (minIntervalMs > 0) {
            long lastAt = lastDialogueAtByChannel.getOrDefault(spec.channel(), Long.MIN_VALUE);
            if (now - lastAt < minIntervalMs) {
                LOGGER.debug("[ActorDialogue] {} throttled ({}ms < {}ms), local line used",
                        spec.channel(), now - lastAt, minIntervalMs);
                deliverLine(spec.speaker(), fallbackText);
                return;
            }
        }
        lastDialogueAtByChannel.put(spec.channel(), now);

        ServerPlayer generator = selectGenerator(serverLevel, spec.speaker(), spec.preferredAudience());
        if (generator == null) {
            LOGGER.debug("[ActorDialogue] {} has no generator, local line used", spec.channel());
            deliverLine(spec.speaker(), fallbackText);
            triggerFollowUp(spec, fallbackText);
            return;
        }

        String outputLanguageCode = resolveLanguageCode(generator);
        UUID jobId = UUID.randomUUID();
        jobsById.put(jobId, new PendingJob(jobId, generator.getUUID(), spec, now));
        LOGGER.debug("[ActorDialogue] {} LLM request sent (job={} generator={})",
                spec.channel(), jobId, generator.getName().getString());
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

    /**
     * 通道对应的最小间隔(毫秒);返回 0 表示该通道不做限流。
     *
     * <p>觉醒通道保持原语义(配置非法时回退 30 秒);赠礼通道默认 0 = 完全不限制。
     */
    private long minDialogueIntervalMs(DialogueChannel channel) {
        int seconds = switch (channel) {
            case GIFT -> com.whitecloud233.modid.herobrine_companion.config.Config.giftDialogueMinIntervalSeconds;
            case AWAKENED -> {
                int configured = com.whitecloud233.modid.herobrine_companion.config.Config.awakenedDialogueMinIntervalSeconds;
                yield configured <= 0 ? (int) (FALLBACK_MIN_DIALOGUE_INTERVAL_MS / 1000L) : configured;
            }
        };
        return seconds <= 0 ? 0L : seconds * 1000L;
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
