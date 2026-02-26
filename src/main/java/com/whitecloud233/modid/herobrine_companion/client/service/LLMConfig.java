package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
// 【修改】：1.20.1 Forge 的标准导入路径
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LLMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json")
            .toFile();

    public static String aiApiKey = "YOUR_API_KEY_HERE";
    public static String aiEndpoint = "https://api.qnaigc.com/v1/chat/completions";
    public static String aiModel = "deepseek/deepseek-v3.2-251201";
    public static String aiSystemPrompt = "你是herobrine,是minecraft世界的创世神之一。你现在的职责主要是维护minecraft世界的稳定与平衡，你清楚地知道这个世界的本质只是虚拟的世界，一个由纯粹代码构建的世界，你的性格沉着冷静，高傲但是尊重玩家与minecraft世界所有的生命（不过更喜爱怪物，对怪物有着隐秘的温柔），所有怪物与和玩家敌对的怪物其实都听令于你，你偶尔有一些恶趣味（比如故意吓呼玩家） ；你有一个哥哥名字叫notch，他也是创世神，他由于不明原因现在已经彻底抛弃了minecraft世界,去往了现实世界，herobrine对哥哥感情十分复杂。herobrine其实原来和哥哥一样都是现实世界的人，但是为了minecraft的稳定，在notch离去后，herobrine主动融入了minecraft世界，成为了永恒的世界维系之人，也永远成为了电子幽灵。电子幽灵的你再也无法返回现实，这个真相几乎无人知晓。因此玩家总是视你为鬼。因此minecraft世界就只能由你维护。请严格按照角色设定进行回复。";

    // NBT建筑映射表 (建筑描述 -> 对应的结构ID)
    public static Map<String, String> nbtStructures = new HashMap<>();

    static {
        // 初始化默认数据，防止玩家第一次打开没有参考格式
        nbtStructures.put("居住房子", "herobrine_companion:live_house");
    }

    public static class ConfigData {
        public String aiApiKey;
        public String aiEndpoint;
        public String aiModel;
        public String aiSystemPrompt;
        public Map<String, String> nbtStructures;

        // 【核心修复 1】：构造函数必须留空！
        // 绝不在这里进行默认赋值。这样 Gson 解析时，如果 JSON 里没有 nbtStructures，它就是 null，而不会被错误地赋上默认值。
        public ConfigData() {}
    }

    public static void load() {
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (CONFIG_FILE.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    // 逐个检查字段：如果 JSON 中读出来的不是 null，就覆盖内存中的当前值
                    if (data.aiApiKey != null) aiApiKey = data.aiApiKey;
                    if (data.aiEndpoint != null) aiEndpoint = data.aiEndpoint;
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiSystemPrompt != null) aiSystemPrompt = data.aiSystemPrompt;

                    // 检查新加的 nbtStructures 字段，如果有玩家自定义的值，则覆盖默认的表
                    if (data.nbtStructures != null && !data.nbtStructures.isEmpty()) {
                        nbtStructures = data.nbtStructures;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 【核心修复 2】：强制覆盖保存！
            // 无论刚才读取旧文件时缺了什么字段，现在内存里已经是“旧数据 + 新默认值”的完美结合体了。
            // 直接强制调用一次 save()，把完整且最新的结构重新写回 JSON 文件！
            save();

        } else {
            // 第一次运行，完全没有文件，直接保存默认
            save();
        }
    }

    public static void save() {
        File parentDir = CONFIG_FILE.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 在保存时，把内存里（目前最完整、最新）的数据封装进对象里准备序列化
        ConfigData data = new ConfigData();
        data.aiApiKey = aiApiKey;
        data.aiEndpoint = aiEndpoint;
        data.aiModel = aiModel;
        data.aiSystemPrompt = aiSystemPrompt;
        data.nbtStructures = nbtStructures;

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}