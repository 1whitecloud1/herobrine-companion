package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.KubeJS.SafeKubeJSCaller;
import com.whitecloud233.modid.herobrine_companion.network.HeroContentChannel;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
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
