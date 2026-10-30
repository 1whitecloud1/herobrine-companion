package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.OpenCookSelectionPacket;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.OpenMillstoneSelectionPacket;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.SelectCookOptionPacket;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.SelectMillstoneOptionPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.SPacketWorldRendCinematic;
import com.whitecloud233.modid.herobrine_companion.fight.network.CPacketCollapseFinished;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketFakeCrash;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketStartCollapse;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialoguePromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialogueResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.AppendCrossChatHistoryPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.CloseCrossChatSessionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenCrossChatInvitePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenCrossSessionHubPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenHeroChatPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.PresentCrossChatAiLinePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.RequestCrossChatSessionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.RespondCrossChatInvitePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SendCrossChatHbMessagePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SendCrossChatMessagePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatAutoChatPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatAutoTurnLimitPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatPermissionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SyncCrossChatStatePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.UpdateClientLanguagePacket;
import com.whitecloud233.modid.herobrine_companion.network.SyncServerConfigPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "4"; // Adds authoritative protection-state replies.
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(HerobrineCompanion.MODID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    // wire ID 固定，新增消息只使用新编号；删除或重排注册不能复用已发布的编号。
    private static final Map<Integer, Class<?>> REGISTERED_IDS = new HashMap<>();

    public static void register() {

        // ---------- 核心交互(C→S 指令 / S→C 同步) ----------
        reg(0, PeacefulPacket.class, PeacefulPacket::encode, PeacefulPacket::new, PeacefulPacket::handle);
        reg(1, ContractPacket.class, ContractPacket::encode, ContractPacket::new, ContractPacket::handle);
        reg(2, ClearAreaPacket.class, ClearAreaPacket::encode, ClearAreaPacket::new, ClearAreaPacket::handle);
        reg(3, OpenTradePacket.class, OpenTradePacket::encode, OpenTradePacket::new, OpenTradePacket::handle);
        reg(4, ToggleCompanionPacket.class, ToggleCompanionPacket::encode, ToggleCompanionPacket::new, ToggleCompanionPacket::handle);
        reg(5, SyncHeroVisitPacket.class, SyncHeroVisitPacket::encode, SyncHeroVisitPacket::new, SyncHeroVisitPacket::handle);
        reg(6, RequestActionPacket.class, RequestActionPacket::encode, RequestActionPacket::new, RequestActionPacket::handle);
        reg(7, DesolateAreaPacket.class, DesolateAreaPacket::encode, DesolateAreaPacket::new, DesolateAreaPacket::handle);
        reg(8, FlattenAreaPacket.class, FlattenAreaPacket::encode, FlattenAreaPacket::new, FlattenAreaPacket::handle);
        reg(9, ClaimRewardPacket.class, ClaimRewardPacket::encode, ClaimRewardPacket::new, ClaimRewardPacket::handle);
        reg(10, ToggleSkinPacket.class, ToggleSkinPacket::encode, ToggleSkinPacket::new, ToggleSkinPacket::handle);
        reg(11, SyncHeroCosmeticsPacket.class, SyncHeroCosmeticsPacket::encode, SyncHeroCosmeticsPacket::new, SyncHeroCosmeticsPacket::handle);
        reg(12, SyncRewardsPacket.class, SyncRewardsPacket::encode, SyncRewardsPacket::new, SyncRewardsPacket::handle);
        reg(13, TriggerEternalOathPacket.class, TriggerEternalOathPacket::encode, TriggerEternalOathPacket::new, TriggerEternalOathPacket::handle);
        reg(14, CleaveSkillPacket.class, CleaveSkillPacket::encode, CleaveSkillPacket::new, CleaveSkillPacket::handle);
        reg(15, OpenWardrobePacket.class, OpenWardrobePacket::encode, OpenWardrobePacket::new, OpenWardrobePacket::handle);

        // ---------- 挑战模式 / 战斗演出(S→C FX) ----------
        reg(16, StartChallengePacket.class, StartChallengePacket::encode, StartChallengePacket::new, StartChallengePacket::handle);
        reg(17, PaleLightningPacket.class, PaleLightningPacket::encode, PaleLightningPacket::new, PaleLightningPacket::handle);
        reg(18, PaleLightningArcPacket.class, PaleLightningArcPacket::encode, PaleLightningArcPacket::new, PaleLightningArcPacket::handle);
        reg(19, ChallengeAfterimagePacket.class, ChallengeAfterimagePacket::encode, ChallengeAfterimagePacket::new, ChallengeAfterimagePacket::handle);
        reg(20, DestructionGodLightningPacket.class, DestructionGodLightningPacket::encode, DestructionGodLightningPacket::new, DestructionGodLightningPacket::handle);
        reg(21, DestructionGodLightningArcPacket.class, DestructionGodLightningArcPacket::encode, DestructionGodLightningArcPacket::new, DestructionGodLightningArcPacket::handle);
        reg(22, DestructionGodOrbPacket.class, DestructionGodOrbPacket::encode, DestructionGodOrbPacket::new, DestructionGodOrbPacket::handle);
        reg(23, DestructionGodThunderSkyNetPacket.class, DestructionGodThunderSkyNetPacket::encode, DestructionGodThunderSkyNetPacket::new, DestructionGodThunderSkyNetPacket::handle);
        reg(24, DestructionGodFaultSplitPacket.class, DestructionGodFaultSplitPacket::encode, DestructionGodFaultSplitPacket::new, DestructionGodFaultSplitPacket::handle);
        reg(25, SavePosePacket.class, SavePosePacket::encode, SavePosePacket::new, SavePosePacket::handle);

        // ---------- AI 观察 / 指令 ----------
        reg(26, AIObservationPacket.class, AIObservationPacket::encode, AIObservationPacket::new, AIObservationPacket::handle);
        reg(27, HeroAIActionPacket.class, HeroAIActionPacket::encode, HeroAIActionPacket::new, HeroAIActionPacket::handle);
        reg(28, ToggleBattleModePacket.class, ToggleBattleModePacket::encode, ToggleBattleModePacket::new, ToggleBattleModePacket::handle);

        // ---------- 演出:世界崩坏 / 假死机 / 灭世演出(C→S 与 S→C) ----------
        reg(29, SPacketStartCollapse.class, SPacketStartCollapse::encode, SPacketStartCollapse::new, SPacketStartCollapse::handle);
        reg(30, SPacketFakeCrash.class, SPacketFakeCrash::encode, SPacketFakeCrash::new, SPacketFakeCrash::handle);
        reg(31, SPacketWorldRendCinematic.class, SPacketWorldRendCinematic::encode, SPacketWorldRendCinematic::new, SPacketWorldRendCinematic::handle);
        reg(32, CPacketCollapseFinished.class, CPacketCollapseFinished::encode, CPacketCollapseFinished::new, CPacketCollapseFinished::handle);

        // ---------- 召唤 / 传送 / 惩罚(C→S) ----------
        reg(33, SummonHeroPacket.class, SummonHeroPacket::encode, SummonHeroPacket::new, SummonHeroPacket::handle);
        reg(34, TeleportToHeroPacket.class, TeleportToHeroPacket::encode, TeleportToHeroPacket::new, TeleportToHeroPacket::handle);
        reg(35, HeroPunishmentPacket.class, HeroPunishmentPacket::encode, HeroPunishmentPacket::new, HeroPunishmentPacket::handle);

        // ---------- 烹饪兼容 ----------
        reg(36, OpenCookSelectionPacket.class, OpenCookSelectionPacket::encode, OpenCookSelectionPacket::new, OpenCookSelectionPacket::handle);
        reg(37, SelectCookOptionPacket.class, SelectCookOptionPacket::encode, SelectCookOptionPacket::new, SelectCookOptionPacket::handle);
        reg(78, OpenMillstoneSelectionPacket.class, OpenMillstoneSelectionPacket::encode, OpenMillstoneSelectionPacket::new, OpenMillstoneSelectionPacket::handle);
        reg(79, SelectMillstoneOptionPacket.class, SelectMillstoneOptionPacket::encode, SelectMillstoneOptionPacket::new, SelectMillstoneOptionPacket::handle);

        // ---------- AI 对话 / 跨服聊天(S→C 指令, C→S 结果) ----------
        reg(38, OpenHeroChatPacket.class, OpenHeroChatPacket::encode, OpenHeroChatPacket::new, OpenHeroChatPacket::handle);
        reg(39, UpdateClientLanguagePacket.class, UpdateClientLanguagePacket::encode, UpdateClientLanguagePacket::new, UpdateClientLanguagePacket::handle);
        reg(40, AppendCrossChatHistoryPacket.class, AppendCrossChatHistoryPacket::encode, AppendCrossChatHistoryPacket::new, AppendCrossChatHistoryPacket::handle);
        reg(41, PresentCrossChatAiLinePacket.class, PresentCrossChatAiLinePacket::encode, PresentCrossChatAiLinePacket::new, PresentCrossChatAiLinePacket::handle);
        reg(42, SyncCrossChatStatePacket.class, SyncCrossChatStatePacket::encode, SyncCrossChatStatePacket::new, SyncCrossChatStatePacket::handle);
        reg(43, OpenCrossChatInvitePacket.class, OpenCrossChatInvitePacket::encode, OpenCrossChatInvitePacket::new, OpenCrossChatInvitePacket::handle);
        reg(44, OpenCrossSessionHubPacket.class, OpenCrossSessionHubPacket::encode, OpenCrossSessionHubPacket::new, OpenCrossSessionHubPacket::handle);
        reg(45, RequestCrossChatSessionPacket.class, RequestCrossChatSessionPacket::encode, RequestCrossChatSessionPacket::new, RequestCrossChatSessionPacket::handle);
        reg(46, RespondCrossChatInvitePacket.class, RespondCrossChatInvitePacket::encode, RespondCrossChatInvitePacket::new, RespondCrossChatInvitePacket::handle);
        reg(47, SetCrossChatPermissionPacket.class, SetCrossChatPermissionPacket::encode, SetCrossChatPermissionPacket::new, SetCrossChatPermissionPacket::handle);
        reg(48, SetCrossChatAutoChatPacket.class, SetCrossChatAutoChatPacket::encode, SetCrossChatAutoChatPacket::new, SetCrossChatAutoChatPacket::handle);
        reg(49, SetCrossChatAutoTurnLimitPacket.class, SetCrossChatAutoTurnLimitPacket::encode, SetCrossChatAutoTurnLimitPacket::new, SetCrossChatAutoTurnLimitPacket::handle);
        reg(50, SendCrossChatMessagePacket.class, SendCrossChatMessagePacket::encode, SendCrossChatMessagePacket::new, SendCrossChatMessagePacket::handle);
        reg(51, SendCrossChatHbMessagePacket.class, SendCrossChatHbMessagePacket::encode, SendCrossChatHbMessagePacket::new, SendCrossChatHbMessagePacket::handle);
        reg(52, CloseCrossChatSessionPacket.class, CloseCrossChatSessionPacket::encode, CloseCrossChatSessionPacket::new, CloseCrossChatSessionPacket::handle);
        reg(53, ActorDialoguePromptPacket.class, ActorDialoguePromptPacket::encode, ActorDialoguePromptPacket::new, ActorDialoguePromptPacket::handle);
        reg(54, ActorDialogueResultPacket.class, ActorDialogueResultPacket::encode, ActorDialogueResultPacket::new, ActorDialogueResultPacket::handle);
        reg(55, HeroCrossChatPromptPacket.class, HeroCrossChatPromptPacket::encode, HeroCrossChatPromptPacket::new, HeroCrossChatPromptPacket::handle);
        reg(56, HeroCrossChatResultPacket.class, HeroCrossChatResultPacket::encode, HeroCrossChatResultPacket::new, HeroCrossChatResultPacket::handle);

        // ---------- 骑乘输入 / 竞技场切片 ----------
        reg(57, JeanMountInputPacket.class, JeanMountInputPacket::encode, JeanMountInputPacket::new, JeanMountInputPacket::handle);
        reg(58, SPacketChallengeArenaSlice.class, SPacketChallengeArenaSlice::encode, SPacketChallengeArenaSlice::new, SPacketChallengeArenaSlice::handle);

        // ---------- Agent 工具/任务请求(C→S, M4) ----------
        reg(59, AgentRequestPacket.class, AgentRequestPacket::encode, AgentRequestPacket::new, AgentRequestPacket::handle);

        // ---------- Agent 对话结果回流(C→S, M4 Phase 1) ----------
        reg(60, AgentChatOutcomePacket.class, AgentChatOutcomePacket::encode, AgentChatOutcomePacket::new, AgentChatOutcomePacket::handle);

        // ---------- Agent 可观测面板(C→S 查询 / S→C 快照, M5) ----------
        reg(61, RequestAgentStatusPacket.class, RequestAgentStatusPacket::encode, RequestAgentStatusPacket::new, RequestAgentStatusPacket::handle);
        reg(62, AgentStatusPacket.class, AgentStatusPacket::encode, AgentStatusPacket::new, AgentStatusPacket::handle);

        // ---------- Agent 长期记忆(记忆摘要 C→S / S→C, 清理 C→S, M4 Phase 1) ----------
        reg(63, RequestHeroMemoryDigestPacket.class, RequestHeroMemoryDigestPacket::encode, RequestHeroMemoryDigestPacket::new, RequestHeroMemoryDigestPacket::handle);
        reg(64, HeroMemoryDigestPacket.class, HeroMemoryDigestPacket::encode, HeroMemoryDigestPacket::new, HeroMemoryDigestPacket::handle);
        reg(65, ClearHeroMemoryPacket.class, ClearHeroMemoryPacket::encode, ClearHeroMemoryPacket::new, ClearHeroMemoryPacket::handle);

        // ---------- Agent 工具结果(S→C, M4 共享基础设施) ----------
        reg(66, AgentToolResultPacket.class, AgentToolResultPacket::encode, AgentToolResultPacket::new, AgentToolResultPacket::handle);

        // ---------- Agent 工具审批(S→C 提示 / C→S 同意·拒绝, P3) ----------
        reg(67, ToolApprovalPromptPacket.class, ToolApprovalPromptPacket::encode, ToolApprovalPromptPacket::new, ToolApprovalPromptPacket::handle);
        reg(68, ApproveToolPacket.class, ApproveToolPacket::encode, ApproveToolPacket::new, ApproveToolPacket::handle);
        reg(69, RejectToolPacket.class, RejectToolPacket::encode, RejectToolPacket::new, RejectToolPacket::handle);

        // ---------- 客户端设置实时同步到服务端 ----------
        reg(70, SyncServerConfigPacket.class, SyncServerConfigPacket::encode, SyncServerConfigPacket::new, SyncServerConfigPacket::handle);

        // ---------- 结构清单同步(C→S 请求 / S→C 响应, 服务端权威) ----------
        reg(71, StructureIndexPacket.class, StructureIndexPacket::encode, StructureIndexPacket::new, StructureIndexPacket::handle);

        // ---------- 委托状态同步(S→C, 交付物品开屏拦截) ----------
        reg(72, QuestStateSyncPacket.class, QuestStateSyncPacket::encode, QuestStateSyncPacket::new, QuestStateSyncPacket::handle);

        // ---------- 赠礼(托付, C→S;追加在末尾保持协议 ID 稳定) ----------
        reg(73, OfferGiftPacket.class, OfferGiftPacket::encode, OfferGiftPacket::new, OfferGiftPacket::handle);

        // ---------- 无名之蛋糕:摆放/收回(绕开原版物品冷却门禁) ----------
        reg(74, BirthdayCakePlacePacket.class, BirthdayCakePlacePacket::encode, BirthdayCakePlacePacket::new, BirthdayCakePlacePacket::handle);
        reg(75, PoemGesturePacket.class, PoemGesturePacket::encode, PoemGesturePacket::new, PoemGesturePacket::handle);
        reg(76, PoemAnimationRequestPacket.class, PoemAnimationRequestPacket::encode, PoemAnimationRequestPacket::new, PoemAnimationRequestPacket::handle);
        reg(77, PoemAnimationPacket.class, PoemAnimationPacket::encode, PoemAnimationPacket::new, PoemAnimationPacket::handle);
        reg(80, SyncPeacefulPacket.class, SyncPeacefulPacket::encode, SyncPeacefulPacket::new, SyncPeacefulPacket::handle);
    }

    private static <MSG> void reg(int id,
                                  Class<MSG> type,
                                  BiConsumer<MSG, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, MSG> decoder,
                                  BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        if (id < 0 || REGISTERED_IDS.containsKey(id)) {
            throw new IllegalStateException("Invalid or duplicate packet id " + id + " for " + type.getName());
        }
        if (REGISTERED_IDS.containsValue(type)) {
            throw new IllegalStateException("Packet type already registered: " + type.getName());
        }
        INSTANCE.registerMessage(id, type, encoder, decoder, handler);
        REGISTERED_IDS.put(id, type);
    }



    public static void sendToServer(Object packet) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    // [新增] 发送给所有能看到某个实体的玩家 (非常适合 Boss 技能)
    public static void sendToTracking(Object packet, Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }
}
