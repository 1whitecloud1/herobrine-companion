package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.ResourcePackLoader;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
        String packId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "packs/" + packName).toString();
        Component title = Component.literal("Kaleidoscope Compat - " + packName.toUpperCase(Locale.ROOT));
        event.addRepositorySource(packConsumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    packId,
                    title,
                    true,
                    ignored -> openPackResources(packId, packName),
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
            if (pack != null) {
                LOGGER.info("Registered {} builtin pack fallback for {}", MOD_ID, packId);
                packConsumer.accept(pack);
            }
        });
    }

    @Nullable
    private static PackResources openPackResources(String packId, String packName) {
        Optional<net.minecraftforge.resource.PathPackResources> rootPack = ResourcePackLoader.getPackFor(MOD_ID);
        if (rootPack.isEmpty()) {
            LOGGER.warn("Unable to resolve mod resource pack for {}", MOD_ID);
            return null;
        }

        Path source = rootPack.get().getSource();
        try {
            if (Files.isDirectory(source)) {
                Path packRoot = source.resolve("packs").resolve(packName);
                if (!Files.exists(packRoot)) {
                    LOGGER.warn("Missing builtin pack directory {}", packRoot);
                    return null;
                }
                return new ManagedPathPackResources(packId, packRoot, null);
            }

            if (Files.isRegularFile(source)) {
                URI jarUri = URI.create("jar:" + source.toUri());
                FileSystem fileSystem;
                boolean shouldClose = false;
                try {
                    fileSystem = FileSystems.newFileSystem(jarUri, Map.of());
                    shouldClose = true;
                } catch (FileSystemAlreadyExistsException e) {
                    fileSystem = FileSystems.getFileSystem(jarUri);
                }
                Path packRoot = fileSystem.getPath("/packs", packName);
                if (!Files.exists(packRoot)) {
                    if (shouldClose) {
                        safeClose(fileSystem, packId);
                    }
                    LOGGER.warn("Missing builtin pack path /packs/{} inside {}", packName, source);
                    return null;
                }
                return new ManagedPathPackResources(packId, packRoot, shouldClose ? fileSystem : null);
            }

            LOGGER.warn("Unsupported {} resource pack source {}", MOD_ID, source);
        } catch (IOException e) {
            LOGGER.warn("Failed to open builtin pack {} from {}", packId, source, e);
        }
        return null;
    }

    private static void safeClose(FileSystem fileSystem, String packId) {
        try {
            fileSystem.close();
        } catch (IOException e) {
            LOGGER.debug("Failed to close temporary filesystem for {}", packId, e);
        }
    }

    private static final class ManagedPathPackResources extends net.minecraftforge.resource.PathPackResources {
        @Nullable
        private final FileSystem ownedFileSystem;

        private ManagedPathPackResources(String packId, Path source, @Nullable FileSystem ownedFileSystem) {
            super(packId, true, source);
            this.ownedFileSystem = ownedFileSystem;
        }

        @Override
        public void close() {
            super.close();
            if (ownedFileSystem != null) {
                safeClose(ownedFileSystem, packId());
            }
        }
    }
}
