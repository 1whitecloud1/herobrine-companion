package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AIGameContextSupport {
    private static final int MAX_SERVER_PLAYERS_IN_PROMPT = 12;
    private static final int MAX_NEARBY_PLAYERS_IN_PROMPT = 6;
    private static final double NEARBY_PLAYER_DETAIL_RADIUS = 96.0D;

    private AIGameContextSupport() {}

    static String getDynamicGameData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "";
        StringBuilder data = new StringBuilder("\n\n[System Inject: Current World Data]:\n");
        data.append("- Player Permission: ").append(mc.player.hasPermissions(2) ? "[Cheats Enabled] (Can use tools freely).\n" : "[Cheats Disabled] (CANNOT use physical alteration tools. Decline gently if asked).\n");
        data.append("- Available Dimensions: ");
        for (ResourceKey<Level> levelKey : mc.player.connection.levels()) data.append(levelKey.location()).append(", ");
        data.append("\n");
        try {
            var registryAccess = mc.player.connection.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            data.append("- Your Unstable Zone Structure ID: ");
            for (ResourceLocation loc : structureRegistry.keySet()) {
                if ((loc.getNamespace().equals(HerobrineCompanion.MODID) && loc.getPath().equals("unstable_zone")) || loc.getPath().contains("village")) data.append(loc).append(", ");
            }
            data.append("\n- Available Biomes: ");
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            for (ResourceLocation loc : biomeRegistry.keySet()) {
                if (loc.getNamespace().equals(HerobrineCompanion.MODID) || loc.getNamespace().equals("twilightforest") || loc.getPath().contains("cherry")) data.append(loc).append(", ");
            }
            data.append("\n");
        } catch (Exception ignored) {}
        data.append("- Custom NBT Structure Library (use place template): \n");
        if (LLMConfig.nbtStructures != null && !LLMConfig.nbtStructures.isEmpty()) {
            for (Map.Entry<String, String> entry : LLMConfig.nbtStructures.entrySet()) data.append("  * ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        } else data.append("  * (None configured)\n");
        appendOtherPlayerAwareness(data, mc);
        data.append("\n- [Omniscient Eye] Current Environment:\n");
        if (!mc.player.getMainHandItem().isEmpty()) data.append("  * Player Mainhand: ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem())).append("\n");
        data.append("  * Entities within 20 blocks (You pity monsters): ");
        if (mc.level != null) {
            int entityCount = 0;
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && entity.distanceTo(mc.player) < 20) {
                    data.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).append(", ");
                    if (++entityCount > 15) { data.append("...and more"); break; }
                }
            }
            if (entityCount == 0) data.append("Peaceful, no entities.");
        }
        return data.append("\n").toString();
    }

    private static void appendOtherPlayerAwareness(StringBuilder data, Minecraft mc) {
        data.append("\n- Other Players On This Server:\n");

        List<PlayerInfo> otherPlayers = new ArrayList<>();
        try {
            for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                if (info == null || info.getProfile() == null || info.getProfile().getName() == null) {
                    continue;
                }
                UUID uuid = info.getProfile().getId();
                if (uuid != null && uuid.equals(mc.player.getUUID())) {
                    continue;
                }
                otherPlayers.add(info);
            }
        } catch (Exception ignored) {
        }

        if (otherPlayers.isEmpty()) {
            data.append("  * (No other online players detected)\n");
            return;
        }

        int serverLimit = Math.min(MAX_SERVER_PLAYERS_IN_PROMPT, otherPlayers.size());
        for (int i = 0; i < serverLimit; i++) {
            PlayerInfo info = otherPlayers.get(i);
            String playerName = info.getProfile().getName();
            net.minecraft.world.entity.player.Player levelPlayer = mc.level == null ? null : mc.level.getPlayerByUUID(info.getProfile().getId());
            if (levelPlayer == null) {
                data.append("  * ").append(playerName).append(" | location=unknown\n");
                continue;
            }

            double distance = mc.player.distanceTo(levelPlayer);
            data.append("  * ").append(playerName)
                    .append(" | dim=").append(levelPlayer.level().dimension().location())
                    .append(" | distance=").append(String.format(java.util.Locale.ROOT, "%.1f", distance))
                    .append(" | health=").append(String.format(java.util.Locale.ROOT, "%.1f", levelPlayer.getHealth()))
                    .append("/").append(String.format(java.util.Locale.ROOT, "%.1f", levelPlayer.getMaxHealth()));

            if (levelPlayer.isCrouching()) {
                data.append(" | crouching");
            }
            if (levelPlayer.isSprinting()) {
                data.append(" | sprinting");
            }
            if (!levelPlayer.getMainHandItem().isEmpty()) {
                data.append(" | mainhand=")
                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(levelPlayer.getMainHandItem().getItem()));
            }
            data.append("\n");
        }

        if (otherPlayers.size() > serverLimit) {
            data.append("  * ...and ").append(otherPlayers.size() - serverLimit).append(" more online players\n");
        }

        if (mc.level != null) {
            data.append("- Nearby Players (within ").append((int) NEARBY_PLAYER_DETAIL_RADIUS).append(" blocks):\n");
            List<net.minecraft.world.entity.player.Player> nearbyPlayers = new ArrayList<>();
            for (net.minecraft.world.entity.player.Player player : mc.level.players()) {
                if (player == mc.player) {
                    continue;
                }
                if (player.distanceTo(mc.player) <= NEARBY_PLAYER_DETAIL_RADIUS) {
                    nearbyPlayers.add(player);
                }
            }

            if (nearbyPlayers.isEmpty()) {
                data.append("  * (No nearby players)\n");
                return;
            }

            int nearbyLimit = Math.min(MAX_NEARBY_PLAYERS_IN_PROMPT, nearbyPlayers.size());
            for (int i = 0; i < nearbyLimit; i++) {
                net.minecraft.world.entity.player.Player player = nearbyPlayers.get(i);
                data.append("  * ").append(player.getName().getString())
                        .append(" | distance=").append(String.format(java.util.Locale.ROOT, "%.1f", player.distanceTo(mc.player)))
                        .append(" | health=").append(String.format(java.util.Locale.ROOT, "%.1f", player.getHealth()))
                        .append("/").append(String.format(java.util.Locale.ROOT, "%.1f", player.getMaxHealth()));

                if (player.isCrouching()) {
                    data.append(" | crouching");
                }
                if (player.isSprinting()) {
                    data.append(" | sprinting");
                }
                if (!player.getMainHandItem().isEmpty()) {
                    data.append(" | mainhand=")
                            .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
                }
                data.append("\n");
            }

            if (nearbyPlayers.size() > nearbyLimit) {
                data.append("  * ...and ").append(nearbyPlayers.size() - nearbyLimit).append(" more nearby players\n");
            }
        }
    }
}
