package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HerobrineCompanion.MODID, "main"),
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
        INSTANCE.registerMessage(id, PaleLightningArcPacket.class, PaleLightningArcPacket::encode, PaleLightningArcPacket::new, PaleLightningArcPacket::handle);
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