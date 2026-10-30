package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.event.HeroContentSnapshot;
import com.whitecloud233.modid.herobrine_companion.event.HeroRewards;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.List;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ClientHeroContent {
    private static List<HeroRewards.Reward> rewards;
    private static MerchantOffers trades;
    private static long revision;
    private static byte[] pending;
    private static List<HeroRewards.Reward> defaults;
    private ClientHeroContent() { }

    public static List<HeroRewards.Reward> rewards() {
        if (rewards != null) return rewards;
        if (defaults == null) defaults = List.copyOf(HeroRewards.defaults());
        return defaults;
    }
    public static MerchantOffers trades() { return trades == null ? new MerchantOffers() : trades; }
    public static long revision() { return revision; }

    public static void accept(byte[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) { pending = data.clone(); return; }
        pending = null;
        try {
            HeroContentSnapshot snapshot = HeroContentSnapshot.decode(data);
            rewards = snapshot.rewards();
            trades = snapshot.trades();
            revision++;
            refreshJei();
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().error("Invalid Herobrine content snapshot", exception);
        }
    }
    private static void refreshJei() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) com.whitecloud233.modid.herobrine_companion.compat.jei.HerobrineJeiPlugin.refreshContent();
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pending != null && Minecraft.getInstance().level != null) accept(pending);
    }
    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { rewards = null; defaults = null; trades = null; revision++; }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { rewards = null; defaults = null; trades = null; pending = null; revision++; }
}
