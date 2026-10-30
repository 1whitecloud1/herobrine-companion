package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;

/** Optional Iris/Oculus API access; neither mod is required to load the renderer. */
final class PoemShaderCompat {
    private static Object api;
    private static Method shaders, shadows;

    static {
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            shaders = type.getMethod("isShaderPackInUse");
            shadows = type.getMethod("isRenderingShadowPass");
        } catch (ClassNotFoundException ignored) {
            // Vanilla rendering has no optional API to consult.
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private PoemShaderCompat() { }

    static boolean shadersEnabled() { return query(shaders); }
    static boolean shadowPass() { return query(shadows); }

    private static boolean query(Method method) {
        if (api == null || method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(api));
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    private static void disable(Throwable exception) {
        api = null;
        LogUtils.getLogger().warn("Unable to query Iris/Oculus; using immediate Poem outlines", exception);
    }
}
