package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HeroEpicFightDebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("herobrine.debug.epicfight", "true"));
    private static final Map<String, String> LAST_STATES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> REPEAT_COUNTS = new ConcurrentHashMap<>();

    private HeroEpicFightDebugLog() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void event(@Nullable HeroEntity hero, String tag, String detail) {
        if (!ENABLED) {
            return;
        }

        LOGGER.info("[HeroEFDebug] {} {}", prefix(hero, tag), detail);
    }

    public static void transition(@Nullable HeroEntity hero, String scope, String stateKey, String detail) {
        if (!ENABLED) {
            return;
        }

        String heroKey = hero != null ? String.valueOf(hero.getId()) : "null";
        String cacheKey = heroKey + "|" + scope;
        String previous = LAST_STATES.put(cacheKey, stateKey);
        if (stateKey.equals(previous)) {
            return;
        }

        LOGGER.info("[HeroEFDebug] {} {}", prefix(hero, scope), detail);
    }

    public static void repeatedEvent(@Nullable HeroEntity hero, String scope, String repeatKey, String detail) {
        if (!ENABLED) {
            return;
        }

        String heroKey = hero != null ? String.valueOf(hero.getId()) : "null";
        String cacheKey = heroKey + "|" + scope + "|" + repeatKey;
        int count = REPEAT_COUNTS.merge(cacheKey, 1, Integer::sum);
        if (!shouldLogRepeat(count)) {
            return;
        }

        LOGGER.info("[HeroEFDebug] {} repeat={} {}", prefix(hero, scope), count, detail);
    }

    public static String heroCoreState(@Nullable HeroEntity hero) {
        if (hero == null) {
            return "hero=null";
        }

        return "battle=" + hero.isBattleModeActive()
                + ",action=" + hero.getBattleActionState()
                + ",actionTicks=" + hero.getBattleActionTicks()
                + ",combo=" + hero.getBattleComboStep()
                + ",main=" + itemKey(hero.getMainHandItem())
                + ",off=" + itemKey(hero.getOffhandItem());
    }

    public static String heroIdentity(@Nullable HeroEntity hero) {
        if (hero == null) {
            return "hero=null";
        }

        return "dummy=" + isLikelyClientPreview(hero)
                + ",added=" + (!hero.isRemoved())
                + ",alive=" + hero.isAlive()
                + ",removed=" + hero.isRemoved();
    }

    public static boolean isLikelyClientPreview(@Nullable HeroEntity hero) {
        return hero != null
                && hero.level().isClientSide
                && !hero.isRemoved()
                && hero.tickCount == 0;
    }

    public static String resolveCallerTag(@Nullable String callerTag) {
        if (callerTag != null && !callerTag.isBlank()) {
            return callerTag;
        }

        return inferExternalCaller();
    }

    public static String inferExternalCaller() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : trace) {
            if (shouldSkipCallerFrame(frame)) {
                continue;
            }

            return shortenClassName(frame.getClassName()) + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }

        return "unknown";
    }

    public static String captureCallerTrace(int maxFrames) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        int appended = 0;
        for (StackTraceElement frame : trace) {
            if (shouldSkipCallerFrame(frame)) {
                continue;
            }

            if (appended > 0) {
                builder.append(" <= ");
            }
            builder.append(shortenClassName(frame.getClassName()))
                    .append('#')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
            appended++;
            if (appended >= Math.max(1, maxFrames)) {
                break;
            }
        }

        return appended > 0 ? builder.toString() : "trace=empty";
    }

    public static String itemKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = itemId != null ? itemId.toString() : String.valueOf(stack.getItem());
        return key + "x" + stack.getCount();
    }

    public static String animatorKey(@Nullable Object animator) {
        if (animator == null) {
            return "animator=null";
        }

        try {
            return animationKey(invokeMethod(animator, "getPlayerFor", new Object[]{null}));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "animator_error=" + exception.getClass().getSimpleName();
        }
    }

    public static String animatorId(@Nullable Object animator) {
        if (animator == null) {
            return "animator=null";
        }

        try {
            return animationId(invokeMethod(animator, "getPlayerFor", new Object[]{null}));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "animator_error=" + exception.getClass().getSimpleName();
        }
    }

    public static String animationKey(@Nullable Object player) {
        if (player == null) {
            return "player=null";
        }
        if (isAnimationPlayerEmpty(player)) {
            return "anim=empty";
        }

        try {
            ResourceLocation animationId = resolveAnimationId(player);
            String animationKey = animationId != null ? animationId.toString() : "unregistered";
            double prevElapsed = asDouble(invokeMethod(player, "getPrevElapsedTime"));
            double elapsed = asDouble(invokeMethod(player, "getElapsedTime"));
            return animationKey + "@" + String.format(java.util.Locale.ROOT, "%.3f/%.3f", prevElapsed, elapsed);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "anim_error=" + exception.getClass().getSimpleName();
        }
    }

    public static String animationId(@Nullable Object player) {
        if (player == null) {
            return "player=null";
        }
        if (isAnimationPlayerEmpty(player)) {
            return "anim=empty";
        }

        try {
            ResourceLocation animationId = resolveAnimationId(player);
            return animationId != null ? animationId.toString() : "unregistered";
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "anim_error=" + exception.getClass().getSimpleName();
        }
    }

    private static boolean isAnimationPlayerEmpty(Object player) {
        try {
            Object result = invokeMethod(player, "isEmpty");
            return result instanceof Boolean empty && empty;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @Nullable
    private static ResourceLocation resolveAnimationId(Object player) throws ReflectiveOperationException {
        Object realAnimation = invokeMethod(player, "getRealAnimation");
        Object animation = invokeMethod(realAnimation, "get");
        Object registryName = invokeMethod(animation, "getRegistryName");
        return registryName instanceof ResourceLocation location ? location : null;
    }

    private static Object invokeMethod(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = resolveMethod(target.getClass(), methodName, args.length);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName + "/" + args.length);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @Nullable
    private static Method resolveMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static String prefix(@Nullable HeroEntity hero, String tag) {
        String side = hero != null ? (hero.level().isClientSide ? "C" : "S") : "?";
        int heroId = hero != null ? hero.getId() : -1;
        int tick = hero != null ? hero.tickCount : -1;
        return "[" + tag + "][" + side + "][hero=" + heroId + "][tick=" + tick + "][thread=" + Thread.currentThread().getName() + "]";
    }

    private static boolean shouldLogRepeat(int count) {
        return count <= 3 || (count & (count - 1)) == 0 || count % 100 == 0;
    }

    private static boolean shouldSkipCallerFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        String methodName = frame.getMethodName();
        if (className.equals(Thread.class.getName())) {
            return true;
        }
        if (className.equals(HeroEpicFightDebugLog.class.getName())) {
            return true;
        }
        if (className.equals(HeroEntity.class.getName())
                && (methodName.equals("setBattleModeActive") || methodName.equals("setBattleModeActiveFrom") || methodName.equals("applyBattleModeActive"))) {
            return true;
        }
        return false;
    }

    private static String shortenClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}


