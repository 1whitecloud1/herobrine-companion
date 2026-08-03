package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.service.ActorDialogueRequest;
import com.whitecloud233.modid.herobrine_companion.client.service.ActorDialogueService;
import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.CrossChatHistoryStore;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialogueResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatResultPacket;
import com.whitecloud233.modid.herobrine_companion.util.LegacyFormattingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收包后触发客户端 LLM 编排的处理入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发。
 *
 * <p>LLM 的 API Key 只存在客户端配置里,所以"服务器发 prompt 包 → 客户端调用 LLM →
 * 结果包回发服务器"这条回环是架构使然,不能搬去服务端。
 */
public final class ClientAiPrompts {
    private static final Map<String, Long> RECENT_OBSERVATIONS = new ConcurrentHashMap<>();
    private static final long DUPLICATE_SUPPRESS_WINDOW_MS = 5_000L;
    private static final String AUTONOMOUS_REST_FALLBACK_KEY = "message.herobrine_companion.autonomous_rest";
    private static final String AUTONOMOUS_COOK_FALLBACK_KEY = "message.herobrine_companion.autonomous_cook";
    private static final int MIN_BUBBLE_TICKS = 45;
    private static final int MAX_BUBBLE_TICKS = 180;

    private ClientAiPrompts() {
    }

    public static void handleAIObservation(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                                           String contextTranslationKey, String contextFallbackName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (shouldSuppressDuplicate(mc.player.getUUID(), heroId, observationDesc)) {
            return;
        }

        boolean domesticLine = isDomesticFallbackKey(fallbackKey);
        String resolvedContextName = resolveContextName(contextTranslationKey, contextFallbackName);
        String effectiveObservation = buildObservationDescription(fallbackKey, observationDesc, resolvedContextName);
        boolean isAiReady = !LLMConfig.isKeyMissingOrInvalid() && Config.aiVisionEnabled;
        if (isAiReady) {
            AIService.observeEnvironment(effectiveObservation, mc.player.getUUID())
                    .thenAccept(reply -> mc.tell(() -> {
                        if (isUsableAiReply(reply)) {
                            Component line = Component.literal(sanitize(reply));
                            if (domesticLine) {
                                showHeroDialogue(mc, heroId, line, true);
                            } else {
                                mc.player.sendSystemMessage(Component.literal(LegacyFormattingText.normalize("§e<Herobrine> §f" + reply)));
                            }
                        } else {
                            showFallbackDialogue(mc, heroId, fallbackKey, fallbackVariants, resolvedContextName);
                        }
                    }));
            return;
        }

        showFallbackDialogue(mc, heroId, fallbackKey, fallbackVariants, resolvedContextName);
    }

    public static void presentCrossChatAiLine(String peerName, boolean hbMode, String speaker, String content,
                                              String kind, byte displayType, String primaryName,
                                              String secondaryName, boolean translateForViewer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        CompletableFuture<String> localizedFuture = translateForViewer
                ? AIService.localizeText(content, mc.options.languageCode, mc.player.getUUID())
                : CompletableFuture.completedFuture(content);

        localizedFuture
                .exceptionally(ignored -> content)
                .thenAccept(localizedContent -> mc.tell(() ->
                        applyPresentedLine(mc, peerName, hbMode, speaker, content, kind, displayType, primaryName, secondaryName, localizedContent)));
    }

    public static void handleHeroCrossChatPrompt(UUID jobId, UUID sessionId, byte kind, String prompt,
                                                 String seedText, String outputLanguageCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        UUID scopeId = UUID.nameUUIDFromBytes(("hb-cross-session:" + sessionId).getBytes(StandardCharsets.UTF_8));
        String fallback = buildFallbackReply(seedText, kind, outputLanguageCode);

        if (LLMConfig.isKeyMissingOrInvalid()) {
            PacketHandler.sendToServer(new HeroCrossChatResultPacket(jobId, fallback));
            return;
        }

        AIService.chatForCrossSession(prompt, seedText, scopeId, mc.player.getUUID(), outputLanguageCode)
                .thenApply(reply -> normalizeReply(reply, fallback))
                .exceptionally(ignored -> fallback)
                .thenAccept(reply -> PacketHandler.sendToServer(new HeroCrossChatResultPacket(jobId, reply)));
    }

