package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LLMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean apiKeyMarkedInvalid = false;

    // 1. 公开配置：放在 config 文件夹，会被整合包打包
    private static final File PUBLIC_CONFIG = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json").toFile();

    // 2. 私密配置：放在游戏根目录，绝对不会被打包工具带走（核心防御）
    private static final File PRIVATE_SECRETS = FMLPaths.GAMEDIR.get()
            .resolve("herobrine_ai_secrets.json").toFile();

    // 静态变量
    public static String aiApiKey = "YOUR_API_KEY_HERE";
    public static String aiEndpoint = "https://api.qnaigc.com/v1/chat/completions";
    public static String aiModel = "deepseek/deepseek-v3.2-251201";
    public static String aiSystemPrompt = "You are Herobrine...";
    public static Map<String, String> nbtStructures = new HashMap<>();

    public static boolean isKeyMissing() {
        return aiApiKey == null || aiApiKey.isBlank() || aiApiKey.equals("YOUR_API_KEY_HERE");
    }

    public static boolean isKeyMissingOrInvalid() {
        return isKeyMissing() || apiKeyMarkedInvalid;
    }

    public static void markApiKeyInvalid() {
        apiKeyMarkedInvalid = true;
    }

    public static void markApiKeyValid() {
        apiKeyMarkedInvalid = false;
    }

    public static void load() {
        // 加载公开设置
        if (PUBLIC_CONFIG.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiSystemPrompt != null) aiSystemPrompt = data.aiSystemPrompt;
                    if (data.nbtStructures != null) nbtStructures = data.nbtStructures;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        // 加载私密密钥（覆盖默认值）
        if (PRIVATE_SECRETS.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
                SecretData data = GSON.fromJson(reader, SecretData.class);
                if (data != null && data.aiApiKey != null) {
                    aiApiKey = data.aiApiKey;
                    if (data.aiEndpoint != null) aiEndpoint = data.aiEndpoint;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        save();
    }

    public static void save() {
        try {
            markApiKeyValid();

            // 保存公开配置
            PUBLIC_CONFIG.getParentFile().mkdirs();
            ConfigData pData = new ConfigData();
            pData.aiModel = aiModel; pData.aiSystemPrompt = aiSystemPrompt; pData.nbtStructures = nbtStructures;
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
                GSON.toJson(pData, writer);
            }

            // 保存私密密钥
            SecretData sData = new SecretData();
            sData.aiApiKey = aiApiKey; sData.aiEndpoint = aiEndpoint;
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
                GSON.toJson(sData, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static class ConfigData { String aiModel; String aiSystemPrompt; Map<String, String> nbtStructures; }
    private static class SecretData { String aiApiKey; String aiEndpoint; }
}