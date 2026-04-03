package com.whitecloud233.herobrine_companion.network;

// 【新增】导入这三个新的数据包
import com.whitecloud233.herobrine_companion.client.fight.network.CPacketCollapseFinished;
import com.whitecloud233.herobrine_companion.client.fight.network.SPacketFakeCrash;
import com.whitecloud233.herobrine_companion.client.fight.network.SPacketStartCollapse;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
        registrar.playToClient(
                PaleLightningPacket.TYPE,
                PaleLightningPacket.STREAM_CODEC,
                PaleLightningPacket::handle
        );
        registrar.playBidirectional(
                PaleLightningArcPacket.TYPE,
                PaleLightningArcPacket.STREAM_CODEC,
                PaleLightningArcPacket::handle
        );

        // ============================================
        // [新增] 注册 SavePosePacket (双向通信)
        // ============================================
        registrar.playBidirectional(
                SavePosePacket.TYPE,
                SavePosePacket.STREAM_CODEC,
                SavePosePacket::handle
        );

        // 注册我们刚刚写的 AIObservationPacket，方向是 服务端 -> 客户端 (playToClient)
        registrar.playToClient(
                AIObservationPacket.TYPE,
                AIObservationPacket.STREAM_CODEC,
                AIObservationPacket::handle
        );

        // ============================================
        // [新增] 试炼崩坏演出相关数据包
        // ============================================

        // 1. 客户端发给服务端：崩坏演出结束，请求结算
        registrar.playToServer(
                CPacketCollapseFinished.TYPE,
                CPacketCollapseFinished.CODEC,
                CPacketCollapseFinished::handle
        );

        // 2. 服务端发给客户端：开始世界崩坏演出
        registrar.playToClient(
                SPacketStartCollapse.TYPE,
                SPacketStartCollapse.CODEC,
                SPacketStartCollapse::handle
        );

        // 3. 服务端发给客户端：触发假死机/蓝屏界面
        registrar.playToClient(
                SPacketFakeCrash.TYPE,
                SPacketFakeCrash.CODEC,
                SPacketFakeCrash::handle
        );
    }

    public static void sendToServer(PeacefulPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ContractPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ClearAreaPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(OpenTradePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ToggleCompanionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(RequestActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(SyncHeroVisitPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(DesolateAreaPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(FlattenAreaPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ClaimRewardPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ToggleSkinPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(TriggerEternalOathPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(CleaveSkillPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(OpenWardrobePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(SyncRewardsPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(SavePosePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(SavePosePacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    // ============================================
    // [新增] 试炼崩坏演出相关发包方法
    // ============================================
    public static void sendToServer(CPacketCollapseFinished packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(SPacketStartCollapse packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(SPacketFakeCrash packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    // 1.21.1 的通用群体发包：发送给所有追踪该实体的玩家（包括玩家自己）
    public static void sendToTracking(CustomPacketPayload packet, Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
    }
}