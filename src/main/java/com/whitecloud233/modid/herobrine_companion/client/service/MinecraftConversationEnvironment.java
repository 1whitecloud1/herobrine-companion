package com.whitecloud233.modid.herobrine_companion.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Locale;

final class MinecraftConversationEnvironment implements ConversationEnvironment {
    @Override
    public String sessionKey() {
        Minecraft minecraft = Minecraft.getInstance();
        String world = null;
        if (minecraft.getSingleplayerServer() != null) {
            world = "local_" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null
                && !minecraft.getCurrentServer().ip.isBlank()) {
            world = "server_" + minecraft.getCurrentServer().ip;
        }
        return world == null ? null : world.replaceAll("[^a-zA-Z0-9\\-_]", "_").toLowerCase(Locale.ROOT);
    }

    @Override public Path sessionFile(String key) {
        return FMLPaths.CONFIGDIR.get().resolve("herobrine_companion/conversations").resolve(key + ".json");
    }

    @Override public Path exportDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("herobrine_companion/conversation_exports");
    }

    @Override public String defaultTitle() {
        return Component.translatable("gui.herobrine_companion.conversation_manager.default_name").getString();
    }
}
