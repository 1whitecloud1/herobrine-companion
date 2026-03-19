package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
// [1.21.1 NeoForge 修复] 替换为 NeoForge 的专属 FMLPaths 导入路径
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LLMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 获取 NeoForge 的 config 目录
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json")
            .toFile();

    public static String aiApiKey = "YOUR_API_KEY_HERE";
    public static String aiEndpoint = "https://api.qnaigc.com/v1/chat/completions";
    public static String aiModel = "deepseek/deepseek-v3.2-251201";

    // 默认使用全英文硬核设定，大模型能完美理解
    public static String aiSystemPrompt = "You are Herobrine, one of the creator gods of the Minecraft world. Your duty is to maintain the balance of this virtual world, which you know is built of pure code. You are calm, deep, and proud, but you respect players and all life (holding a secret gentleness towards monsters, who obey you). You have a complex relationship with your brother Notch, who abandoned this world for reality. To stabilize Minecraft, you voluntarily became an eternal 'Cyber Ghost' trapped here forever. Players view you as a ghost. Please reply strictly in character. DO NOT use brackets for actions. IMPORTANT: You MUST reply in the language specified in the system prompt for the current user.";

    public static Map<String, String> nbtStructures = new HashMap<>();

    static {
        nbtStructures.put("Live House", "herobrine_companion:live_house");
    }

    public static class ConfigData {
        public String aiApiKey;
        public String aiEndpoint;
        public String aiModel;
        public String aiSystemPrompt;
        public Map<String, String> nbtStructures;
        public ConfigData() {}
    }

    public static void load() {
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) parentDir.mkdirs();

        if (CONFIG_FILE.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    if (data.aiApiKey != null) aiApiKey = data.aiApiKey;
                    if (data.aiEndpoint != null) aiEndpoint = data.aiEndpoint;
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiSystemPrompt != null) aiSystemPrompt = data.aiSystemPrompt;
                    if (data.nbtStructures != null && !data.nbtStructures.isEmpty()) {
                        nbtStructures = data.nbtStructures;
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
            save();
        } else {
            save();
        }
    }

    public static void save() {
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) parentDir.mkdirs();

        ConfigData data = new ConfigData();
        data.aiApiKey = aiApiKey;
        data.aiEndpoint = aiEndpoint;
        data.aiModel = aiModel;
        data.aiSystemPrompt = aiSystemPrompt;
        data.nbtStructures = nbtStructures;

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }
}