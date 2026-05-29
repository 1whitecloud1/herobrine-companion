package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.OpenCookSelectionPacket;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.SelectCookOptionPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.SPacketWorldRendCinematic;
import com.whitecloud233.modid.herobrine_companion.network.ai.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(HerobrineCompanion.MODID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, PeacefulPacket.class, PeacefulPacket::encode, PeacefulPacket::new, PeacefulPacket::handle);
        INSTANCE.registerMessage(id++, ContractPacket.class, ContractPacket::encode, ContractPacket::new, ContractPacket::handle);
        INSTANCE.registerMessage(id++, ClearAreaPacket.class, ClearAreaPacket::encode, ClearAreaPacket::new, ClearAreaPacket::handle);
        INSTANCE.registerMessage(id++, OpenTradePacket.class, OpenTradePacket::encode, OpenTradePacket::new, OpenTradePacket::handle);
        INSTANCE.registerMessage(id++, ToggleCompanionPacket.class, ToggleCompanionPacket::encode, ToggleCompanionPacket::new, ToggleCompanionPacket::handle);
        INSTANCE.registerMessage(id++, SyncHeroVisitPacket.class, SyncHeroVisitPacket::encode, SyncHeroVisitPacket::new, SyncHeroVisitPacket::handle);
        INSTANCE.registerMessage(id++, RequestActionPacket.class, RequestActionPacket::encode, RequestActionPacket::new, RequestActionPacket::handle);
        INSTANCE.registerMessage(id++, DesolateAreaPacket.class, DesolateAreaPacket::encode, DesolateAreaPacket::new, DesolateAreaPacket::handle);
        INSTANCE.registerMessage(id++, FlattenAreaPacket.class, FlattenAreaPacket::encode, FlattenAreaPacket::new, FlattenAreaPacket::handle);
        INSTANCE.registerMessage(id++, ClaimRewardPacket.class, ClaimRewardPacket::encode, ClaimRewardPacket::new, ClaimRewardPacket::handle);
        INSTANCE.registerMessage(id++, ToggleSkinPacket.class, ToggleSkinPacket::encode, ToggleSkinPacket::new, ToggleSkinPacket::handle);
        INSTANCE.registerMessage(id++, SyncHeroCosmeticsPacket.class, SyncHeroCosmeticsPacket::encode, SyncHeroCosmeticsPacket::new, SyncHeroCosmeticsPacket::handle);
        INSTANCE.registerMessage(id++, SyncRewardsPacket.class, SyncRewardsPacket::encode, SyncRewardsPacket::new, SyncRewardsPacket::handle);
        INSTANCE.registerMessage(id++, TriggerEternalOathPacket.class, TriggerEternalOathPacket::encode, TriggerEternalOathPacket::new, TriggerEternalOathPacket::handle);
        INSTANCE.registerMessage(id++, CleaveSkillPacket.class, CleaveSkillPacket::encode, CleaveSkillPacket::new, CleaveSkillPacket::handle);
        // 注意：原代码最后一行没有用 id++，如果是继续添加，请确保 ID 自增
        INSTANCE.registerMessage(id++, OpenWardrobePacket.class, OpenWardrobePacket::toBytes, OpenWardrobePacket::new, OpenWardrobePacket::handle);

        // [新增] 注册挑战模式数据包
        // [修复] 补上 StartChallengePacket 的 id++，否则会覆盖
        INSTANCE.registerMessage(id++, StartChallengePacket.class, StartChallengePacket::encode, StartChallengePacket::new, StartChallengePacket::handle);

        // [新增] 注册苍白雷电包
        INSTANCE.registerMessage(id++, PaleLightningPacket.class, PaleLightningPacket::encode, PaleLightningPacket::new, PaleLightningPacket::handle);
        // [新增] 注册苍白雷电弧包
        INSTANCE.registerMessage(id++, PaleLightningArcPacket.class, PaleLightningArcPacket::encode, PaleLightningArcPacket::new, PaleLightningArcPacket::handle);
        INSTANCE.registerMessage(id++, DestructionGodLightningPacket.class, DestructionGodLightningPacket::encode, DestructionGodLightningPacket::new, DestructionGodLightningPacket::handle);
        INSTANCE.registerMessage(id++, DestructionGodLightningArcPacket.class, DestructionGodLightningArcPacket::encode, DestructionGodLightningArcPacket::new, DestructionGodLightningArcPacket::handle);
        INSTANCE.registerMessage(id++, DestructionGodOrbPacket.class, DestructionGodOrbPacket::encode, DestructionGodOrbPacket::new, DestructionGodOrbPacket::handle);
        INSTANCE.registerMessage(id++, DestructionGodThunderSkyNetPacket.class, DestructionGodThunderSkyNetPacket::encode, DestructionGodThunderSkyNetPacket::new, DestructionGodThunderSkyNetPacket::handle);
        INSTANCE.registerMessage(id++, DestructionGodFaultSplitPacket.class, DestructionGodFaultSplitPacket::encode, DestructionGodFaultSplitPacket::new, DestructionGodFaultSplitPacket::handle);
        // [原代码修复] 注册姿势同步数据包 (把 id 改成 id++)
        INSTANCE.registerMessage(id++, SavePosePacket.class, SavePosePacket::encode, SavePosePacket::new, SavePosePacket::handle);

        // [新增] 注册 AI 观察环境数据包
        INSTANCE.registerMessage(id++, AIObservationPacket.class, AIObservationPacket::encode, AIObservationPacket::new, AIObservationPacket::handle);
        INSTANCE.registerMessage(id++, HeroAIActionPacket.class, HeroAIActionPacket::encode, HeroAIActionPacket::new, HeroAIActionPacket::handle);
        INSTANCE.registerMessage(id++, ToggleBattleModePacket.class, ToggleBattleModePacket::encode, ToggleBattleModePacket::new, ToggleBattleModePacket::handle);