    public static void handleActorDialoguePrompt(UUID jobId, UUID conversationScopeId, String systemPrompt,
                                                 String userPrompt, String seedText, String fallbackKey,
                                                 List<String> fallbackArgs, String outputLanguageCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        String fallback = buildActorFallbackReply(seedText, fallbackKey, fallbackArgs);
        if (LLMConfig.isSetupIncomplete() || LLMConfig.isKeyMissingOrInvalid()) {
            PacketHandler.sendToServer(new ActorDialogueResultPacket(jobId, fallback));
            return;
        }

        ActorDialogueService.generateLine(new ActorDialogueRequest(
                        systemPrompt,
                        userPrompt,
                        fallback,
                        conversationScopeId,
                        mc.player.getUUID(),
                        outputLanguageCode
                ))
                .thenApply(reply -> normalizeReply(reply, fallback))
                .exceptionally(ignored -> fallback)
                .thenAccept(reply -> PacketHandler.sendToServer(new ActorDialogueResultPacket(jobId, reply)));
    }

    private static boolean shouldSuppressDuplicate(UUID playerUUID, int heroId, String observationDesc) {
        if (playerUUID == null) {
            return false;
        }

        String normalizedObservation = observationDesc == null ? "" : observationDesc.trim().toLowerCase(Locale.ROOT);
        if (normalizedObservation.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        String key = playerUUID + ":" + heroId + ":" + normalizedObservation;
        Long previous = RECENT_OBSERVATIONS.put(key, now);
        RECENT_OBSERVATIONS.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_SUPPRESS_WINDOW_MS);
        return previous != null && now - previous <= DUPLICATE_SUPPRESS_WINDOW_MS;
    }

    private static boolean isUsableAiReply(String reply) {
        if (reply == null || reply.isEmpty()) {
            return false;
        }
        return !reply.startsWith("§c") && !reply.startsWith("搂c") && !reply.startsWith("鎼係");
    }

    private static void showFallbackDialogue(Minecraft mc, int heroId, String fallbackKey, int fallbackVariants,
                                             String resolvedContextName) {
        Component line = buildFallbackDialogue(mc, fallbackKey, fallbackVariants, resolvedContextName);
        if (line == null) {
            return;
        }

        if (isDomesticFallbackKey(fallbackKey)) {
            showHeroDialogue(mc, heroId, line, true);
            return;
        }

        if (mc.player != null) {
            mc.player.sendSystemMessage(line);
        }
    }

    private static Component buildFallbackDialogue(Minecraft mc, String fallbackKey, int fallbackVariants,
                                                   String resolvedContextName) {
        if (mc.player == null || fallbackKey == null || fallbackKey.isEmpty()) {
            return null;
        }

        int variant = fallbackVariants <= 1 ? 1 : mc.player.getRandom().nextInt(fallbackVariants) + 1;
        if (AUTONOMOUS_REST_FALLBACK_KEY.equals(fallbackKey)) {
            return buildAutonomousRestFallback(mc.options.languageCode, resolvedContextName, variant);
        }
        if (AUTONOMOUS_COOK_FALLBACK_KEY.equals(fallbackKey)) {
            return buildAutonomousCookFallback(mc.options.languageCode, resolvedContextName, variant);
        }

        return fallbackVariants <= 1
                ? Component.translatable(fallbackKey)
                : Component.translatable(fallbackKey + "_" + variant);
    }

    private static Component buildAutonomousRestFallback(String languageCode, String restTargetName, int variant) {
        boolean chinese = isChineseLocale(languageCode);
        String subject = sanitize(restTargetName);
        if (subject.isEmpty()) {
            subject = chinese ? "这里" : "this place";
        }

        String line = switch (variant) {
            case 2 -> chinese
                    ? "就在" + subject + "歇一会儿。"
                    : "I'll rest on " + subject + " for a while.";
            case 3 -> chinese
                    ? subject + "看着还算安静。"
                    : subject + " looks quiet enough for a short rest.";
            case 4 -> chinese
                    ? "这地方不错，" + subject + "正合适。"
                    : "This will do. " + subject + " feels right.";
            default -> chinese
                    ? "先在" + subject + "上坐一下。"
                    : "I'll sit by " + subject + " and rest a moment.";
        };
        return Component.literal(line);
    }

    private static Component buildAutonomousCookFallback(String languageCode, String dishName, int variant) {
        boolean chinese = isChineseLocale(languageCode);
        String subject = sanitize(dishName);
        if (subject.isEmpty()) {
            subject = chinese ? "这道菜" : "this dish";
        }

        String line = switch (variant) {
            case 2 -> chinese
                    ? "想试试" + subject + "会是什么味道。"
                    : "I want to see how " + subject + " turns out.";
            case 3 -> chinese
                    ? subject + "闻起来应该不错。"
                    : subject + " sounds worth making.";
            case 4 -> chinese
                    ? "先做个" + subject + "，别打扰我。"
                    : "I'll make " + subject + ". Don't interrupt.";
            default -> chinese
                    ? "让我做一道" + subject + "。"
                    : "Let me cook some " + subject + ".";
        };
        return Component.literal(line);
    }

