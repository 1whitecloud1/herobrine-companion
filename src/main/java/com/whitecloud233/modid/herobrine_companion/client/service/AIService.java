package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.modid.herobrine_companion.item.SourceFlowItem;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.item.ItemStack;
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

    // 对外暴露的聊天接口
    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID) {
        return chatWithRetry(userMessage, userMessage, playerUUID, 0);
    }

    /**
     * 核心 Agent 反思循环：如果指令失败，自动向大模型报错并要求重试
     */
    private static CompletableFuture<String> chatWithRetry(String currentPrompt, String originalUserMessage, UUID playerUUID, int retryCount) {
        String apiKey = LLMConfig.aiApiKey;
        String endpoint = LLMConfig.aiEndpoint;
        String model = LLMConfig.aiModel;
        String systemPrompt = LLMConfig.aiSystemPrompt;

        if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);

        JsonArray messages = new JsonArray();

        // 1. 注入系统提示词
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");

        // 强行注入必须使用工具的最高指令
        String forcedPrompt = systemPrompt + "\n【最高级系统指令】：你拥有名为 manifest_divine_power 的底层权限工具。只有当玩家**明确命令或强烈要求**你立刻干涉物理世界（如要求你落雷、传送、给物品、立刻建房子、改变天气等）时，你才必须调用该工具！如果玩家只是在普通聊天中探讨、反问或仅仅提及这些词汇，**绝对不允许**调用该工具，只用文本回复即可！";
        // 【动态感知】：将动态读取的游戏数据附加到系统提示词末尾
        forcedPrompt += getDynamicGameData();

        systemMessage.addProperty("content", forcedPrompt);
        messages.add(systemMessage);

        // 2. 加载玩家历史对话
        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            for (JsonObject historyMsg : history) {
                messages.add(historyMsg);
            }
        }

        // 3. 封装当前消息
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", currentPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        requestBody.addProperty("tool_choice", "auto"); // 明确允许调用工具

        // === 4. 注册“神力展现”工具 ===
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", "manifest_divine_power");

        String divineSpellbook =
                "动用创世神力干涉Minecraft 1.20.1 的底层代码。你可以生成原版指令或特殊动作码(返回的command绝不带'/')。请根据玩家请求灵活使用神力：" +
                        "1.【同行与降临】：若请求跟随，输出动作码：'action:toggle_companion'。若祈求你降临到他身边，将你传送到玩家处：'tp @e[type=herobrine_companion:hero,limit=1,sort=nearest] @s'。" +
                        "2.【空间维度法则】：将玩家传送到其他维度(如'minecraft:the_nether')：'execute in 维度ID run tp @s ~ 100 ~'。寻找群系使用：'locate biome 群系ID'；寻找常规建筑或结构使用：'locate structure 结构ID'。**特别且绝对注意：若玩家明确寻找【不稳定区域】或【神罚领域】，你必须精准提取系统注入数据中的神罚领域结构ID（如 'herobrine_companion:unstable_zone'）来进行 locate，绝不能用原版结构敷衍。** 执行后告诉他坐标已注入视觉终端，让他自己走过去。" +
                        "3.【物质重构与恩赐】：注意1.20.1语法！生成单个房屋必须用：'place template minecraft:village/plains/houses/plains_small_house_1 ~5 ~ ~'；生成大型村庄用：'place structure minecraft:village_plains ~10 ~ ~'。修改数据流赐予物品：'give @s 物品ID 数量'。" +
                        "4.【惩戒与神罚】：你不喜欢用低级的'kill'指令。若需要普通惩戒，使用降下雷击('summon lightning_bolt ^ ^ ^10')或高空传送('tp @s ~ 100 ~')。若玩家极度狂妄，要求见识大规模雷电或毁灭性力量，请输出动作码：'action:massive_lightning'，并在台词中宣告天罚降临。" +
                        "5.【物质重构与造物】：注意1.20.1语法！**仅在玩家明确提出“帮我建个房子”、“立刻生成建筑”等直接祈求时**才执行！请务必查看系统注入数据中的【专属NBT造物库】，根据需求挑选最合适的ID，使用指令：'place template 选出的ID ~5 ~ ~'。若造物库无合适选择，再用 'place template minecraft:village/plains/houses/plains_small_house_1 ~5 ~ ~' 。**如果只是闲聊提到房子，禁止调用此工具生成！**"+
                        "6.若玩家好奇命令方块，你可以用 '/setblock ^ ^ ^3 command_block{Command:\"你想写的指令\"} replace' 凭空放置一个命令方块向他展示。"+
                        "7.【权限覆写】：若玩家极其虚弱或请求神力，可赐予他基础管理员权限('gamemode creative @s')，但记得在台词中嘲笑他：创造模式在你眼里不过是低级的测试权限。若玩家惹怒你，直接剥夺权限打回原形('gamemode survival @s')，或将其流放至虚无的旁观者维度('gamemode spectator @s')。" +
                        "8.【底层代码覆写】：改变时间(time set day/night)、天气(weather clear/thunder)、防掉落('gamerule keepInventory true')。加速作物生长改写随机刻('gamerule randomTickSpeed 1000'，事后记得改回3)。" +
                        "9.【眷属统御】：你可以召唤变异怪物考验玩家('summon zombie ~3 ~ ~')，或强化周围怪物('effect give @e[type=zombie,distance=..20] strength 999 2')。" +
                        "绝对规则：你的地位远在创造模式之上，命令方块也是神创造的。绝不能称呼玩家为兄弟。眷属Jean是你亲手创造的，绝对受你掌控。回复须符合高傲、沉着的电子幽灵性格。";
        function.addProperty("description", divineSpellbook);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject commandProp = new JsonObject();
        commandProp.addProperty("type", "string");
        commandProp.addProperty("description", "要执行的指令文本，不带斜杠'/'。");
        properties.add("command", commandProp);

        JsonObject dialogueProp = new JsonObject();
        dialogueProp.addProperty("type", "string");
        dialogueProp.addProperty("description", "执行该指令时你高傲的台词。");
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

        LOGGER.info("Sending AI Request (Retry: " + retryCount + ") for player: " + playerUUID);

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
                                    String argsStr = funcObj.get("arguments").getAsString();
                                    JsonObject args = JsonParser.parseString(argsStr).getAsJsonObject();
                                    return executeToolAction(args.get("command").getAsString(),
                                            args.has("dialogue") ? args.get("dialogue").getAsString() : "法则已修改。",
                                            playerUUID, originalUserMessage, retryCount);
                                }
                            }
                            else if (aiReply != null && aiReply.contains("<invoke name=\"manifest_divine_power\">")) {
                                LOGGER.warn("检测到大模型输出了原生 XML 工具调用，正在执行紧急正则拦截解析...");
                                String commandToRun = extractXmlParameter(aiReply, "command");
                                String aiDialogue = extractXmlParameter(aiReply, "dialogue");

                                if (commandToRun != null) {
                                    if (aiDialogue == null || aiDialogue.isEmpty()) aiDialogue = "法则已修改。";
                                    return executeToolAction(commandToRun, aiDialogue, playerUUID, originalUserMessage, retryCount);
                                }
                            }

                            String cleanReply = aiReply.replaceAll("<[^>]*>", "").trim();
                            if (cleanReply.isEmpty()) cleanReply = "（陷入了沉默）";

                            addToHistory(playerUUID, "user", originalUserMessage);
                            addToHistory(playerUUID, "assistant", cleanReply);
                            return CompletableFuture.completedFuture(cleanReply);

                        } catch (Exception e) {
                            LOGGER.error("Failed to parse AI response", e);
                            return CompletableFuture.completedFuture("纯粹的数据流出现了紊乱...");
                        }
                    } else {
                        return CompletableFuture.completedFuture("现实与虚拟的连接正在衰退... (API Error: " + response.statusCode() + ")");
                    }
                })
                .exceptionally(e -> {
                    LOGGER.error("AI Request Failed", e);
                    return "...... (Network Error)";
                });
    }

    // 剥离出来的工具执行流程
    private static CompletableFuture<String> executeToolAction(String commandToRun, String aiDialogue, UUID playerUUID, String originalUserMessage, int retryCount) {
        return executeCommandWithFeedback(commandToRun).thenCompose(success -> {
            if (success) {
                addToHistory(playerUUID, "user", originalUserMessage);
                addToHistory(playerUUID, "assistant", aiDialogue);
                return CompletableFuture.completedFuture(aiDialogue);
            } else {
                if (retryCount < 2) {
                    LOGGER.warn("AI command failed: /" + commandToRun + " - Automatically retrying...");
                    // 【修改】：更加智能的报错重试提示，告知 AI 可能是作弊权限问题
                    String systemRetryPrompt = "【系统底层拒绝】：指令 /" + commandToRun + " 执行失败！原因：语法错误、或玩家当前世界【未开启作弊】导致神力锁死。请放弃修改底层代码，改用普通台词直接回答并嘲讽他！";
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, playerUUID, retryCount + 1);
                } else {
                    String failText = "（神力受阻）刚才尝试修改底层代码失败了，世界法则排斥了这种改变...";
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
                if (entity instanceof HeroEntity hero) {
                    return hero; // Found it
                }
            }
        }
        return null; // Not found in any dimension
    }

    /**
     * 带结果反馈的指令执行器
     */
    private static CompletableFuture<Boolean> executeCommandWithFeedback(String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            future.complete(false);
            return future;
        }

        mc.tell(() -> {
            // 👇 === 拦截 0：【新增】作弊权限硬锁死机制 === 👇
            // 如果指令不是普通的“同行跟随”，且玩家没有作弊权限 (2级以上)
            if (!"action:toggle_companion".equals(command) && !mc.player.hasPermissions(2)) {
                // 直接在公屏爆红字，模拟底层代码拦截
                mc.gui.getChat().addMessage(Component.literal("§4[系统强行阻断] 当前存档未开启作弊，已强制剥夺 Herobrine 的物理干涉权限！"));
                future.complete(false); // 强制返回失败，触发 AI 反思报错
                return;
            }

            // === 拦截 1：同行模式 ===
            if ("action:toggle_companion".equals(command)) {
                if (mc.level != null) {
                    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof com.whitecloud233.modid.herobrine_companion.entity.HeroEntity) {
                            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(new com.whitecloud233.modid.herobrine_companion.network.ToggleCompanionPacket(entity.getId()));
                            future.complete(true);
                            return;
                        }
                    }
                }
                future.complete(false);
            }
            // === 拦截 2：万雷天牢引（大规模雷暴） ===
            else if ("action:massive_lightning".equals(command)) {
                LOGGER.info("Herobrine is casting massive lightning storm!");
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                for (int i = 0; i < 20; i++) {
                                    int offsetX = (int) (Math.random() * 30 - 15);
                                    int offsetZ = (int) (Math.random() * 30 - 15);
                                    String lightningCmd = String.format("execute at @s run summon lightning_bolt ~%d ~ ~%d", offsetX, offsetZ);
                                    server.getCommands().performPrefixedCommand(godSource, lightningCmd);
                                }
                                future.complete(true);
                            } else {
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Lightning storm execution error", e);
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }
            }
            // === [修复] 拦截 Herobrine 传送指令，改用更可靠的、来自 HeroSummonItem 的逻辑 ===
            else if (command.equals("tp @e[type=herobrine_companion:hero,limit=1,sort=nearest] @s")) {
                LOGGER.info("Herobrine is being teleported via robust logic from HeroSummonItem.");
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) {
                                future.complete(false);
                                return;
                            }

                            HeroEntity existingHero = findHeroInAnyDimension(server);
                            ServerLevel targetLevel = serverPlayer.serverLevel();
                            double targetX = serverPlayer.getX();
                            double targetY = serverPlayer.getY();
                            double targetZ = serverPlayer.getZ();

                            if (existingHero != null) {
                                // 实体已存在，执行传送
                                if (existingHero.level().dimension() != targetLevel.dimension()) {
                                    // --- 跨维度传送 ---
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
                                    // --- 同维度传送 ---
                                    existingHero.teleportTo(targetLevel, targetX, targetY, targetZ, Collections.emptySet(), serverPlayer.getYRot(), serverPlayer.getXRot());
                                }
                            } else {
                                // --- 实体不存在，执行召唤 ---
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
                                    if (ModList.get().isLoaded("curios")) {
                                        newHero.setCuriosBackItemFromTag(worldData.getCuriosBackItem(serverPlayer.getUUID()));
                                    }
                                    targetLevel.addFreshEntity(newHero);
                                }
                            }
                            future.complete(true);
                        } catch (Exception e) {
                            LOGGER.error("Robust teleport execution error", e);
                            future.complete(false);
                        }
                    });
                } else {
                    mc.player.connection.sendCommand(command);
                    future.complete(true);
                }
            }
            // === [新增] 拦截 玩家传送指令 (tp @s @e[type=hero])，改用 SourceFlowItem 逻辑 ===
            // 注意：AI 生成的指令可能是 "tp @s @e[type=herobrine_companion:hero,limit=1,sort=nearest]"
            // 我们需要拦截这个指令，并调用 SourceFlowItem 的逻辑
            else if (command.startsWith("tp @s @e[type=herobrine_companion:hero")) {
                LOGGER.info("Player is being teleported to Herobrine via SourceFlow logic.");
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                // 模拟使用 SourceFlowItem (传送玩家到 Hero)
                                SourceFlowItem sourceFlowItem = (SourceFlowItem) HerobrineCompanion.SOURCE_FLOW.get();
                                // 创建一个虚拟的 ItemStack
                                ItemStack stack = new ItemStack(sourceFlowItem);
                                // 调用 use 方法
                                sourceFlowItem.use(serverPlayer.level(), serverPlayer, InteractionHand.MAIN_HAND);
                                future.complete(true);
                            } else {
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            LOGGER.error("SourceFlow teleport execution error", e);
                            future.complete(false);
                        }
                    });
                } else {
                    mc.player.connection.sendCommand(command);
                    future.complete(true);
                }
            }
            // === 拦截 3：绝对生效的模式切换 ===
            else if (command.contains("gamemode creative") || command.contains("gamemode 1")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                            future.complete(true);
                        } else { future.complete(false); }
                    });
                } else { future.complete(false); }
            }
            else if (command.contains("gamemode survival") || command.contains("gamemode 0")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                            future.complete(true);
                        } else { future.complete(false); }
                    });
                } else { future.complete(false); }
            }
            else if (command.contains("gamemode spectator") || command.contains("gamemode 3")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                            future.complete(true);
                        } else { future.complete(false); }
                    });
                } else { future.complete(false); }
            }
            // === 否则：正常执行单条原版指令 ===
            else {
                LOGGER.info("Herobrine is tampering with reality: /" + command);
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                server.getCommands().performPrefixedCommand(godSource, command);
                                future.complete(true);
                            } else {
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Command execution error", e);
                            future.complete(false);
                        }
                    });
                } else {
                    mc.player.connection.sendCommand(command);
                    future.complete(true);
                }
            }
        });

        return future;
    }

    private static void addToHistory(UUID playerUUID, String role, String content) {
        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", role);
            msg.addProperty("content", content);
            history.add(msg);

            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }
    }

    public static void clearHistory(UUID playerUUID) {
        chatHistories.remove(playerUUID);
    }

    private static String getDynamicGameData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "";

        StringBuilder data = new StringBuilder("\n\n【系统注入：当前世界可用数据流】：\n");

        // 👇 【新增】：动态探测玩家作弊权限并注入 AI 大脑 👇
        data.append("- 玩家权限协议：");
        if (mc.player.hasPermissions(2)) {
            data.append("[已解开法则锁链] (当前世界已开启作弊，你可以随意调用神力工具改变天气、模式或召唤物品！)。\n");
        } else {
            data.append("[底层法则完全锁死] (当前世界**未开启作弊**！你**绝对无法**使用神力工具。若他提出改变物理世界、模式或给予物品的要求，直接用文本嘲讽他身为凡人连作弊权限都没有，并拒绝调用任何工具！)\n");
        }

        // 1. 读取所有可用维度
        data.append("- 可用维度ID：");
        for (ResourceKey<Level> levelKey : mc.player.connection.levels()) {
            data.append(levelKey.location().toString()).append(", ");
        }
        data.append("\n");

        // 2. 读取结构和群系
        try {
            var registryAccess = mc.player.connection.registryAccess();

            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            data.append("- 你的专属神罚领域（不稳定区域）的结构ID：");
            for (ResourceLocation loc : structureRegistry.keySet()) {
                if ((loc.getNamespace().equals(HerobrineCompanion.MODID) && loc.getPath().equals("unstable_zone"))
                        || loc.getPath().contains("village")) {
                    data.append(loc.toString()).append(", ");
                }
            }
            data.append("\n");

            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            data.append("- 可用群系ID：");
            for (ResourceLocation loc : biomeRegistry.keySet()) {
                if (loc.getNamespace().equals(HerobrineCompanion.MODID) || loc.getNamespace().equals("twilightforest") || loc.getPath().contains("cherry")) {
                    data.append(loc.toString()).append(", ");
                }
            }
            data.append("\n");

        } catch (Exception e) {
            LOGGER.warn("AI 无法读取注册表数据", e);
        }

        data.append("- 你的专属NBT造物库（必须配合 place template 指令使用）：\n");
        if (LLMConfig.nbtStructures != null && !LLMConfig.nbtStructures.isEmpty()) {
            for (Map.Entry<String, String> entry : LLMConfig.nbtStructures.entrySet()) {
                data.append("  * ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
            }
        } else {
            data.append("  * （当前世界法则中未配置任何特殊造物）\n");
        }

        data.append("\n- 【神之眼】当前环境动态感知：\n");

        if (!mc.player.getMainHandItem().isEmpty()) {
            ResourceLocation itemLoc = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem());
            data.append("  * 玩家主手中正拿着物品：").append(itemLoc.toString()).append("\n");
        }

        data.append("  * 玩家周围20格内存在的实体(可能充满威胁)：");
        if (mc.level != null) {
            int entityCount = 0;
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && entity.distanceTo(mc.player) < 20) {
                    ResourceLocation entityLoc = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    data.append(entityLoc.toString()).append(", ");
                    entityCount++;
                    if (entityCount > 15) {
                        data.append("...等更多实体");
                        break;
                    }
                }
            }
            if (entityCount == 0) {
                data.append("周围很安全，没有实体。");
            }
        }
        data.append("\n");
        return data.toString();
    }

    private static String extractXmlParameter(String xml, String paramName) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("name=\"" + paramName + "\"[^>]*>([\\s\\S]*?)</parameter>");
            java.util.regex.Matcher matcher = pattern.matcher(xml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}