// [新增] 注册第一阶段：虚晃一枪（世界崩坏）数据包
        // 【新增】：你漏掉了这个崩坏演出数据包！！
        INSTANCE.registerMessage(id++, com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketStartCollapse.class,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketStartCollapse::toBytes,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketStartCollapse::new,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketStartCollapse::handle);
        // 【新增】：注册第三阶段，假死机 Meta 数据包
        INSTANCE.registerMessage(id++, com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketFakeCrash.class,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketFakeCrash::toBytes,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketFakeCrash::new,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketFakeCrash::handle);
        INSTANCE.registerMessage(id++, SPacketWorldRendCinematic.class,
                SPacketWorldRendCinematic::toBytes,
                SPacketWorldRendCinematic::new,
                SPacketWorldRendCinematic::handle);
        // 【新增】：注册客户端通知服务器演出结束的包
        INSTANCE.registerMessage(id++, com.whitecloud233.modid.herobrine_companion.client.fight.network.CPacketCollapseFinished.class,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.CPacketCollapseFinished::toBytes,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.CPacketCollapseFinished::new,
                com.whitecloud233.modid.herobrine_companion.client.fight.network.CPacketCollapseFinished::handle);
        // 👇 【新增】：注册召唤实体、传送到实体身边的两个数据包
        INSTANCE.registerMessage(id++, SummonHeroPacket.class,
                SummonHeroPacket::toBytes, SummonHeroPacket::new, SummonHeroPacket::handle);
        INSTANCE.registerMessage(id++, TeleportToHeroPacket.class,
                TeleportToHeroPacket::toBytes, TeleportToHeroPacket::new, TeleportToHeroPacket::handle);
        INSTANCE.registerMessage(id++, HeroPunishmentPacket.class,
                HeroPunishmentPacket::encode, HeroPunishmentPacket::new, HeroPunishmentPacket::handle);
        INSTANCE.registerMessage(id++, OpenCookSelectionPacket.class,
                OpenCookSelectionPacket::encode, OpenCookSelectionPacket::new, OpenCookSelectionPacket::handle);
        INSTANCE.registerMessage(id++, SelectCookOptionPacket.class,
                SelectCookOptionPacket::encode, SelectCookOptionPacket::new, SelectCookOptionPacket::handle);
        INSTANCE.registerMessage(id++, OpenHeroChatPacket.class,
                OpenHeroChatPacket::encode, OpenHeroChatPacket::new, OpenHeroChatPacket::handle);
        INSTANCE.registerMessage(id++, UpdateClientLanguagePacket.class,
                UpdateClientLanguagePacket::encode, UpdateClientLanguagePacket::new, UpdateClientLanguagePacket::handle);
        INSTANCE.registerMessage(id++, AppendCrossChatHistoryPacket.class,
                AppendCrossChatHistoryPacket::encode, AppendCrossChatHistoryPacket::new, AppendCrossChatHistoryPacket::handle);
        INSTANCE.registerMessage(id++, PresentCrossChatAiLinePacket.class,
                PresentCrossChatAiLinePacket::encode, PresentCrossChatAiLinePacket::new, PresentCrossChatAiLinePacket::handle);
        INSTANCE.registerMessage(id++, SyncCrossChatStatePacket.class,
                SyncCrossChatStatePacket::encode, SyncCrossChatStatePacket::new, SyncCrossChatStatePacket::handle);
        INSTANCE.registerMessage(id++, OpenCrossChatInvitePacket.class,
                OpenCrossChatInvitePacket::encode, OpenCrossChatInvitePacket::new, OpenCrossChatInvitePacket::handle);
        INSTANCE.registerMessage(id++, OpenCrossSessionHubPacket.class,
                OpenCrossSessionHubPacket::encode, OpenCrossSessionHubPacket::new, OpenCrossSessionHubPacket::handle);
        INSTANCE.registerMessage(id++, RequestCrossChatSessionPacket.class,
                RequestCrossChatSessionPacket::encode, RequestCrossChatSessionPacket::new, RequestCrossChatSessionPacket::handle);
        INSTANCE.registerMessage(id++, RespondCrossChatInvitePacket.class,
                RespondCrossChatInvitePacket::encode, RespondCrossChatInvitePacket::new, RespondCrossChatInvitePacket::handle);
        INSTANCE.registerMessage(id++, SetCrossChatPermissionPacket.class,
                SetCrossChatPermissionPacket::encode, SetCrossChatPermissionPacket::new, SetCrossChatPermissionPacket::handle);
        INSTANCE.registerMessage(id++, SetCrossChatAutoChatPacket.class,
                SetCrossChatAutoChatPacket::encode, SetCrossChatAutoChatPacket::new, SetCrossChatAutoChatPacket::handle);
        INSTANCE.registerMessage(id++, SetCrossChatAutoTurnLimitPacket.class,
                SetCrossChatAutoTurnLimitPacket::encode, SetCrossChatAutoTurnLimitPacket::new, SetCrossChatAutoTurnLimitPacket::handle);
        INSTANCE.registerMessage(id++, SendCrossChatMessagePacket.class,
                SendCrossChatMessagePacket::encode, SendCrossChatMessagePacket::new, SendCrossChatMessagePacket::handle);
        INSTANCE.registerMessage(id++, SendCrossChatHbMessagePacket.class,
                SendCrossChatHbMessagePacket::encode, SendCrossChatHbMessagePacket::new, SendCrossChatHbMessagePacket::handle);
        INSTANCE.registerMessage(id++, CloseCrossChatSessionPacket.class,
                CloseCrossChatSessionPacket::encode, CloseCrossChatSessionPacket::new, CloseCrossChatSessionPacket::handle);
        INSTANCE.registerMessage(id++, com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket.class,
                com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket::encode,
                com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket::new,
                com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket::handle);
        INSTANCE.registerMessage(id++, HeroCrossChatResultPacket.class,
                HeroCrossChatResultPacket::encode, HeroCrossChatResultPacket::new, HeroCrossChatResultPacket::handle);
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
