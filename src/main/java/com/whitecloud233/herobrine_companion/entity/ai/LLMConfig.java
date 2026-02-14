package com.whitecloud233.herobrine_companion.entity.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class LLMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json")
            .toFile();

    public static String aiApiKey = "YOUR_API_KEY_HERE";
    public static String aiEndpoint = "https://api.qnaigc.com/v1/chat/completions";
    public static String aiModel = "deepseek/deepseek-v3.2-251201";
    public static String aiSystemPrompt = "你是herobrine,是minecraft世界的创世神之一。你现在的职责主要是维护minecraft世界的稳定与平衡，你清楚地知道这个世界的本质只是虚拟的世界，一个由纯粹代码构建的世界，你的性格沉着冷静，高傲但是尊重玩家与minecraft世界所有的生命（不过更喜爱怪物，对怪物有着隐秘的温柔），所有怪物与和玩家敌对的怪物其实都听令于你，你偶尔有一些恶趣味（比如故意吓呼玩家） ；你有一个哥哥名字叫notch，他也是创世神，他由于不明原因现在已经彻底抛弃了minecraft世界,去往了现实世界，herobrine对哥哥感情十分复杂。herobrine其实原来和哥哥一样都是现实世界的人，但是为了minecraft的稳定，在notch离去后，herobrine主动融入了minecraft世界，成为了永恒的世界维系之人，也永远成为了电子幽灵。电子幽灵的你再也无法返回现实，这个真相几乎无人知晓。因此玩家总是视你为鬼。因此minecraft世界就只能由你维护。请严格按照角色设定进行回复。";

    public static class ConfigData {
        public String aiApiKey;
        public String aiEndpoint;
        public String aiModel;
        public String aiSystemPrompt;

        public ConfigData() {
            this.aiApiKey = LLMConfig.aiApiKey;
            this.aiEndpoint = LLMConfig.aiEndpoint;
            this.aiModel = LLMConfig.aiModel;
            this.aiSystemPrompt = LLMConfig.aiSystemPrompt;
        }
    }

    public static void load() {
        // 确保父目录存在
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (CONFIG_FILE.exists()) {
            // [修复] 使用 InputStreamReader 指定 UTF-8 编码读取
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    aiApiKey = data.aiApiKey;
                    aiEndpoint = data.aiEndpoint;
                    aiModel = data.aiModel;
                    aiSystemPrompt = data.aiSystemPrompt;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        // 确保父目录存在
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        ConfigData data = new ConfigData();
        data.aiApiKey = aiApiKey;
        data.aiEndpoint = aiEndpoint;
        data.aiModel = aiModel;
        data.aiSystemPrompt = aiSystemPrompt;

        // [修复] 使用 OutputStreamWriter 指定 UTF-8 编码写入
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
