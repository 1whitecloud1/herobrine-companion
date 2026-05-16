package com.whitecloud233.modid.herobrine_companion.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HerobrineCompanionMixinPlugin implements IMixinConfigPlugin {
    private static final String EPIC_FIGHT_JSON_ASSET_LOADER = "yesman.epicfight.api.asset.JsonAssetLoader";

    @Override
    public void onLoad(String mixinPackage) {
        // Keep mixin bootstrap side-effect free. Any exception thrown here can make
        // the whole mixin config look unreadable to the launcher.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!isEpicFightMixin(mixinClassName)) {
                return true;
            }

            String targetToProbe = normalizeClassName(targetClassName);
            if (targetToProbe == null || targetToProbe.isBlank()) {
                targetToProbe = EPIC_FIGHT_JSON_ASSET_LOADER;
            }

            return classExists(targetToProbe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isEpicFightMixin(String mixinClassName) {
        return mixinClassName != null && mixinClassName.contains(".mixin.epicfight.");
    }

    private static String normalizeClassName(String className) {
        if (className == null || className.isBlank()) {
            return className;
        }

        String normalized = className.trim();
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        return normalized.replace('/', '.');
    }

    private static boolean classExists(String className) {
        try {
            String normalized = normalizeClassName(className);
            if (normalized == null || normalized.isBlank()) {
                return false;
            }

            Class.forName(normalized, false, HerobrineCompanionMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