    private static String buildObservationDescription(String fallbackKey, String observationDesc, String resolvedContextName) {
        if (AUTONOMOUS_REST_FALLBACK_KEY.equals(fallbackKey)) {
            String subject = resolvedContextName.isBlank() ? "the spot you chose" : resolvedContextName;
            return "You are Hero. You yourself chose to rest on [" + subject + "]. Reply to the player with one short, natural first-person line about taking a quiet break there. Do not say the player is the one resting.";
        }
        if (AUTONOMOUS_COOK_FALLBACK_KEY.equals(fallbackKey)) {
            String subject = resolvedContextName.isBlank() ? "the dish you chose" : resolvedContextName;
            return "You are Hero. You yourself decided to cook [" + subject + "] with nearby cookware, without using the player's ingredients. Reply to the player with one short, natural first-person line that shows interest in the food. Do not say the player is the one cooking.";
        }
        return observationDesc == null ? "" : observationDesc;
    }

    private static String resolveContextName(String contextTranslationKey, String contextFallbackName) {
        String translationKey = sanitize(contextTranslationKey);
        if (!translationKey.isEmpty()) {
            String localized = Component.translatable(translationKey).getString().trim();
            if (!localized.isEmpty() && !translationKey.equals(localized)) {
                return localized;
            }
        }
        return sanitize(contextFallbackName);
    }

    private static boolean isDomesticFallbackKey(String fallbackKey) {
        return AUTONOMOUS_REST_FALLBACK_KEY.equals(fallbackKey)
                || AUTONOMOUS_COOK_FALLBACK_KEY.equals(fallbackKey);
    }

    private static void showHeroDialogue(Minecraft mc, int heroId, Component line, boolean withPrefix) {
        if (mc.player != null) {
            Component chatLine = withPrefix
                    ? Component.literal("<Herobrine> ").withStyle(ChatFormatting.YELLOW)
                    .append(line.copy().withStyle(ChatFormatting.WHITE))
                    : line;
            mc.player.sendSystemMessage(chatLine);
        }

        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(heroId);
        if (entity instanceof SpeechBubbleAccessor accessor) {
            accessor.herobrineCompanion$showSpeechBubble(line, computeBubbleDuration(line));
        }
    }

    private static int computeBubbleDuration(Component line) {
        int duration = MIN_BUBBLE_TICKS + line.getString().length() * 2;
        return Math.max(MIN_BUBBLE_TICKS, Math.min(MAX_BUBBLE_TICKS, duration));
    }

    private static void applyPresentedLine(Minecraft mc, String peerName, boolean hbMode, String speaker,
                                           String originalContent, String kind, byte displayType,
                                           String primaryName, String secondaryName, String localizedContent) {
        String finalContent = sanitize(localizedContent);
        if (finalContent.isEmpty()) {
            finalContent = sanitize(originalContent);
        }

        Component message = switch (displayType) {
            case 1 -> Component.translatable(
                    "message.herobrine_companion.cross_chat.chat.hb_to_hb_opening",
                    primaryName,
                    secondaryName,
                    finalContent
            );
            case 2 -> Component.translatable(
                    "message.herobrine_companion.cross_chat.chat.hb_echo",
                    primaryName,
                    finalContent
            );
            default -> Component.translatable(
                    "message.herobrine_companion.cross_chat.chat.remote_hb_reply",
                    primaryName,
                    finalContent
            );
        };

        mc.gui.getChat().addMessage(message);
        CrossChatHistoryStore.getInstance().appendEntry(peerName, hbMode, speaker, finalContent, kind);
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
        if (normalized.startsWith("§c")
                || normalized.startsWith("搂c")
                || normalized.startsWith("鎼係")
                || lowered.contains("network error")
                || lowered.contains("api error")
                || lowered.contains("connection to reality fading")) {
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
            case HeroCrossChatPromptPacket.KIND_HB_OPENING -> chinese ? "……我在听。" : "...I'm listening.";
            case HeroCrossChatPromptPacket.KIND_HB_REPLY -> chinese ? "……那就继续说。" : "...Then keep speaking.";
            default -> "...";
        };
    }

    private static String buildActorFallbackReply(String seedText, String fallbackKey, List<String> fallbackArgs) {
        if (fallbackKey != null && !fallbackKey.isBlank()) {
            Object[] args = fallbackArgs == null ? new Object[0] : fallbackArgs.toArray(new Object[0]);
            String translated = Component.translatable(fallbackKey, args).getString();
            String sanitized = sanitize(translated);
            if (!sanitized.isEmpty()) {
                return sanitized;
            }
        }
        String sanitizedSeed = sanitize(seedText);
        return sanitizedSeed.isEmpty() ? "..." : sanitizedSeed;
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

    private static String sanitize(String content) {
        return content == null ? "" : content.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
