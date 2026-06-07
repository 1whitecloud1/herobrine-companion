package com.whitecloud233.herobrine_companion.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

public final class ClientOnlyExecutor {
    private ClientOnlyExecutor() {
    }

    public static void invoke(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(className);
            Method method = handlerClass.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke client-only handler: " + className + "#" + methodName, e);
        }
    }
}
