package com.whitecloud233.herobrine_companion.api.compat;

import com.whitecloud233.herobrine_companion.compat.safety.CompatPreflight;
import com.whitecloud233.herobrine_companion.compat.safety.CompatSafety;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Lazy boundary to bundled integrations; the caller never needs third-party API types. */
public final class OptionalCompat {
    private static final String PACKAGE = "com.whitecloud233.herobrine_companion.compat.";
    private static final ConcurrentHashMap<Call, Method> METHODS = new ConcurrentHashMap<>();

    private OptionalCompat() { }

    public static boolean isInstalled() { return true; }

    public static Object call(String target, String method, Class<?>[] parameterTypes, Object... arguments) {
        String integration = CompatSafety.groupForTarget(target);
        if (!CompatPreflight.available(integration)) return null;
        Call key = new Call(target, method, List.of(parameterTypes));
        try {
            Method implementation = METHODS.get(key);
            if (implementation == null) {
                Class<?> type = Class.forName(PACKAGE + target, true, OptionalCompat.class.getClassLoader());
                implementation = type.getMethod(method, parameterTypes);
                METHODS.put(key, implementation);
            }
            return implementation.invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
            CompatSafety.disable(integration, target + "." + method + " failed", cause);
            return null;
        }
    }

    private record Call(String target, String method, List<Class<?>> parameters) { }
}
