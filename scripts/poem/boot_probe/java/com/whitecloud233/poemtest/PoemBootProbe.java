package com.whitecloud233.poemtest;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/** Only loaded by verify_client_boot.gradle; never included in the distributable mod. */
@Mod("poem_boot_probe")
public final class PoemBootProbe {
    private int ticks, readyTicks;
    private boolean finished;
    private boolean loadingWarningsAcknowledged;
    private boolean optionalVfxNoticeDismissed;
    private List<String> loadingMessages = List.of();

    public PoemBootProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (mc.screen != null && mc.getOverlay() == null
                && mc.screen.getClass().getName().equals("com.hm.efn.client.gui.WarningEventHandler$1")) {
            // Nightfall's optional AAA Particles recommendation can cover an otherwise loaded title screen.
            // Use its Ignore button only in this isolated run; never follow the download link.
            String ignoreLabel = Component.translatable("efn.warning.ignore").getString();
            Button ignore = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.visible && button.getMessage().getString().equals(ignoreLabel))
                    .findFirst().orElse(null);
            if (ignore == null || optionalVfxNoticeDismissed) {
                finish(mc, false, "Nightfall optional VFX notice has no usable Ignore button");
                return;
            }
            optionalVfxNoticeDismissed = true;
            LogUtils.getLogger().info("POEM_CLIENT_BOOT_NOTICE: Dismissing Nightfall's optional AAA Particles recommendation");
            ignore.onPress();
            return;
        }
        if (mc.screen instanceof LoadingErrorScreen loading && mc.getOverlay() == null) {
            loadingMessages = loading.children().stream()
                    .filter(LoadingErrorScreen.LoadingEntryList.class::isInstance)
                    .map(LoadingErrorScreen.LoadingEntryList.class::cast)
                    .flatMap(list -> list.children().stream())
                    .map(entry -> entry.getNarration().getString()).toList();
            String continueLabel = FMLTranslations.parseMessage("fml.button.continue.launch");
            Button continueLaunch = loading.children().stream().filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.active && button.visible
                            && button.getMessage().getString().equals(continueLabel))
                    .findFirst().orElse(null);
            // NeoForge only supplies this button when its mod-loading error list is empty.
            if (continueLaunch == null || loadingWarningsAcknowledged) {
                finish(mc, false, "NeoForge loading screen prevents launch: " + loadingMessages);
                return;
            }
            loadingWarningsAcknowledged = true;
            LogUtils.getLogger().info("POEM_CLIENT_BOOT_WARNINGS: {}", loadingMessages);
            continueLaunch.onPress();
            return;
        }
        if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            if (++readyTicks < 20) return;
            boolean facing = Arrays.stream(LivingEntityRenderer.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("poem$attackFacing") && m.getReturnType() == float.class);
            boolean charge = Arrays.stream(Player.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("poem$cutStrength"));
            finish(mc, facing && charge, facing && charge ? "Title screen loaded with both Poem mixins applied"
                    : "Poem mixin callbacks missing from transformed game classes");
        } else {
            readyTicks = 0;
            if (ticks > 1200) finish(mc, false, "Title screen did not finish loading");
        }
    }

    private void finish(Minecraft mc, boolean success, String reason) {
        finished = true;
        try {
            Path report = Path.of(System.getProperty("poem.bootProbe.report"));
            Files.createDirectories(report.getParent());
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "status", success ? "passed" : "failed", "reason", reason,
                    "screen", mc.screen == null ? "none" : mc.screen.getClass().getName(),
                    "overlay_cleared", mc.getOverlay() == null,
                    "loading_warnings_acknowledged", loadingWarningsAcknowledged,
                    "optional_vfx_notice_dismissed", optionalVfxNoticeDismissed,
                    "loading_messages", loadingMessages,
                    "mods", ModList.get().getMods().stream().map(m -> m.getModId()).sorted().toList(),
                    "ticks", ticks)));
            LogUtils.getLogger().info("POEM_CLIENT_BOOT_{}: {}", success ? "OK" : "FAILED", reason);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot write Poem boot probe report", error);
        } finally {
            // Stop only this isolated test client; no world is opened or saved.
            mc.stop();
        }
    }
}
