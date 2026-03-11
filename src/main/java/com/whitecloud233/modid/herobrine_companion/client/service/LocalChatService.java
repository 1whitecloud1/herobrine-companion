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
import java.nio.file.StandardCopyOption;
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

    private static URLClassLoader customClassLoader;
    private static Driver h2Driver; // 显式持有驱动实例

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
        initDatabase(); // 2. 初始化数据库并释放文件
        loadChatRules(); // 3. 预加载聊天规则到内存
    }

    /**
     * 尝试加载 H2 驱动。支持重定向后的包名和开发环境原生包名。
     */
    private void loadH2Driver() {
        // 依次尝试发布版重定向路径和开发版原生路径
        String[] driverClassNames = {
                "herobrine_companion.shadow.h2.Driver",
                "org.h2.Driver"
        };

        for (String className : driverClassNames) {
            try {
                Class<?> driverClass = Class.forName(className);
                h2Driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                LOGGER.info("成功加载 H2 驱动: {}", className);
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                LOGGER.error("实例化驱动 {} 时出错", className, e);
            }
        }

        // 开发环境 Fallback 逻辑：从 run/libs 加载
        LOGGER.warn("环境未找到内置 H2，尝试从外部文件手动加载...");
        try {
            File runDir = new File(".");
            File jarFile = new File(runDir, "libs/h2-2.2.224.jar");
            if (!jarFile.exists()) jarFile = new File(runDir, "../libs/h2-2.2.224.jar");

            if (jarFile.exists()) {
                URL[] urls = {jarFile.toURI().toURL()};
                customClassLoader = new URLClassLoader(urls, LocalChatService.class.getClassLoader());
                Class<?> driverClass = customClassLoader.loadClass("org.h2.Driver");
                h2Driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                LOGGER.info("手动加载开发环境 H2 驱动成功: {}", jarFile.getCanonicalPath());
            } else {
                LOGGER.error("驱动加载彻底失败，数据库功能将不可用。");
            }
        } catch (Exception ex) {
            LOGGER.error("外部驱动加载异常", ex);
        }
    }

    private void initDatabase() {
        try {
            // 定位配置目录: .minecraft/config/herobrine_companion
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion");
            if (!Files.exists(configDir)) Files.createDirectories(configDir);

            Path dbFilePath = configDir.resolve(DB_FILE_NAME + ".mv.db");

            // 如果配置文件不存在，则从 Jar 包中释放默认数据库
            if (!Files.exists(dbFilePath)) {
                LOGGER.info("检测到未找到数据库，正在从资源文件释放...");
                String resourcePath = "/assets/herobrine_companion/database/hero_brain.mv.db";
                try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                    if (in != null) {
                        Files.copy(in, dbFilePath, StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("默认数据库释放成功: {}", dbFilePath);
                    } else {
                        LOGGER.error("错误：Jar 包内找不到资源文件: {}", resourcePath);
                    }
                }
            }

            String dbPathStr = configDir.resolve(DB_FILE_NAME).toAbsolutePath().toString();
            String url = "jdbc:h2:file:" + dbPathStr;
            LOGGER.info("正在连接数据库: {}", url);

            // 优先使用手动加载的驱动实例连接，避免 DriverManager 扫描失败
            java.util.Properties info = new java.util.Properties();
            info.put("user", "sa");
            info.put("password", "");

            if (h2Driver != null) {
                connection = h2Driver.connect(url, info);
                LOGGER.info("数据库连接成功（通过驱动实例）。");
            } else {
                connection = DriverManager.getConnection(url, "sa", "");
                LOGGER.info("数据库连接成功（通过 DriverManager）。");
            }

        } catch (Exception e) {
            LOGGER.error("无法初始化 H2 数据库", e);
        }
    }

    /**
     * 从数据库加载所有匹配规则
     */
    public void loadChatRules() {
        if (connection == null) {
            LOGGER.warn("数据库未连接，跳过加载规则。");
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
                    LOGGER.warn("跳过无效正则表达式规则 ID: {}", id);
                }
            }
            LOGGER.info("数据库加载完成，共 {} 条聊天规则。", count);

        } catch (SQLException e) {
            LOGGER.error("查询 chat_rules 表失败", e);
        }
    }

    /**
     * 根据输入消息获取随机匹配的响应
     */
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

    /**
     * 强制删除现有数据库并重新从资源释放（用于重置或修复）
     */
    public boolean forceRestoreDefaultDatabase() {
        LOGGER.info("正在执行数据库强制恢复...");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }

            Path configDir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion");
            Files.deleteIfExists(configDir.resolve(DB_FILE_NAME + ".mv.db"));
            Files.deleteIfExists(configDir.resolve(DB_FILE_NAME + ".trace.db"));

            initDatabase();
            loadChatRules();
            LOGGER.info("数据库恢复完成。");
            return true;
        } catch (Exception e) {
            LOGGER.error("恢复数据库时发生错误", e);
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            if (customClassLoader != null) customClassLoader.close();
        } catch (Exception e) {
            LOGGER.error("关闭数据库资源失败", e);
        }
    }

    public record CachedRule(int id, Pattern pattern, String response) {}
}