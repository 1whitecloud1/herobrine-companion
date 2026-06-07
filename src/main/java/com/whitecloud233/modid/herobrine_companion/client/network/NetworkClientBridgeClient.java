package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.modid.herobrine_companion.client.fight.event.ClientCollapseHandler;
import com.whitecloud233.modid.herobrine_companion.client.gui.FakeCrashScreen;
import com.whitecloud233.modid.herobrine_companion.client.render.HeroClientSkinCache;
import com.whitecloud233.modid.herobrine_companion.client.service.ActorDialogueRequest;
import com.whitecloud233.modid.herobrine_companion.client.service.ActorDialogueService;
import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.CrossChatHistoryStore;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookSelectionScreen;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.cinematic.ClientSpatialRendHandler;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodClientPacketHandler;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import com.whitecloud233.modid.herobrine_companion.network.ClientPacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialogueResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatResultPacket;
import com.whitecloud233.modid.herobrine_companion.util.LegacyFormattingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkClientBridgeClient {
    private static final Map<String, Long> RECENT_OBSERVATIONS = new ConcurrentHashMap<>();
    private static final long DUPLICATE_SUPPRESS_WINDOW_MS = 5_000L;
    private static final String AUTONOMOUS_REST_FALLBACK_KEY = "message.herobrine_companion.autonomous_rest";
    private static final String AUTONOMOUS_COOK_FALLBACK_KEY = "message.herobrine_companion.autonomous_cook";
    private static final int MIN_BUBBLE_TICKS = 45;
    private static final int MAX_BUBBLE_TICKS = 180;

    private NetworkClientBridgeClient() {
    }

    public static void openCookSelection(int heroId, BlockPos cookwarePos, List<HeroCookingCompat.CookOptionView> options) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new HeroCookSelectionScreen(heroId, cookwarePos, options, minecraft.screen));
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
                                mc.player.sendSystemMessage(Component.literal(LegacyFormattingText.normalize("\u00A7e<Herobrine> \u00A7f" + reply)));
                            }
                        } else {
                            showFallbackDialogue(mc, heroId, fallbackKey, fallbackVariants, resolvedContextName);
                        }
                    }));
            return;
        }

        showFallbackDialogue(mc, heroId, fallbackKey, fallbackVariants, resolvedContextName);
    }

    public static void applySavePose(int entityId, boolean isPosing, float[][] angles) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof HeroEntity hero) {
            hero.isPoseEditing = isPosing;
            hero.customPoseAngles = copyAngles(angles);
        }
    }

    public static void applySyncRewards(int entityId, Set<Integer> claimedRewards) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof HeroEntity hero) {
            for (int rewardId : claimedRewards) {
                hero.claimReward(rewardId);
            }
        }
    }

    public static void applySyncHeroCosmetics(int entityId, int skinVariant, String customSkinName,
                                              byte[] customSkinData, CompoundTag curiosBackItem,
                                              CompoundTag accessoriesData) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof HeroEntity hero)) {
            return;
        }

        hero.setSkinVariant(skinVariant);
        hero.setCustomSkinName(customSkinName);
        hero.setCuriosBackItemFromTag(curiosBackItem);
        hero.setAccessoriesDataFromTag(accessoriesData);

        if (skinVariant == HeroEntity.SKIN_CUSTOM && customSkinData.length > 0) {
            HeroClientSkinCache.put(hero.getUUID(), customSkinData);
        } else {
            HeroClientSkinCache.clear(hero.getUUID());
        }
    }

    public static void openHeroChat(boolean hbInputMode) {
        ClientHooks.openHeroChatFromCommand(hbInputMode);
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        ClientHooks.openCrossChatInvite(requesterId, requesterName);
    }

    public static void openCrossSessionHub(int entityId) {
        ClientHooks.openCrossSessionHub(entityId);
    }

    public static void appendCrossChatHistory(String peerName, boolean hbMode, String speaker, String content, String kind) {
        CrossChatHistoryStore.getInstance().appendEntry(peerName, hbMode, speaker, content, kind);
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

    public static void syncCrossChatState(boolean allowIncoming, boolean activeSession, String peerName,
                                          boolean autoChatEnabled, int autoHbTurnLimit) {
        ClientHooks.syncCrossChatState(allowIncoming, activeSession, peerName, autoChatEnabled, autoHbTurnLimit);
    }

    public static void setVisitedHeroDimension(boolean visited) {
        ClientHooks.setVisitedHeroDimension(visited);
    }

    public static void triggerEternalOath() {
        ClientHooks.triggerEternalOath();
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

    public static void startCollapse() {
        ClientCollapseHandler.startCollapse();
    }

    public static void openFakeCrash() {
        FakeCrashScreen.open();
    }

    public static void handleWorldRendCinematic(double x, double y, double z) {
        ClientSpatialRendHandler.startWorldRendCinematic(x, y, z);
    }

    public static void handlePaleLightning(PaleLightningPacket packet) {
        ClientPacketHandler.handlePaleLightning(packet);
    }

    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        ClientPacketHandler.handlePaleLightningArc(packet);
    }

    public static void handleDestructionGodLightning(DestructionGodLightningPacket packet) {
        DestructionGodClientPacketHandler.handleLightning(packet);
    }

    public static void handleDestructionGodLightningArc(DestructionGodLightningArcPacket packet) {
        DestructionGodClientPacketHandler.handleLightningArc(packet);
    }

    public static void handleDestructionGodOrb(DestructionGodOrbPacket packet) {
        DestructionGodClientPacketHandler.handleOrb(packet);
    }

    public static void handleDestructionGodThunderSkyNet(DestructionGodThunderSkyNetPacket packet) {
        DestructionGodClientPacketHandler.handleThunderSkyNet(packet);
    }

    public static void handleDestructionGodFaultSplit(DestructionGodFaultSplitPacket packet) {
        DestructionGodClientPacketHandler.handleFaultSplit(packet);
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
        return !reply.startsWith("\u00A7c") && !reply.startsWith("\u6402c") && !reply.startsWith("\u93bc\u4fc2");
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
            subject = chinese ? "\u8fd9\u91cc" : "this place";
        }

        String line = switch (variant) {
            case 2 -> chinese
                    ? "\u5c31\u5728" + subject + "\u6b47\u4e00\u4f1a\u513f\u3002"
                    : "I'll rest on " + subject + " for a while.";
            case 3 -> chinese
                    ? subject + "\u770b\u7740\u8fd8\u7b97\u5b89\u9759\u3002"
                    : subject + " looks quiet enough for a short rest.";
            case 4 -> chinese
                    ? "\u8fd9\u5730\u65b9\u4e0d\u9519\uff0c" + subject + "\u6b63\u5408\u9002\u3002"
                    : "This will do. " + subject + " feels right.";
            default -> chinese
                    ? "\u5148\u5728" + subject + "\u4e0a\u5750\u4e00\u4e0b\u3002"
                    : "I'll sit by " + subject + " and rest a moment.";
        };
        return Component.literal(line);
    }

    private static Component buildAutonomousCookFallback(String languageCode, String dishName, int variant) {
        boolean chinese = isChineseLocale(languageCode);
        String subject = sanitize(dishName);
        if (subject.isEmpty()) {
            subject = chinese ? "\u8fd9\u9053\u83dc" : "this dish";
        }

        String line = switch (variant) {
            case 2 -> chinese
                    ? "\u60f3\u8bd5\u8bd5" + subject + "\u4f1a\u662f\u4ec0\u4e48\u5473\u9053\u3002"
                    : "I want to see how " + subject + " turns out.";
            case 3 -> chinese
                    ? subject + "\u95fb\u8d77\u6765\u5e94\u8be5\u4e0d\u9519\u3002"
                    : subject + " sounds worth making.";
            case 4 -> chinese
                    ? "\u5148\u505a\u4e2a" + subject + "\uff0c\u522b\u6253\u6270\u6211\u3002"
                    : "I'll make " + subject + ". Don't interrupt.";
            default -> chinese
                    ? "\u8ba9\u6211\u505a\u4e00\u9053" + subject + "\u3002"
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
        if (normalized.startsWith("\u00A7c")
                || normalized.startsWith("\u6402c")
                || normalized.startsWith("\u93bc\u4fc2")
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
            case HeroCrossChatPromptPacket.KIND_HB_OPENING -> chinese ? "\u2026\u2026\u6211\u5728\u542c\u3002" : "...I'm listening.";
            case HeroCrossChatPromptPacket.KIND_HB_REPLY -> chinese ? "\u2026\u2026\u90a3\u5c31\u7ee7\u7eed\u8bf4\u3002" : "...Then keep speaking.";
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

    private static float[][] copyAngles(float[][] source) {
        float[][] dest = new float[10][3];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 3);
        }
        return dest;
    }
}
