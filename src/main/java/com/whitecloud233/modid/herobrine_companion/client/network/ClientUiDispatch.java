package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.modid.herobrine_companion.client.gui.FakeCrashScreen;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookSelectionScreen;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * 收包后需要在客户端打开 UI 的处理入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发。
 */
public final class ClientUiDispatch {
    private ClientUiDispatch() {
    }

    public static void openCookSelection(int heroId, BlockPos cookwarePos, List<HeroCookingCompat.CookOptionView> options) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new HeroCookSelectionScreen(heroId, cookwarePos, options, minecraft.screen));
    }

    public static void openHeroChat(boolean hbInputMode) {
        ClientHooks.openHeroChatFromCommand(hbInputMode);
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        ClientHooks.openCrossChatInvite(requesterId, requesterName);
    }

    public static void openCrossSessionHub(int entityId) {
        ClientHooks.openCrossSessionHub(entityId);
    }

    public static void openFakeCrash() {
        FakeCrashScreen.open();
    }

    public static void triggerEternalOath() {
        ClientHooks.triggerEternalOath();
    }
}
