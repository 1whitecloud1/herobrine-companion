package com.whitecloud233.modid.herobrine_companion.client.service;

import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class LocalChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalChatService.class);
    private static final String DB_FILE_NAME = "hero_brain"; // 不带后缀的文件名
    private static final Random random = new Random();
    private static final List<CachedRule> cachedRules = new ArrayList<>();

    // 保存 ClassLoader 防止被垃圾回收
    private static URLClassLoader customClassLoader;
    private static Driver h2Driver;

    private static volatile LocalChatService INSTANCE;
    private Connection connection;

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
        loadH2Driver(); // 1. 加载驱动
        initDatabase(); // 2. 初始化（含释放文件逻辑）
        loadChatRules(); // 3. 读取规则
    }

    /**
     * 尝试加载 H2 驱动。
     * 优先使用环境中的驱动（ShadowJar 打包进去的），如果找不到则尝试本地 fallback（仅限开发环境）。
     */
    private void loadH2Driver() {
        try {
            // 正常情况下，发布版模组会包含 H2，这步应该直接成功
            Class.forName("org.h2.Driver");
            LOGGER.info("环境类路径中已存在 H2 驱动。");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("环境未找到 H2，尝试从 run/libs/ 手动加载 (仅限开发环境)...");
            try {
                File runDir = new File(".");
                File jarFile = new File(new File(runDir, "libs"), "h2-2.2.224.jar");
                if (jarFile.exists()) {
                    URL[] urls = {jarFile.toURI().toURL()};
                    customClassLoader = new URLClassLoader(urls, LocalChatService.class.getClassLoader());
                    Class<?> driverClass = customClassLoader.loadClass("org.h2.Driver");
                    h2Driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                    LOGGER.info("手动加载开发环境 H2 驱动成功！");
                }
            } catch (Exception ex) {
                LOGGER.error("驱动加载彻底失败，数据库功能将不可用。", ex);
            }
        }
    }

    private void initDatabase() {
        try {
            // 获取配置文件目录: .minecraft/config/herobrine_companion
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion");
            if (!Files.exists(configDir)) Files.createDirectories(configDir);

            // 目标数据库文件路径
            Path dbFilePath = configDir.resolve(DB_FILE_NAME + ".mv.db");

            // === 【关键步骤】自动释放数据库文件 ===
            if (!Files.exists(dbFilePath)) {
                LOGGER.info("检测到首次运行，正在释放默认数据库...");

                // 这是你放在 resources 下的路径
                String resourcePath = "/assets/herobrine_companion/database/hero_brain.mv.db";

                try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                    if (in != null) {
                        Files.copy(in, dbFilePath);
                        LOGGER.info("数据库释放成功！路径: {}", dbFilePath);
                    } else {
                        // 如果这里报错，说明你忘了把文件放进 src/main/resources 里
                        LOGGER.error("严重错误：Jar 包内找不到默认数据库文件！请检查路径: {}", resourcePath);
                    }
                } catch (IOException e) {
                    LOGGER.error("释放数据库文件时发生 IO 错误", e);
                }
            }
            // ===================================

            // 连接字符串 (去掉了 MODE=SQLite)
            String dbPathStr = configDir.resolve(DB_FILE_NAME).toAbsolutePath().toString();
            String url = "jdbc:h2:file:" + dbPathStr + ";AUTO_SERVER=TRUE";

            LOGGER.info("正在连接数据库: {}", url);

            if (h2Driver != null) {
                java.util.Properties info = new java.util.Properties();
                info.put("user", "sa");
                info.put("password", "");
                connection = h2Driver.connect(url, info);
            } else {
                connection = DriverManager.getConnection(url, "sa", "");
            }

            LOGGER.info("数据库连接成功！");

        } catch (Exception e) {
            LOGGER.error("无法初始化 H2 数据库", e);
        }
    }

    public void loadChatRules() {
        if (connection == null) {
            LOGGER.warn("数据库未连接，跳过加载。");
            return;
        }

        cachedRules.clear();
        String sql = "SELECT id, pattern, response FROM chat_rules";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String patternStr = rs.getString("pattern");
                String response = rs.getString("response");
                try {
                    Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                    cachedRules.add(new CachedRule(id, pattern, response));
                    count++;
                } catch (Exception e) {
                    LOGGER.warn("跳过无效规则 ID {}", id);
                }
            }
            LOGGER.info("已加载 {} 条聊天规则。", count);

        } catch (SQLException e) {
            LOGGER.error("读取 chat_rules 失败 (表可能不存在)", e);
        }
    }

    public CachedRule getChatResponse(String message) {
        if (cachedRules.isEmpty()) return null;
        List<CachedRule> matches = new ArrayList<>();
        for (CachedRule rule : cachedRules) {
            if (rule.pattern.matcher(message).find()) {
                matches.add(rule);
            }
        }
        if (!matches.isEmpty()) {
            return matches.get(random.nextInt(matches.size()));
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            if (customClassLoader != null) customClassLoader.close();
        } catch (Exception e) {
            LOGGER.error("关闭资源失败", e);
        }
    }

    public record CachedRule(int id, Pattern pattern, String response) {}
}