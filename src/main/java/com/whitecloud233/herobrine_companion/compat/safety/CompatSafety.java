package com.whitecloud233.herobrine_companion.compat.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Available before Minecraft, configuration specs and optional mod classes are initialized. */
public final class CompatSafety {
    public static final List<String> INTEGRATIONS = List.of("accessories", "armourers_workshop", "epicfight",
            "simplehats", "waveycapes", "wom", "efn", "epic_fight_avalon");
    private static final Logger LOGGER = LoggerFactory.getLogger("HerobrineCompanion/Compat");
    private static final Map<String, String> DISABLED = new ConcurrentHashMap<>();
    private static final Properties OPTIONS = loadOptions();

    private CompatSafety() { }

    private static Path configFile() {
        String override = System.getProperty("herobrine.compat.config");
        if (override != null) return Path.of(override);
        Path config = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        return (config == null ? Path.of("config") : config).resolve("herobrine_companion-compat.properties");
    }

    private static Properties loadOptions() {
        Properties options = new Properties();
        options.setProperty("safeMode", "false");
        INTEGRATIONS.forEach(id -> options.setProperty(id, "true"));
        try {
            Path path = configFile();
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) { options.load(input); }
                for (String key : options.stringPropertyNames()) {
                    if ((key.equals("safeMode") || INTEGRATIONS.contains(key))
                            && !List.of("true", "false").contains(options.getProperty(key).trim().toLowerCase(java.util.Locale.ROOT))) {
                        options.setProperty("safeMode", "true");
                        LOGGER.warn("Invalid compatibility option {}; safe mode enabled", key);
                    }
                }
            } else {
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                try (OutputStream output = Files.newOutputStream(path, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    options.store(output, "Herobrine Companion optional integrations. Restart after editing. safeMode disables all integrations listed here.");
                }
            }
        } catch (IOException | RuntimeException exception) {
            options.setProperty("safeMode", "true");
            LOGGER.warn("Cannot read compatibility settings; safe mode enabled", exception);
        }
        return options;
    }

    public static boolean configured(String integration) {
        String id = normalize(integration);
        if (!INTEGRATIONS.contains(id)) return false;
        if (Boolean.parseBoolean(System.getProperty("herobrine.compat.safeMode", OPTIONS.getProperty("safeMode", "false")).trim())) return false;
        return Boolean.parseBoolean(System.getProperty("herobrine.compat." + id, OPTIONS.getProperty(id, "true")).trim());
    }

    public static boolean allowed(String integration) {
        String id = normalize(integration);
        if (!configured(id) || DISABLED.containsKey(id)) return false;
        if (List.of("wom", "efn", "epic_fight_avalon").contains(id)
                && (!configured("epicfight") || DISABLED.containsKey("epicfight"))) return false;
        return true;
    }

    public static String normalize(String integration) {
        return switch (integration) {
            case "ArmourerWorkshop" -> "armourers_workshop";
            case "nightfall" -> "efn";
            case "avalon" -> "epic_fight_avalon";
            default -> integration;
        };
    }

    public static String groupForTarget(String target) {
        return normalize(target.substring(0, target.indexOf('.') < 0 ? target.length() : target.indexOf('.')));
    }

    public static void disable(String integration, String reason, Throwable failure) {
        rethrowFatal(failure);
        String id = normalize(integration);
        if (DISABLED.putIfAbsent(id, reason) == null) {
            LOGGER.warn("Disabled Herobrine integration {}: {}. Other mod features remain available; restart after fixing the cause.", id, reason, failure);
        }
    }

    public static void rethrowFatal(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof VirtualMachineError fatal) throw fatal;
            if (cause instanceof ThreadDeath death) throw death;
        }
    }

    public static boolean run(String integration, String action, Runnable operation) {
        if (!allowed(integration)) return false;
        try { operation.run(); return true; }
        catch (RuntimeException | LinkageError exception) {
            disable(integration, action + " failed", exception);
            return false;
        }
    }

    public static <T> T call(String integration, String action, Supplier<T> operation, T fallback) {
        if (!allowed(integration)) return fallback;
        try { return operation.get(); }
        catch (RuntimeException | LinkageError exception) {
            disable(integration, action + " failed", exception);
            return fallback;
        }
    }

    public static String reason(String integration) {
        String id = normalize(integration);
        if (!configured(id)) return "disabled by startup configuration";
        return DISABLED.getOrDefault(id, "available pending runtime checks");
    }

    public static Map<String, String> failures() { return Map.copyOf(DISABLED); }
}
