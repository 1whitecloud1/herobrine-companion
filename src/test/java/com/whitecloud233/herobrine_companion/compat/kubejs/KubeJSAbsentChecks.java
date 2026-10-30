package com.whitecloud233.herobrine_companion.compat.kubejs;

public final class KubeJSAbsentChecks {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = KubeJSAbsentChecks.class.getClassLoader();
        for (String name : new String[]{"dev.latvian.mods.kubejs.KubeJS", "dev.latvian.mods.rhino.Context"}) {
            try { Class.forName(name, false, loader); throw new AssertionError("Optional API leaked into absent-mod check: " + name); }
            catch (ClassNotFoundException expected) { }
        }
        for (String name : new String[]{"com.whitecloud233.herobrine_companion.event.HeroContentReload", "com.whitecloud233.herobrine_companion.event.HeroContentLimits",
                "com.whitecloud233.herobrine_companion.event.HeroContentSnapshot", "com.whitecloud233.herobrine_companion.event.HeroTrades", "com.whitecloud233.herobrine_companion.compat.kubejs.SafeKubeJSCaller"}) {
            Class<?> type = Class.forName(name, false, loader);
            type.getDeclaredMethods();
        }
        System.out.println("KUBEJS_ABSENT_OK: common hooks load without KubeJS/Rhino");
    }
}
