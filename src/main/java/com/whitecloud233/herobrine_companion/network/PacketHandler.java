package com.whitecloud233.herobrine_companion.network;

import net.minecraft.server.level.ServerPlayer;
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
        // Removed QuestActionPacket
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
                SwitchSkinPacket.TYPE,
                SwitchSkinPacket.STREAM_CODEC,
                SwitchSkinPacket::handle
        );
        // [新增] 注册触发永恒誓约的数据包 (S2C)
        registrar.playToClient(
                TriggerEternalOathPacket.TYPE,
                TriggerEternalOathPacket.STREAM_CODEC,
                TriggerEternalOathPacket::handle
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
    
    // Removed sendToServer(QuestActionPacket)

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

    public static void sendToServer(SwitchSkinPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    // [新增] 发送给玩家
    public static void sendToPlayer(TriggerEternalOathPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
