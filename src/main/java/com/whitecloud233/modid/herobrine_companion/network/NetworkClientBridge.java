package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NetworkClientBridge {
    private NetworkClientBridge() {
    }

    public static void openCookSelection(int heroId, BlockPos cookwarePos, List<HeroCookingCompat.CookOptionView> options) {
        List<HeroCookingCompat.CookOptionView> safeOptions = List.copyOf(options);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.openCookSelection(heroId, cookwarePos, safeOptions));
    }

    public static void handleAIObservation(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                                           String contextTranslationKey, String contextFallbackName) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleAIObservation(
                        heroId, observationDesc, fallbackKey, fallbackVariants,
                        contextTranslationKey, contextFallbackName));
    }

    public static void applySavePose(int entityId, boolean isPosing, float[][] angles) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.applySavePose(entityId, isPosing, angles));
    }

    public static void applySyncRewards(int entityId, Set<Integer> claimedRewards) {
        Set<Integer> safeClaimedRewards = Set.copyOf(claimedRewards);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.applySyncRewards(entityId, safeClaimedRewards));
    }

    public static void applySyncHeroCosmetics(int entityId, int skinVariant, String customSkinName, byte[] customSkinData,
                                              CompoundTag curiosBackItem, CompoundTag accessoriesData) {
        byte[] safeCustomSkinData = customSkinData == null ? new byte[0] : customSkinData.clone();
        CompoundTag safeCuriosBackItem = curiosBackItem == null ? new CompoundTag() : curiosBackItem.copy();
        CompoundTag safeAccessoriesData = accessoriesData == null ? new CompoundTag() : accessoriesData.copy();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.applySyncHeroCosmetics(
                        entityId, skinVariant, customSkinName, safeCustomSkinData, safeCuriosBackItem, safeAccessoriesData));
    }

    public static void openHeroChat(boolean hbInputMode) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.openHeroChat(hbInputMode));
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.openCrossChatInvite(requesterId, requesterName));
    }

    public static void openCrossSessionHub(int entityId) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.openCrossSessionHub(entityId));
    }

    public static void appendCrossChatHistory(String peerName, boolean hbMode, String speaker, String content, String kind) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.appendCrossChatHistory(
                        peerName, hbMode, speaker, content, kind));
    }

    public static void presentCrossChatAiLine(String peerName, boolean hbMode, String speaker, String content,
                                              String kind, byte displayType, String primaryName,
                                              String secondaryName, boolean translateForViewer) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.presentCrossChatAiLine(
                        peerName, hbMode, speaker, content, kind, displayType, primaryName, secondaryName, translateForViewer));
    }

    public static void syncCrossChatState(boolean allowIncoming, boolean activeSession, String peerName,
                                          boolean autoChatEnabled, int autoHbTurnLimit) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.syncCrossChatState(
                        allowIncoming, activeSession, peerName, autoChatEnabled, autoHbTurnLimit));
    }

    public static void setVisitedHeroDimension(boolean visited) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.setVisitedHeroDimension(visited));
    }

    public static void triggerEternalOath() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.triggerEternalOath());
    }

    public static void handleHeroCrossChatPrompt(UUID jobId, UUID sessionId, byte kind, String prompt,
                                                 String seedText, String outputLanguageCode) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleHeroCrossChatPrompt(
                        jobId, sessionId, kind, prompt, seedText, outputLanguageCode));
    }

    public static void handleActorDialoguePrompt(UUID jobId, UUID conversationScopeId, String systemPrompt,
                                                 String userPrompt, String seedText, String fallbackKey,
                                                 List<String> fallbackArgs, String outputLanguageCode) {
        List<String> safeFallbackArgs = List.copyOf(fallbackArgs);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleActorDialoguePrompt(
                        jobId, conversationScopeId, systemPrompt, userPrompt, seedText,
                        fallbackKey, safeFallbackArgs, outputLanguageCode));
    }

    public static void startCollapse() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.startCollapse());
    }

    public static void openFakeCrash() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.openFakeCrash());
    }

    public static void handleWorldRendCinematic(double x, double y, double z) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleWorldRendCinematic(x, y, z));
    }

    public static void handlePaleLightning(PaleLightningPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handlePaleLightning(packet));
    }

    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handlePaleLightningArc(packet));
    }

    public static void handleDestructionGodLightning(DestructionGodLightningPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleDestructionGodLightning(packet));
    }

    public static void handleDestructionGodLightningArc(DestructionGodLightningArcPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleDestructionGodLightningArc(packet));
    }

    public static void handleDestructionGodOrb(DestructionGodOrbPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleDestructionGodOrb(packet));
    }

    public static void handleDestructionGodThunderSkyNet(DestructionGodThunderSkyNetPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleDestructionGodThunderSkyNet(packet));
    }

    public static void handleDestructionGodFaultSplit(DestructionGodFaultSplitPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.network.NetworkClientBridgeClient.handleDestructionGodFaultSplit(packet));
    }
}
