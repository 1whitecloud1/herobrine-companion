package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.compat.cooking.OpenCookSelectionPacket;
import com.whitecloud233.herobrine_companion.compat.cooking.SelectCookOptionPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.herobrine_companion.destructiongod.network.SPacketWorldRendCinematic;
import com.whitecloud233.herobrine_companion.fight.network.CPacketCollapseFinished;
import com.whitecloud233.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.herobrine_companion.fight.network.SPacketFakeCrash;
import com.whitecloud233.herobrine_companion.fight.network.SPacketStartCollapse;
import com.whitecloud233.herobrine_companion.network.ai.ActorDialoguePromptPacket;
import com.whitecloud233.herobrine_companion.network.ai.ActorDialogueResultPacket;
import com.whitecloud233.herobrine_companion.network.ai.AppendCrossChatHistoryPacket;
import com.whitecloud233.herobrine_companion.network.ai.CloseCrossChatSessionPacket;
import com.whitecloud233.herobrine_companion.network.ai.HeroCrossChatPromptPacket;
import com.whitecloud233.herobrine_companion.network.ai.HeroCrossChatResultPacket;
import com.whitecloud233.herobrine_companion.network.ai.OpenCrossChatInvitePacket;
import com.whitecloud233.herobrine_companion.network.ai.OpenCrossSessionHubPacket;
import com.whitecloud233.herobrine_companion.network.ai.OpenHeroChatPacket;
import com.whitecloud233.herobrine_companion.network.ai.PresentCrossChatAiLinePacket;
import com.whitecloud233.herobrine_companion.network.ai.RequestCrossChatSessionPacket;
import com.whitecloud233.herobrine_companion.network.ai.RespondCrossChatInvitePacket;
import com.whitecloud233.herobrine_companion.network.ai.SendCrossChatHbMessagePacket;
import com.whitecloud233.herobrine_companion.network.ai.SendCrossChatMessagePacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatAutoChatPacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatAutoTurnLimitPacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatPermissionPacket;
import com.whitecloud233.herobrine_companion.network.ai.SyncCrossChatStatePacket;
import com.whitecloud233.herobrine_companion.network.ai.SyncSpeechBubblePacket;
import com.whitecloud233.herobrine_companion.network.ai.UpdateClientLanguagePacket;
import com.whitecloud233.herobrine_companion.network.AgentChatOutcomePacket;
import com.whitecloud233.herobrine_companion.network.AgentRequestPacket;
import com.whitecloud233.herobrine_companion.network.AgentStatusPacket;
import com.whitecloud233.herobrine_companion.network.AgentToolResultPacket;
import com.whitecloud233.herobrine_companion.network.ApproveToolPacket;
import com.whitecloud233.herobrine_companion.network.ClearHeroMemoryPacket;
import com.whitecloud233.herobrine_companion.network.HeroMemoryDigestPacket;
import com.whitecloud233.herobrine_companion.network.RejectToolPacket;
import com.whitecloud233.herobrine_companion.network.RequestAgentStatusPacket;
import com.whitecloud233.herobrine_companion.network.RequestHeroMemoryDigestPacket;
import com.whitecloud233.herobrine_companion.network.ToolApprovalPromptPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PacketHandler {

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                PeacefulPacket.TYPE,
                PeacefulPacket.STREAM_CODEC,
                PeacefulPacket::handle
        );
        registrar.playToServer(
                ContractPacket.TYPE,
                ContractPacket.STREAM_CODEC,
                ContractPacket::handle
        );
        registrar.playToServer(
                ClearAreaPacket.TYPE,
                ClearAreaPacket.STREAM_CODEC,
                ClearAreaPacket::handle
        );
        registrar.playToServer(
                OpenTradePacket.TYPE,
                OpenTradePacket.STREAM_CODEC,
                OpenTradePacket::handle
        );
        registrar.playToServer(
                ToggleCompanionPacket.TYPE,
                ToggleCompanionPacket.STREAM_CODEC,
                ToggleCompanionPacket::handle
        );
        registrar.playToServer(
                RequestActionPacket.TYPE,
                RequestActionPacket.STREAM_CODEC,
                RequestActionPacket::handle
        );
        registrar.playToClient(
                OpenCookSelectionPacket.TYPE,
                OpenCookSelectionPacket.STREAM_CODEC,
                OpenCookSelectionPacket::handle
        );
        registrar.playToServer(
                SelectCookOptionPacket.TYPE,
                SelectCookOptionPacket.STREAM_CODEC,
                SelectCookOptionPacket::handle
        );
        registrar.playToClient(
                SyncHeroVisitPacket.TYPE,
                SyncHeroVisitPacket.STREAM_CODEC,
                SyncHeroVisitPacket::handle
        );
        registrar.playToServer(
                DesolateAreaPacket.TYPE,
                DesolateAreaPacket.STREAM_CODEC,
                DesolateAreaPacket::handle
        );
        registrar.playToServer(
                FlattenAreaPacket.TYPE,
                FlattenAreaPacket.STREAM_CODEC,
                FlattenAreaPacket::handle
        );
        registrar.playToServer(
                ClaimRewardPacket.TYPE,
                ClaimRewardPacket.STREAM_CODEC,
                ClaimRewardPacket::handle
        );
        registrar.playToServer(
                ToggleSkinPacket.TYPE,
                ToggleSkinPacket.STREAM_CODEC,
                ToggleSkinPacket::handle
        );
        registrar.playToServer(
                HeroPunishmentPacket.TYPE,
                HeroPunishmentPacket.STREAM_CODEC,
                HeroPunishmentPacket::handle
        );
        registrar.playToServer(
                HeroAIActionPacket.TYPE,
                HeroAIActionPacket.STREAM_CODEC,
                HeroAIActionPacket::handle
        );
        registrar.playToClient(
                TriggerEternalOathPacket.TYPE,
                TriggerEternalOathPacket.STREAM_CODEC,
                TriggerEternalOathPacket::handle
        );
        registrar.playToServer(
                CleaveSkillPacket.TYPE,
                CleaveSkillPacket.STREAM_CODEC,
                CleaveSkillPacket::handle
        );
        registrar.playToServer(
                OpenWardrobePacket.TYPE,
                OpenWardrobePacket.STREAM_CODEC,
                OpenWardrobePacket::handle
        );
        registrar.playToClient(
                SyncRewardsPacket.TYPE,
                SyncRewardsPacket.STREAM_CODEC,
                SyncRewardsPacket::handle
        );
        registrar.playToServer(
                StartChallengePacket.TYPE,
                StartChallengePacket.STREAM_CODEC,
                StartChallengePacket::handle
        );
        registrar.playToServer(
                ToggleBattleModePacket.TYPE,
                ToggleBattleModePacket.STREAM_CODEC,
                ToggleBattleModePacket::handle
        );
        registrar.playToClient(
                PaleLightningPacket.TYPE,
                PaleLightningPacket.STREAM_CODEC,
                PaleLightningPacket::handle
        );
        registrar.playToClient(
                PaleLightningArcPacket.TYPE,
                PaleLightningArcPacket.STREAM_CODEC,
                PaleLightningArcPacket::handle
        );
        registrar.playBidirectional(
                SavePosePacket.TYPE,
                SavePosePacket.STREAM_CODEC,
                SavePosePacket::handle
        );
        registrar.playToClient(
                AIObservationPacket.TYPE,
                AIObservationPacket.STREAM_CODEC,
                AIObservationPacket::handle
        );
        registrar.playToClient(
                SyncHeroCosmeticsPacket.TYPE,
                SyncHeroCosmeticsPacket.STREAM_CODEC,
                SyncHeroCosmeticsPacket::handle
        );
        registrar.playToServer(
                CPacketCollapseFinished.TYPE,
                CPacketCollapseFinished.STREAM_CODEC,
                CPacketCollapseFinished::handle
        );
        registrar.playToClient(
                SPacketStartCollapse.TYPE,
                SPacketStartCollapse.STREAM_CODEC,
                SPacketStartCollapse::handle
        );
        registrar.playToClient(
                SPacketFakeCrash.TYPE,
                SPacketFakeCrash.STREAM_CODEC,
                SPacketFakeCrash::handle
        );
        registrar.playToClient(
                ChallengeAfterimagePacket.TYPE,
                ChallengeAfterimagePacket.STREAM_CODEC,
                ChallengeAfterimagePacket::handle
        );
        registrar.playToClient(
                SPacketChallengeArenaSlice.TYPE,
                SPacketChallengeArenaSlice.STREAM_CODEC,
                SPacketChallengeArenaSlice::handle
        );
        registrar.playToServer(
                SummonHeroPacket.TYPE,
                SummonHeroPacket.STREAM_CODEC,
                SummonHeroPacket::handle
        );
        registrar.playToServer(
                TeleportToHeroPacket.TYPE,
                TeleportToHeroPacket.STREAM_CODEC,
                TeleportToHeroPacket::handle
        );
        registrar.playToClient(AppendCrossChatHistoryPacket.TYPE, AppendCrossChatHistoryPacket.STREAM_CODEC, AppendCrossChatHistoryPacket::handle);
        registrar.playToServer(CloseCrossChatSessionPacket.TYPE, CloseCrossChatSessionPacket.STREAM_CODEC, CloseCrossChatSessionPacket::handle);
        registrar.playToClient(HeroCrossChatPromptPacket.TYPE, HeroCrossChatPromptPacket.STREAM_CODEC, HeroCrossChatPromptPacket::handle);
        registrar.playToServer(HeroCrossChatResultPacket.TYPE, HeroCrossChatResultPacket.STREAM_CODEC, HeroCrossChatResultPacket::handle);
        registrar.playToClient(ActorDialoguePromptPacket.TYPE, ActorDialoguePromptPacket.STREAM_CODEC, ActorDialoguePromptPacket::handle);
        registrar.playToServer(ActorDialogueResultPacket.TYPE, ActorDialogueResultPacket.STREAM_CODEC, ActorDialogueResultPacket::handle);
        registrar.playToClient(SyncSpeechBubblePacket.TYPE, SyncSpeechBubblePacket.STREAM_CODEC, SyncSpeechBubblePacket::handle);
        registrar.playToClient(OpenCrossChatInvitePacket.TYPE, OpenCrossChatInvitePacket.STREAM_CODEC, OpenCrossChatInvitePacket::handle);
        registrar.playToClient(OpenCrossSessionHubPacket.TYPE, OpenCrossSessionHubPacket.STREAM_CODEC, OpenCrossSessionHubPacket::handle);
        registrar.playToClient(OpenHeroChatPacket.TYPE, OpenHeroChatPacket.STREAM_CODEC, OpenHeroChatPacket::handle);
        registrar.playToClient(PresentCrossChatAiLinePacket.TYPE, PresentCrossChatAiLinePacket.STREAM_CODEC, PresentCrossChatAiLinePacket::handle);
        registrar.playToServer(RequestCrossChatSessionPacket.TYPE, RequestCrossChatSessionPacket.STREAM_CODEC, RequestCrossChatSessionPacket::handle);
        registrar.playToServer(RespondCrossChatInvitePacket.TYPE, RespondCrossChatInvitePacket.STREAM_CODEC, RespondCrossChatInvitePacket::handle);
        registrar.playToServer(SendCrossChatHbMessagePacket.TYPE, SendCrossChatHbMessagePacket.STREAM_CODEC, SendCrossChatHbMessagePacket::handle);
        registrar.playToServer(SendCrossChatMessagePacket.TYPE, SendCrossChatMessagePacket.STREAM_CODEC, SendCrossChatMessagePacket::handle);
        registrar.playToServer(SetCrossChatAutoChatPacket.TYPE, SetCrossChatAutoChatPacket.STREAM_CODEC, SetCrossChatAutoChatPacket::handle);
        registrar.playToServer(SetCrossChatAutoTurnLimitPacket.TYPE, SetCrossChatAutoTurnLimitPacket.STREAM_CODEC, SetCrossChatAutoTurnLimitPacket::handle);
        registrar.playToServer(SetCrossChatPermissionPacket.TYPE, SetCrossChatPermissionPacket.STREAM_CODEC, SetCrossChatPermissionPacket::handle);
        registrar.playToClient(SyncCrossChatStatePacket.TYPE, SyncCrossChatStatePacket.STREAM_CODEC, SyncCrossChatStatePacket::handle);
        registrar.playToServer(UpdateClientLanguagePacket.TYPE, UpdateClientLanguagePacket.STREAM_CODEC, UpdateClientLanguagePacket::handle);
        registrar.playToServer(JeanMountInputPacket.TYPE, JeanMountInputPacket.STREAM_CODEC, JeanMountInputPacket::handle);

        // ---------- 灭世神粒子演出(S→C) ----------
        registrar.playToClient(
                DestructionGodLightningPacket.TYPE,
                DestructionGodLightningPacket.STREAM_CODEC,
                DestructionGodLightningPacket::handle
        );
        registrar.playToClient(
                DestructionGodLightningArcPacket.TYPE,
                DestructionGodLightningArcPacket.STREAM_CODEC,
                DestructionGodLightningArcPacket::handle
        );
        registrar.playToClient(
                DestructionGodOrbPacket.TYPE,
                DestructionGodOrbPacket.STREAM_CODEC,
                DestructionGodOrbPacket::handle
        );
        registrar.playToClient(
                DestructionGodThunderSkyNetPacket.TYPE,
                DestructionGodThunderSkyNetPacket.STREAM_CODEC,
                DestructionGodThunderSkyNetPacket::handle
        );
        registrar.playToClient(
                DestructionGodFaultSplitPacket.TYPE,
                DestructionGodFaultSplitPacket.STREAM_CODEC,
                DestructionGodFaultSplitPacket::handle
        );
        registrar.playToClient(
                SPacketWorldRendCinematic.TYPE,
                SPacketWorldRendCinematic.STREAM_CODEC,
                SPacketWorldRendCinematic::handle
        );

        // ---------- Agent 自主行为（M4/P2~P3） ----------
        registrar.playToServer(AgentRequestPacket.TYPE, AgentRequestPacket.STREAM_CODEC, AgentRequestPacket::handle);
        registrar.playToServer(AgentChatOutcomePacket.TYPE, AgentChatOutcomePacket.STREAM_CODEC, AgentChatOutcomePacket::handle);
        registrar.playToServer(RequestAgentStatusPacket.TYPE, RequestAgentStatusPacket.STREAM_CODEC, RequestAgentStatusPacket::handle);
        registrar.playToServer(RequestHeroMemoryDigestPacket.TYPE, RequestHeroMemoryDigestPacket.STREAM_CODEC, RequestHeroMemoryDigestPacket::handle);
        registrar.playToServer(ClearHeroMemoryPacket.TYPE, ClearHeroMemoryPacket.STREAM_CODEC, ClearHeroMemoryPacket::handle);
        registrar.playToServer(ApproveToolPacket.TYPE, ApproveToolPacket.STREAM_CODEC, ApproveToolPacket::handle);
        registrar.playToServer(RejectToolPacket.TYPE, RejectToolPacket.STREAM_CODEC, RejectToolPacket::handle);
        registrar.playToClient(AgentStatusPacket.TYPE, AgentStatusPacket.STREAM_CODEC, AgentStatusPacket::handle);
        registrar.playToClient(AgentToolResultPacket.TYPE, AgentToolResultPacket.STREAM_CODEC, AgentToolResultPacket::handle);
        registrar.playToClient(ToolApprovalPromptPacket.TYPE, ToolApprovalPromptPacket.STREAM_CODEC, ToolApprovalPromptPacket::handle);
        registrar.playToClient(HeroMemoryDigestPacket.TYPE, HeroMemoryDigestPacket.STREAM_CODEC, HeroMemoryDigestPacket::handle);
    }

    // 统一的发包入口:泛型方法覆盖所有包,不再需要按类型抄重载。
    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(CustomPacketPayload packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    // 发送给所有追踪该实体的玩家(含玩家自己)
    public static void sendToTracking(CustomPacketPayload packet, Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
    }

    public static void sendToNearby(CustomPacketPayload packet, Entity entity, double radius) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(serverLevel, null, entity.getX(), entity.getY(), entity.getZ(), radius, packet);
        } else {
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
        }
    }
}
