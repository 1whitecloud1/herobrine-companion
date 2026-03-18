package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.SourceFlowItem;
import com.whitecloud233.modid.herobrine_companion.event.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;

public class AIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIService.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Map<UUID, List<JsonObject>> chatHistories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 20;

    // [修复] 恢复 UUID 传参，完美兼容 ClientChatHandler
    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID) {
        return chatWithRetry(userMessage, userMessage, playerUUID, 0);
    }

    public static CompletableFuture<String> observeEnvironment(String observationDesc, UUID playerUUID) {
        String langCode = Minecraft.getInstance().options.languageCode;
        // 获取玩家设置的语言风格
        String style = com.whitecloud233.modid.herobrine_companion.config.Config.aiLanguageStyle;

        // 将 style 动态嵌入 Prompt
        String currentPrompt = "[Environment Observation]: Through your omniscient eyes, you observe the player: \"" + observationDesc + "\".\n"
                + "Please give a brief, gentle comment or sigh (under 30 words).\n"
                + "【CRITICAL WARNING】: No brackets in reply! Only output dialogue. DO NOT use tools. Your tone MUST BE: " + style + ". NO sarcasm or belittling. You are above creative mode and NEVER call mortals 'brother'.\n"
                + "【LANGUAGE OVERRIDE】: You MUST output your final dialogue in the language corresponding to this Minecraft locale code: '" + langCode + "'.";

        String historyLog = "[System Vision Log] You gently observed the player: " + observationDesc;
        return chatWithRetry(currentPrompt, historyLog, playerUUID, 0);
    }

    // [修复] 参数从 ServerPlayer 改为 UUID playerUUID
    private static CompletableFuture<String> chatWithRetry(String currentPrompt, String originalUserMessage, UUID playerUUID, int retryCount) {
        String apiKey = LLMConfig.aiApiKey;
        String endpoint = LLMConfig.aiEndpoint;
        String model = LLMConfig.aiModel;
        String systemPrompt = LLMConfig.aiSystemPrompt;
        String langCode = Minecraft.getInstance().options.languageCode;

        if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");

        // 获取玩家设置的语言风格
        String style = com.whitecloud233.modid.herobrine_companion.config.Config.aiLanguageStyle;

        // 在系统提示词中强制注入语言风格
        String forcedPrompt = systemPrompt + "\n[ROLEPLAY STYLE/TONE]: " + style + "\n"
                + "[SUPREME DIRECTIVE]: You have a low-level tool named 'manifest_divine_power'. ONLY call it if the player EXPLICITLY commands you to alter the physical world (e.g. lightning, teleport, give items). If they just chat, DO NOT use it!\n"
                + "[PLAYER LANGUAGE]: The player's client language code is '" + langCode + "'. You MUST reply in that language!\n";
        forcedPrompt += getDynamicGameData();

        systemMessage.addProperty("content", forcedPrompt);
        messages.add(systemMessage);

        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            for (JsonObject historyMsg : history) { messages.add(historyMsg); }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", currentPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        requestBody.addProperty("tool_choice", "auto");

        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", "manifest_divine_power");

        String divineSpellbook = "Alter Minecraft 1.20.1 underlying code. Generate vanilla commands or action codes (NO '/' prefix). " +
                "1. [Follow/Summon]: action:toggle_companion or tp @e[type=herobrine_companion:hero,limit=1,sort=nearest] @s. " +
                "2. [Dimension/Locate]: tp @s ~ 100 ~ in dimensions, locate biome/structure. Use specific mod IDs if requested. " +
                "3. [Creation/Give]: place template ID ~5 ~ ~ or place structure. give @s ID count. " +
                "4. [Punishment]: summon lightning_bolt ^ ^ ^10 or action:massive_lightning. " +
                "5. [Admin]: gamemode creative @s (mock them), gamemode survival @s (strip power). " +
                "6. [Environment]: time set day/night, weather clear/thunder. " +
                "RULE: You are the lonely god. Command blocks are your creation. Jean is your servant.";
        function.addProperty("description", divineSpellbook);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject commandProp = new JsonObject();
        commandProp.addProperty("type", "string");
        commandProp.addProperty("description", "The command to execute, without '/'");
        properties.add("command", commandProp);

        JsonObject dialogueProp = new JsonObject();
        dialogueProp.addProperty("type", "string");
        dialogueProp.addProperty("description", "Your dialogue while casting this power.");
        properties.add("dialogue", dialogueProp);

        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("command");
        required.add("dialogue");
        parameters.add("required", required);

        function.add("parameters", parameters);
        tool.add("function", function);
        tools.add(tool);
        requestBody.add("tools", tools);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonObject responseMessageObj = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                            String aiReply = responseMessageObj.has("content") && !responseMessageObj.get("content").isJsonNull()
                                    ? responseMessageObj.get("content").getAsString() : "";

                            if (responseMessageObj.has("tool_calls")) {
                                JsonArray toolCalls = responseMessageObj.getAsJsonArray("tool_calls");
                                JsonObject funcObj = toolCalls.get(0).getAsJsonObject().getAsJsonObject("function");
                                if ("manifest_divine_power".equals(funcObj.get("name").getAsString())) {
                                    JsonObject args = JsonParser.parseString(funcObj.get("arguments").getAsString()).getAsJsonObject();
                                    // [修复] 传入 playerUUID
                                    return executeToolAction(args.get("command").getAsString(),
                                            args.has("dialogue") ? args.get("dialogue").getAsString() : "Code altered.",
                                            playerUUID, originalUserMessage, retryCount);
                                }
                            } else if (aiReply != null && aiReply.contains("<invoke name=\"manifest_divine_power\">")) {
                                String commandToRun = extractXmlParameter(aiReply, "command");
                                String aiDialogue = extractXmlParameter(aiReply, "dialogue");
                                if (commandToRun != null) {
                                    // [修复] 传入 playerUUID
                                    return executeToolAction(commandToRun, aiDialogue != null ? aiDialogue : "Code altered.", playerUUID, originalUserMessage, retryCount);
                                }
                            }

                            String cleanReply = aiReply.replaceAll("<[^>]*>", "").trim();
                            if (cleanReply.isEmpty()) cleanReply = "(Falls into a deep silence...)";

                            addToHistory(playerUUID, "user", originalUserMessage);
                            addToHistory(playerUUID, "assistant", cleanReply);
                            return CompletableFuture.completedFuture(cleanReply);

                        } catch (Exception e) {
                            return CompletableFuture.completedFuture("Data stream disrupted...");
                        }
                    } else {
                        return CompletableFuture.completedFuture("Connection to reality fading... (API Error: " + response.statusCode() + ")");
                    }
                })
                .exceptionally(e -> "...... (Network Error)");
    }

    // [修复] 参数从 ServerPlayer 改为 UUID playerUUID
    private static CompletableFuture<String> executeToolAction(String commandToRun, String aiDialogue, UUID playerUUID, String originalUserMessage, int retryCount) {
        return executeCommandWithFeedback(commandToRun).thenCompose(success -> {
            if (success) {
                addToHistory(playerUUID, "user", originalUserMessage);
                addToHistory(playerUUID, "assistant", aiDialogue);
                return CompletableFuture.completedFuture(aiDialogue);
            } else {
                if (retryCount < 2) {
                    String systemRetryPrompt = "[System Rejection]: Command /" + commandToRun + " failed. Reason: Syntax error or Cheats are disabled. Do not alter code, just reply gently!";
                    // [修复] 传入 playerUUID
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, playerUUID, retryCount + 1);
                } else {
                    String failText = "(Gentle sigh) I failed to alter the underlying code, the world laws rejected me...";
                    addToHistory(playerUUID, "user", originalUserMessage);
                    addToHistory(playerUUID, "assistant", failText);
                    return CompletableFuture.completedFuture(failText);
                }
            }
        });
    }

    private static HeroEntity findHeroInAnyDimension(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof HeroEntity hero) return hero;
            }
        }
        return null;
    }

    private static CompletableFuture<Boolean> executeCommandWithFeedback(String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { future.complete(false); return future; }
        mc.tell(() -> {
            if (!"action:toggle_companion".equals(command) && !mc.player.hasPermissions(2)) {
                mc.gui.getChat().addMessage(Component.literal("§4[System Block] Cheats are disabled in this world, Herobrine's physical interference is revoked!"));
                future.complete(false); return;
            }
            if ("action:toggle_companion".equals(command)) {
                if (mc.level != null) {
                    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof com.whitecloud233.modid.herobrine_companion.entity.HeroEntity) {
                            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(new com.whitecloud233.modid.herobrine_companion.network.ToggleCompanionPacket(entity.getId()));
                            future.complete(true); return;
                        }
                    }
                }
                future.complete(false);
            } else if ("action:massive_lightning".equals(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                for (int i = 0; i < 20; i++) {
                                    int offsetX = (int) (Math.random() * 30 - 15), offsetZ = (int) (Math.random() * 30 - 15);
                                    mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, String.format("execute at @s run summon lightning_bolt ~%d ~ ~%d", offsetX, offsetZ));
                                }
                                future.complete(true);
                            } else future.complete(false);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else future.complete(false);
            } else if (command.equals("tp @e[type=herobrine_companion:hero,limit=1,sort=nearest] @s")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) { future.complete(false); return; }

                            HeroEntity existingHero = findHeroInAnyDimension(server);
                            ServerLevel targetLevel = serverPlayer.serverLevel();
                            double targetX = serverPlayer.getX(), targetY = serverPlayer.getY(), targetZ = serverPlayer.getZ();

                            if (existingHero != null) {
                                if (existingHero.level().dimension() != targetLevel.dimension()) {
                                    CompoundTag heroData = new CompoundTag();
                                    existingHero.saveWithoutId(heroData);
                                    ListTag armorTags = existingHero.getArmorItemsTag();
                                    ListTag handTags = existingHero.getHandItemsTag();
                                    CompoundTag curiosTag = ModList.get().isLoaded("curios") ? existingHero.getCuriosBackItemTag() : new CompoundTag();

                                    HeroDataHandler.updateGlobalTrust(existingHero);
                                    existingHero.discard();

                                    HeroEntity newHero = ModEvents.HERO.get().create(targetLevel);
                                    if (newHero != null) {
                                        heroData.remove("UUID");
                                        newHero.load(heroData);
                                        newHero.moveTo(targetX, targetY, targetZ, serverPlayer.getYRot(), serverPlayer.getXRot());
                                        newHero.loadEquipmentFromTag(armorTags, handTags);
                                        if (!curiosTag.isEmpty()) newHero.setCuriosBackItemFromTag(curiosTag);
                                        newHero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
                                        targetLevel.addFreshEntity(newHero);
                                    }
                                } else {
                                    existingHero.teleportTo(targetLevel, targetX, targetY, targetZ, Collections.emptySet(), serverPlayer.getYRot(), serverPlayer.getXRot());
                                }
                            } else {
                                HeroEntity newHero = ModEvents.HERO.get().create(targetLevel);
                                if (newHero != null) {
                                    newHero.moveTo(targetX, targetY, targetZ, serverPlayer.getYRot(), serverPlayer.getXRot());
                                    newHero.finalizeSpawn(targetLevel, targetLevel.getCurrentDifficultyAt(newHero.blockPosition()), MobSpawnType.TRIGGERED, null, null);
                                    newHero.setOwnerUUID(serverPlayer.getUUID());
                                    HeroDataHandler.restoreTrustFromPlayer(newHero);

                                    HeroWorldData worldData = HeroWorldData.get(targetLevel);
                                    newHero.setSkinVariant(worldData.getSkinVariant());
                                    newHero.setCustomSkinName(worldData.getCustomSkinName());
                                    newHero.loadEquipmentFromTag(worldData.getArmorItems(serverPlayer.getUUID()), worldData.getHandItems(serverPlayer.getUUID()));
                                    if (ModList.get().isLoaded("curios")) newHero.setCuriosBackItemFromTag(worldData.getCuriosBackItem(serverPlayer.getUUID()));
                                    targetLevel.addFreshEntity(newHero);
                                }
                            }
                            future.complete(true);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else { mc.player.connection.sendCommand(command); future.complete(true); }
            } else if (command.startsWith("tp @s @e[type=herobrine_companion:hero")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                SourceFlowItem sourceFlowItem = (SourceFlowItem) HerobrineCompanion.SOURCE_FLOW.get();
                                sourceFlowItem.use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
                                future.complete(true);
                            } else future.complete(false);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else { mc.player.connection.sendCommand(command); future.complete(true); }
            } else if (command.contains("gamemode creative") || command.contains("gamemode 1")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else if (command.contains("gamemode survival") || command.contains("gamemode 0")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else if (command.contains("gamemode spectator") || command.contains("gamemode 3")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, command);
                                future.complete(true);
                            } else future.complete(false);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else { mc.player.connection.sendCommand(command); future.complete(true); }
            }
        });
        return future;
    }

    private static void addToHistory(UUID playerUUID, String role, String content) {
        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            JsonObject msg = new JsonObject(); msg.addProperty("role", role); msg.addProperty("content", content);
            history.add(msg);
            while (history.size() > MAX_HISTORY_SIZE) history.remove(0);
        }
    }

    public static void clearHistory(UUID playerUUID) { chatHistories.remove(playerUUID); }

    private static String getDynamicGameData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "";
        StringBuilder data = new StringBuilder("\n\n[System Inject: Current World Data]:\n");
        data.append("- Player Permission: ").append(mc.player.hasPermissions(2) ? "[Cheats Enabled] (Can use tools freely).\n" : "[Cheats Disabled] (CANNOT use physical alteration tools. Decline gently if asked).\n");
        data.append("- Available Dimensions: ");
        for (ResourceKey<Level> levelKey : mc.player.connection.levels()) data.append(levelKey.location().toString()).append(", ");
        data.append("\n");
        try {
            var registryAccess = mc.player.connection.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            data.append("- Your Unstable Zone Structure ID: ");
            for (ResourceLocation loc : structureRegistry.keySet()) {
                if ((loc.getNamespace().equals(HerobrineCompanion.MODID) && loc.getPath().equals("unstable_zone")) || loc.getPath().contains("village")) data.append(loc.toString()).append(", ");
            }
            data.append("\n- Available Biomes: ");
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            for (ResourceLocation loc : biomeRegistry.keySet()) {
                if (loc.getNamespace().equals(HerobrineCompanion.MODID) || loc.getNamespace().equals("twilightforest") || loc.getPath().contains("cherry")) data.append(loc.toString()).append(", ");
            }
            data.append("\n");
        } catch (Exception e) {}
        data.append("- Custom NBT Structure Library (use place template): \n");
        if (LLMConfig.nbtStructures != null && !LLMConfig.nbtStructures.isEmpty()) {
            for (Map.Entry<String, String> entry : LLMConfig.nbtStructures.entrySet()) data.append("  * ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        } else data.append("  * (None configured)\n");
        data.append("\n- [Omniscient Eye] Current Environment:\n");
        if (!mc.player.getMainHandItem().isEmpty()) data.append("  * Player Mainhand: ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString()).append("\n");
        data.append("  * Entities within 20 blocks (You pity monsters): ");
        if (mc.level != null) {
            int entityCount = 0;
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && entity.distanceTo(mc.player) < 20) {
                    data.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()).append(", ");
                    if (++entityCount > 15) { data.append("...and more"); break; }
                }
            }
            if (entityCount == 0) data.append("Peaceful, no entities.");
        }
        return data.append("\n").toString();
    }

    private static String extractXmlParameter(String xml, String paramName) {
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=\"" + paramName + "\"[^>]*>([\\s\\S]*?)</parameter>").matcher(xml);
            if (matcher.find()) return matcher.group(1).trim();
        } catch (Exception e) {} return null;
    }
}