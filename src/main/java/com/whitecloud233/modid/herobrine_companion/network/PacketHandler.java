package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    }


    public static void sendToServer(Object packet) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
