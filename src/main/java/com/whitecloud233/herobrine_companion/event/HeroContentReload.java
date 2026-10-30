package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.compat.kubejs.SafeKubeJSCaller;
import com.whitecloud233.herobrine_companion.network.HeroContentChannel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HeroContentReload {
    private HeroContentReload() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reload(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        if (ModList.get().isLoaded("kubejs")) {
            try {
                SafeKubeJSCaller.reloadRewards();
            } catch (LinkageError | RuntimeException exception) {
                LogUtils.getLogger().error("Failed to reload Herobrine rewards; keeping the previous definition", exception);
            }
        } else {
            HeroRewards.reset();
        }
        HeroTrades.invalidate();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof HeroEntity hero && hero.getTradingPlayer() != null) hero.resetOffers();
                }
            }
        }
        try {
            byte[] snapshot = HeroContentSnapshot.encode(server);
            if (event.getPlayer() != null) HeroContentChannel.send(event.getPlayer(), snapshot);
            else for (ServerPlayer player : event.getPlayerList().getPlayers()) HeroContentChannel.send(player, snapshot);
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Failed to synchronize Herobrine content", exception);
        }
    }
}
