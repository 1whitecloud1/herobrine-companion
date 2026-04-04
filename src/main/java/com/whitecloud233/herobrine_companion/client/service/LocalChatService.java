package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.fml.loading.FMLPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.nio.file.StandardCopyOption; // 新增导入


public class LocalChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalChatService.class);
    private static final String JSON_FILE_NAME = "hero_brain.json";
    private static final Random random = new Random();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile LocalChatService INSTANCE;
    private final List<RuleData> rulesData = new ArrayList<>();

    public static LocalChatService getInstance() {
        if (INSTANCE == null) {
            synchronized (LocalChatService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalChatService();
                }
            }
        }
        return INSTANCE;
    }

    private LocalChatService() {
        loadChatRules();
    }

    public void loadChatRules() {
        rulesData.clear();
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion");
        File jsonFile = configDir.resolve(JSON_FILE_NAME).toFile();

        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }

        // 如果 config 下没有文件，自动从 resource/assets/ 释放默认的 json
        if (!jsonFile.exists()) {
            extractDefaultRules(jsonFile);
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            List<RuleData> loadedRules = GSON.fromJson(reader, new TypeToken<List<RuleData>>(){}.getType());
            if (loadedRules != null) {
                rulesData.addAll(loadedRules);
            }
            LOGGER.info("成功从 JSON 加载了 {} 条本地对话规则 (已开启 I18n 翻译键支持)。", rulesData.size());
        } catch (Exception e) {
            LOGGER.error("无法加载 hero_brain.json", e);
        }
    }

    private void extractDefaultRules(File destFile) {
        String resourcePath = "/assets/herobrine_companion/database/default_hero_brain.json";
        try (InputStream in = LocalChatService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("未在模组资源中找到 {}, 将生成最小默认规则。", resourcePath);
                createFallbackRules(destFile);
                return;
            }
            Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("已成功释放默认的 JSON 词库。");
        } catch (IOException e) {
            LOGGER.error("释放默认 JSON 规则失败", e);
            createFallbackRules(destFile);
        }
    }

    private void createFallbackRules(File jsonFile) {
        List<RuleData> fallback = new ArrayList<>();
        // 这里的 pattern 和 response 默认使用了多语言的翻译键
        fallback.add(new RuleData(1, "chat.herobrine_companion.rule_hello.pattern", "chat.herobrine_companion.rule_hello.response", 10));
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
            GSON.toJson(fallback, writer);
        } catch (IOException e) {
            LOGGER.error("生成 Fallback JSON 失败", e);
        }
    }

    /**
     * 根据输入消息获取随机匹配的响应
     */
    public CachedRule getChatResponse(String message) {
        if (message == null || message.trim().isEmpty() || rulesData.isEmpty()) return null;

        List<RuleData> matches = new ArrayList<>();

        for (RuleData rule : rulesData) {
            // 1. 【核心逻辑】：如果存在该翻译键，就提取翻译后的文本；如果不存在，就原样使用 json 里的文字 (支持玩家乱写)
            String actualPattern = I18n.exists(rule.pattern) ? I18n.get(rule.pattern) : rule.pattern;

            try {
                // 2. 【性能优化】：只有当语言改变导致实际正则表达式发生变化时，才重新编译。平时直接复用编译好的缓存。
                if (rule.compiledRegex == null || !actualPattern.equals(rule.lastLangRegex)) {
                    rule.compiledRegex = Pattern.compile(actualPattern, Pattern.CASE_INSENSITIVE);
                    rule.lastLangRegex = actualPattern;
                }

                // 3. 匹配玩家聊天内容
                if (rule.compiledRegex.matcher(message).find()) {
                    matches.add(rule);
                }
            } catch (Exception e) {
                LOGGER.warn("跳过无效的正则表达式 (ID: {}): {}", rule.id, actualPattern);
            }
        }

        if (!matches.isEmpty()) {
            RuleData chosen = matches.get(random.nextInt(matches.size()));

            // 4. 【核心逻辑】：同样处理回复内容，支持返回翻译键或普通文本
            String finalResponse = I18n.exists(chosen.response) ? I18n.get(chosen.response) : chosen.response;

            return new CachedRule(chosen.id, chosen.compiledRegex, finalResponse);
        }
        return null;
    }

    public boolean forceRestoreDefaultDatabase() {
        LOGGER.info("正在恢复默认对话词库 JSON...");
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion");
        File jsonFile = configDir.resolve(JSON_FILE_NAME).toFile();
        try {
            Files.deleteIfExists(jsonFile.toPath());
            loadChatRules();
            return true;
        } catch (Exception e) {
            LOGGER.error("恢复默认词库失败", e);
            return false;
        }
    }

    public void close() {
        // 使用 JSON 彻底摆脱数据库文件锁，无需做任何清理。
    }

    // 兼容原有的代码接口结构
    public record CachedRule(int id, Pattern pattern, String response) {}

    // 用于 Gson 映射 JSON 结构的内置数据类
    public static class RuleData {
        public int id;
        public String pattern;
        public String response;
        public int weight;

        // 运行时缓存（声明为 transient，Gson 解析/写入时会自动忽略它们）
        private transient Pattern compiledRegex;
        private transient String lastLangRegex;

        public RuleData(int id, String pattern, String response, int weight) {
            this.id = id;
            this.pattern = pattern;
            this.response = response;
            this.weight = weight;
        }
    }
}