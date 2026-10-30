package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.network.ClientAgentStatus;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientAgentToolConfirmation;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientAgentToolResult;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientAgentFeatureHandler;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientAiPrompts;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientFxHandler;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientMemoryDigest;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientStateSync;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientUiDispatch;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.cinematic.ClientSpatialRendHandler;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.network.DestructionGodClientPacketHandler;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.fight.event.ClientCollapseHandler;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务器 → 客户端的唯一 DistExecutor 边界。所有 S→C 包的 {@code handle} 都经此转发到
 * 客户端侧的职责类({@link ClientUiDispatch} / {@link ClientStateSync} / {@link ClientAiPrompts}
 * / {@link ClientFxHandler} / {@link DestructionGodClientPacketHandler} 等)。
 *
 * <p>方法签名必须保持不变 —— 各包的 {@code handle} 直接引用它们;新增客户端收包动作时,
 * 在这里加一个转发方法即可。跨边界传递的集合/字节数组/CompoundTag 都做了防御性拷贝,
 * 防止网络线程与渲染线程共享可变状态。
 */
public final class NetworkClientBridge {
    public static void setPeacefulProtection(boolean enabled) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStateSync.applyPeacefulProtection(enabled));
    }

    public static void acceptTemplates(net.minecraft.network.Connection source, TemplateLibraryReply reply) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStateSync.acceptTemplates(source, reply));
    }

    public static void acceptTemplatePlacement(net.minecraft.network.Connection source, UUID requestId, boolean ok, boolean complete) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStateSync.acceptTemplatePlacement(source, requestId, ok, complete));
    }

    /**
     * 建筑预览线框（S→C）：把包围盒转交客户端渲染层。
     *
     * <p>网络包不直接引用 {@code client.structure} 下的类——那会破坏"网络层不依赖客户端实现"
     * 的架构边界（{@code verifyArchitecture} 会拦）。这里经唯一 DistExecutor 边界转发。</p>
     */
    public static void acceptTemplatePreview(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String label) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.structure.TemplatePreviewBox.show(
                        new net.minecraft.core.BlockPos(minX, minY, minZ),
                        new net.minecraft.core.BlockPos(maxX, maxY, maxZ), label));
    }

    /** 清空建筑预览（S→C）。 */
    public static void clearTemplatePreview() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.whitecloud233.modid.herobrine_companion.client.structure.TemplatePreviewBox.clear());
    }

    public static void acceptHeroContent(byte[] data) {
        byte[] copy = data.clone();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.whitecloud233.modid.herobrine_companion.client.network.ClientHeroContent.accept(copy));
    }

    private NetworkClientBridge() {
    }

    // ---------- UI ----------

    public static void openCookSelection(int heroId, BlockPos cookwarePos, List<CookOption> options) {
        List<CookOption> safeOptions = List.copyOf(options);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openCookSelection(heroId, cookwarePos, safeOptions));
    }

    public static void openMillstoneSelection(int heroId, BlockPos millstonePos, List<MillstoneOption> options) {
        List<MillstoneOption> safeOptions = List.copyOf(options);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openMillstoneSelection(heroId, millstonePos, safeOptions));
    }

    public static void openHeroChat(boolean hbInputMode) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openHeroChat(hbInputMode));
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openCrossChatInvite(requesterId, requesterName));
    }

    public static void openCrossSessionHub(int entityId) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openCrossSessionHub(entityId));
    }

    public static void triggerEternalOath() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.triggerEternalOath());
    }

    public static void openFakeCrash() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientUiDispatch.openFakeCrash());
    }

    /** Agent 可观测面板快照(M5)。snapshot 为不可变记录,无需防御性拷贝。 */
    public static void acceptAgentStatus(AgentStatusSnapshot snapshot) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAgentStatus.accept(snapshot));
    }

    /** Agent 长期记忆 digest(M4 Phase 1)。纯字符串,无需防御性拷贝。 */
    public static void acceptMemoryDigest(String digest) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientMemoryDigest.accept(digest));
    }

    /** Agent 工具执行结果(M4 共享基础设施,P2 起注入 LLM / 关联合成)。纯字符串 + UUID,无需防御性拷贝。 */
    public static void acceptAgentToolResult(UUID requestId, String toolId, boolean ok, String message) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAgentToolResult.accept(requestId, toolId, ok, message));
    }

    /** Agent 扩展能力握手回执(T22)：只记录服务端声明的能力位；旧服务端不会走到这里。 */
    public static void acceptAgentFeatures(UUID requestId, int features) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAgentFeatureHandler.accept(features));
    }

    /** Agent 工具审批提示(P3)：map 做了防御性拷贝,避免网络线程与渲染线程共享可变状态。 */
    public static void acceptToolApprovalPrompt(UUID requestId, String toolId, Map<String, String> args, Component description) {
        Map<String, String> safeArgs = Map.copyOf(args);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAgentToolConfirmation.accept(requestId, toolId, safeArgs, description));
    }

    // ---------- 实体状态同步 ----------

    public static void applyServerConfig(net.minecraft.network.Connection source, ServerConfigReply reply) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStateSync.applyServerConfig(source, reply));
    }

    public static void applySavePose(int entityId, boolean isPosing, float[][] angles) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.applySavePose(entityId, isPosing, angles));
    }

    public static void applySyncRewards(int entityId, Set<Integer> claimedRewards) {
        Set<Integer> safeClaimedRewards = Set.copyOf(claimedRewards);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.applySyncRewards(entityId, safeClaimedRewards));
    }

    public static void applySyncHeroCosmetics(int entityId, int skinVariant, String customSkinName, byte[] customSkinData,
                                              CompoundTag curiosBackItem, CompoundTag accessoriesData) {
        byte[] safeCustomSkinData = customSkinData == null ? new byte[0] : customSkinData.clone();
        CompoundTag safeCuriosBackItem = curiosBackItem == null ? new CompoundTag() : curiosBackItem.copy();
        CompoundTag safeAccessoriesData = accessoriesData == null ? new CompoundTag() : accessoriesData.copy();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.applySyncHeroCosmetics(
                        entityId, skinVariant, customSkinName, safeCustomSkinData, safeCuriosBackItem, safeAccessoriesData));
    }

    public static void setVisitedHeroDimension(boolean visited) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.setVisitedHeroDimension(visited));
    }

    public static void appendCrossChatHistory(String peerName, boolean hbMode, String speaker, String content, String kind) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.appendCrossChatHistory(peerName, hbMode, speaker, content, kind));
    }

    public static void syncCrossChatState(boolean allowIncoming, boolean activeSession, String peerName,
                                          boolean autoChatEnabled, int autoHbTurnLimit) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientStateSync.syncCrossChatState(allowIncoming, activeSession, peerName, autoChatEnabled, autoHbTurnLimit));
    }

    // ---------- AI 编排 ----------

    public static void handleAIObservation(int heroId, String observationDesc, String fallbackKey, int fallbackVariants,
                                           String contextTranslationKey, String contextFallbackName) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAiPrompts.handleAIObservation(heroId, observationDesc, fallbackKey, fallbackVariants,
                        contextTranslationKey, contextFallbackName));
    }

    public static void presentCrossChatAiLine(String peerName, boolean hbMode, String speaker, String content,
                                              String kind, byte displayType, String primaryName,
                                              String secondaryName, boolean translateForViewer) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAiPrompts.presentCrossChatAiLine(
                        peerName, hbMode, speaker, content, kind, displayType, primaryName, secondaryName, translateForViewer));
    }

    public static void handleHeroCrossChatPrompt(UUID jobId, UUID sessionId, byte kind, String prompt,
                                                 String seedText, String outputLanguageCode) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAiPrompts.handleHeroCrossChatPrompt(
                        jobId, sessionId, kind, prompt, seedText, outputLanguageCode));
    }

    public static void handleActorDialoguePrompt(UUID jobId, UUID conversationScopeId, String systemPrompt,
                                                 String userPrompt, String seedText, String fallbackKey,
                                                 List<String> fallbackArgs, String outputLanguageCode) {
        List<String> safeFallbackArgs = List.copyOf(fallbackArgs);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientAiPrompts.handleActorDialoguePrompt(
                        jobId, conversationScopeId, systemPrompt, userPrompt, seedText,
                        fallbackKey, safeFallbackArgs, outputLanguageCode));
    }

    // ---------- 粒子 / 演出 ----------

    public static void startCollapse() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientCollapseHandler.startCollapse());
    }

    public static void handleWorldRendCinematic(double x, double y, double z) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSpatialRendHandler.startWorldRendCinematic(x, y, z));
    }

    public static void handlePaleLightning(PaleLightningPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientFxHandler.handlePaleLightning(packet));
    }

    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientFxHandler.handlePaleLightningArc(packet));
    }

    public static void handleChallengeAfterimage(ChallengeAfterimagePacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientFxHandler.handleChallengeAfterimage(packet));
    }

    public static void handleChallengeArenaSlice(SPacketChallengeArenaSlice packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientFxHandler.handleChallengeArenaSlice(packet));
    }

    public static void handleDestructionGodLightning(DestructionGodLightningPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DestructionGodClientPacketHandler.handleLightning(packet));
    }

    public static void handleDestructionGodLightningArc(DestructionGodLightningArcPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DestructionGodClientPacketHandler.handleLightningArc(packet));
    }

    public static void handleDestructionGodOrb(DestructionGodOrbPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DestructionGodClientPacketHandler.handleOrb(packet));
    }

    public static void handleDestructionGodThunderSkyNet(DestructionGodThunderSkyNetPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DestructionGodClientPacketHandler.handleThunderSkyNet(packet));
    }

    public static void handleDestructionGodFaultSplit(DestructionGodFaultSplitPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DestructionGodClientPacketHandler.handleFaultSplit(packet));
    }
}
