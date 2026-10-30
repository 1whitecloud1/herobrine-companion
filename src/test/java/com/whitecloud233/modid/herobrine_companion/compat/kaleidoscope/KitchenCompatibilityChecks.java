package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import java.net.URL;
import java.net.URLClassLoader;

public final class KitchenCompatibilityChecks {
    private static final String PACKAGE = "com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.";
    private static int checks;

    public static void main(String[] args) throws Exception {
        URL mainClasses = HeroKaleidoscopeCompat.class.getProtectionDomain().getCodeSource().getLocation();
        int[] optionalLookups = {0};
        try (URLClassLoader loader = new URLClassLoader(new URL[]{mainClasses}, KitchenCompatibilityChecks.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.ysbbbbbb.kaleidoscopecookery.")) {
                    optionalLookups[0]++;
                    throw new ClassNotFoundException("Simulating absent optional Cookery class: " + name);
                }
                if (!name.startsWith(PACKAGE)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }) {
            // 这些类在森罗模组缺席时也必须能加载，不得触发对可选模组类的引用。
            // KitchenIngredientMatcher 已并入 entity.cooking.IngredientMatcher（无模组依赖），
            // 不再属于本包，故从清单中移除。
            for (String name : new String[]{"HeroKaleidoscopeCompat", "KitchenSession", "KitchenRecipes",
                    "MillstoneKitchenStation"}) {
                Class.forName(PACKAGE + name, true, loader).getDeclaredMethods();
                check(optionalLookups[0] == 0, name + " eagerly loaded an optional mod class");
            }
            Class<?> teapot = Class.forName(PACKAGE + "TeapotKitchenStation", true, loader);
            check(optionalLookups[0] == 1, "Teapot must check presence without initializing its implementation holder");
            var stationCheck = teapot.getDeclaredMethod("isTeapotBlockEntity", net.minecraft.world.level.block.entity.BlockEntity.class);
            stationCheck.setAccessible(true);
            check(Boolean.FALSE.equals(stationCheck.invoke(null, new Object[]{null})), "Missing teapot must not match a station");
            var options = teapot.getDeclaredMethod("getAutonomousCookOptions", net.minecraft.server.level.ServerLevel.class);
            options.setAccessible(true);
            check(((java.util.List<?>) options.invoke(null, new Object[]{null})).isEmpty(), "Missing teapot must return no recipes");
            check(optionalLookups[0] == 1, "Missing teapot must not load recipes or serializer classes");
        }
        System.out.println("KitchenCompatibilityChecks: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
