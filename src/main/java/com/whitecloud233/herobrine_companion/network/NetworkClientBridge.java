package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NetworkClientBridge {
    private static final String CLIENT_BRIDGE_CLASS =
            "com.whitecloud233.herobrine_companion.client.network.NetworkClientBridgeClient";

    private NetworkClientBridge() {
    }

    public static void openCookSelection(int heroId, BlockPos cookwarePos, List<HeroCookingCompat.CookOptionView> options) {
        invoke("openCookSelection", new Class<?>[]{int.class, BlockPos.class, List.class},
                heroId, cookwarePos, List.copyOf(options));
    }

    public static void handleAIObservation(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                                           String contextTranslationKey, String contextFallbackName) {
        invoke("handleAIObservation",
                new Class<?>[]{int.class, String.class, String.class, int.class, String.class, String.class},
                heroId, observationDesc, fallbackKey, fallbackVariants, contextTranslationKey, contextFallbackName);
    }

    public static void applySavePose(int entityId, boolean isPosing, float[][] angles) {
        invoke("applySavePose", new Class<?>[]{int.class, boolean.class, float[][].class}, entityId, isPosing, angles);
    }

    public static void applySyncRewards(int entityId, Set<Integer> claimedRewards) {
        invoke("applySyncRewards", new Class<?>[]{int.class, Set.class}, entityId, Set.copyOf(claimedRewards));
    }

    public static void applySyncHeroCosmetics(int entityId, int skinVariant, String customSkinName, byte[] customSkinData,
                                              CompoundTag curiosBackItem, CompoundTag accessoriesData) {
        invoke("applySyncHeroCosmetics",
                new Class<?>[]{int.class, int.class, String.class, byte[].class, CompoundTag.class, CompoundTag.class},
                entityId,
                skinVariant,
                customSkinName,
                customSkinData == null ? new byte[0] : customSkinData.clone(),
                curiosBackItem == null ? new CompoundTag() : curiosBackItem.copy(),
                accessoriesData == null ? new CompoundTag() : accessoriesData.copy());
    }

    public static void openHeroChat(boolean hbInputMode) {
        invoke("openHeroChat", new Class<?>[]{boolean.class}, hbInputMode);
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        invoke("openCrossChatInvite", new Class<?>[]{UUID.class, String.class}, requesterId, requesterName);
    }

    public static void openCrossSessionHub(int entityId) {
        invoke("openCrossSessionHub", new Class<?>[]{int.class}, entityId);
    }

    public static void appendCrossChatHistory(String peerName, boolean hbMode, String speaker, String content, String kind) {
        invoke("appendCrossChatHistory",
                new Class<?>[]{String.class, boolean.class, String.class, String.class, String.class},
                peerName, hbMode, speaker, content, kind);
    }

    public static void presentCrossChatAiLine(String peerName, boolean hbMode, String speaker, String content,
                                              String kind, byte displayType, String primaryName,
                                              String secondaryName, boolean translateForViewer) {
        invoke("presentCrossChatAiLine",
                new Class<?>[]{String.class, boolean.class, String.class, String.class, String.class, byte.class, String.class, String.class, boolean.class},
                peerName, hbMode, speaker, content, kind, displayType, primaryName, secondaryName, translateForViewer);
    }

    public static void syncCrossChatState(boolean allowIncoming, boolean activeSession, String peerName,
                                          boolean autoChatEnabled, int autoHbTurnLimit) {
        invoke("syncCrossChatState",
                new Class<?>[]{boolean.class, boolean.class, String.class, boolean.class, int.class},
                allowIncoming, activeSession, peerName, autoChatEnabled, autoHbTurnLimit);
    }

    public static void setVisitedHeroDimension(boolean visited) {
        invoke("setVisitedHeroDimension", new Class<?>[]{boolean.class}, visited);
    }

    public static void triggerEternalOath() {
        invoke("triggerEternalOath", new Class<?>[0]);
    }

    public static void handleHeroCrossChatPrompt(UUID jobId, UUID sessionId, byte kind, String prompt,
                                                 String seedText, String outputLanguageCode) {
        invoke("handleHeroCrossChatPrompt",
                new Class<?>[]{UUID.class, UUID.class, byte.class, String.class, String.class, String.class},
                jobId, sessionId, kind, prompt, seedText, outputLanguageCode);
    }

    public static void handleActorDialoguePrompt(UUID jobId, UUID conversationScopeId, String systemPrompt,
                                                 String userPrompt, String seedText, String fallbackKey,
                                                 List<String> fallbackArgs, String outputLanguageCode) {
        invoke("handleActorDialoguePrompt",
                new Class<?>[]{UUID.class, UUID.class, String.class, String.class, String.class, String.class, List.class, String.class},
                jobId, conversationScopeId, systemPrompt, userPrompt, seedText,
                fallbackKey, fallbackArgs == null ? List.of() : List.copyOf(fallbackArgs), outputLanguageCode);
    }

    public static void startCollapse() {
        invoke("startCollapse", new Class<?>[0]);
    }

    public static void openFakeCrash() {
        invoke("openFakeCrash", new Class<?>[0]);
    }

    public static void handleWorldRendCinematic(double x, double y, double z) {
        invoke("handleWorldRendCinematic", new Class<?>[]{double.class, double.class, double.class}, x, y, z);
    }

    public static void handlePaleLightning(PaleLightningPacket packet) {
        invoke("handlePaleLightning", new Class<?>[]{PaleLightningPacket.class}, packet);
    }

    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        invoke("handlePaleLightningArc", new Class<?>[]{PaleLightningArcPacket.class}, packet);
    }

    public static void handleDestructionGodLightning(DestructionGodLightningPacket packet) {
        invoke("handleDestructionGodLightning", new Class<?>[]{DestructionGodLightningPacket.class}, packet);
    }

    public static void handleDestructionGodLightningArc(DestructionGodLightningArcPacket packet) {
        invoke("handleDestructionGodLightningArc", new Class<?>[]{DestructionGodLightningArcPacket.class}, packet);
    }

    public static void handleDestructionGodOrb(DestructionGodOrbPacket packet) {
        invoke("handleDestructionGodOrb", new Class<?>[]{DestructionGodOrbPacket.class}, packet);
    }

    public static void handleDestructionGodThunderSkyNet(DestructionGodThunderSkyNetPacket packet) {
        invoke("handleDestructionGodThunderSkyNet", new Class<?>[]{DestructionGodThunderSkyNetPacket.class}, packet);
    }

    public static void handleDestructionGodFaultSplit(DestructionGodFaultSplitPacket packet) {
        invoke("handleDestructionGodFaultSplit", new Class<?>[]{DestructionGodFaultSplitPacket.class}, packet);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        ClientOnlyExecutor.invoke(CLIENT_BRIDGE_CLASS, methodName, parameterTypes, args);
    }
}
