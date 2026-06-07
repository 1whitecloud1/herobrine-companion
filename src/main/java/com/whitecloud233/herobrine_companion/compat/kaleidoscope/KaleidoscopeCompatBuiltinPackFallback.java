package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;

public final class KaleidoscopeCompatBuiltinPackFallback {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "kaleidoscope_compat";
    private static final String MAIN_CLASS = "com.bmt.kaleidoscope_compat.KaleidoscopeCompat";
    private static final String CONFIG_CLASS = "com.bmt.kaleidoscope_compat.config.MainConfig";

    private KaleidoscopeCompatBuiltinPackFallback() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }

        for (String packName : resolvePackNames()) {
            if (!needsDirectoryFallback(packName)) {
                continue;
            }
            registerBuiltinPack(event, packName);
        }
    }

    private static List<String> resolvePackNames() {
        List<String> packNames = new ArrayList<>();
        boolean uniteMode = isUniteMode();
        packNames.add(uniteMode ? "unite" : "compat");
        if (isSoupDatapackEnabled()) {
            packNames.add("soup");
        }
        if (uniteMode) {
            if (ModList.get().isLoaded("farm_and_charm")) {
                packNames.add("unite_farm_and_charm");
            }
            if (ModList.get().isLoaded("farmersdelight")) {
                packNames.add("unite_farmersdelight");
            }
        }
        return packNames;
    }

    private static boolean isUniteMode() {
        Object datapackMode = getConfigValue("datapackMode");
        return datapackMode != null && "UNITE".equals(datapackMode.toString());
    }

    private static boolean isSoupDatapackEnabled() {
        Object enabled = getConfigValue("soupDatapackEnabled");
        return enabled instanceof Boolean flag ? flag : true;
    }

    @Nullable
    private static Object getConfigValue(String fieldName) {
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Field field = configClass.getField(fieldName);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to read {}.{} for builtin pack fallback.", CONFIG_CLASS, fieldName, e);
            return null;
        }
    }

    private static boolean needsDirectoryFallback(String packName) {
        try {
            Class<?> compatClass = Class.forName(MAIN_CLASS);
            boolean hasLegacyZip = compatClass.getResource("/data/kaleidoscope_compat/" + packName + ".zip") != null;
            boolean hasDirectoryPack = compatClass.getResource("/packs/" + packName + "/pack.mcmeta") != null;
            return !hasLegacyZip && hasDirectoryPack;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Failed to resolve {} while checking bundled datapacks.", MAIN_CLASS, e);
            return false;
        }
    }

    private static void registerBuiltinPack(AddPackFindersEvent event, String packName) {
        ResourceLocation packLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "packs/" + packName);
        Component title = Component.literal("Kaleidoscope Compat - " + packName.toUpperCase(Locale.ROOT));
        event.addPackFinders(packLocation, PackType.SERVER_DATA, title, PackSource.BUILT_IN, event.isTrusted(), Pack.Position.TOP);
        LOGGER.info("Registered {} builtin pack fallback for {}", MOD_ID, packLocation);
    }
}
