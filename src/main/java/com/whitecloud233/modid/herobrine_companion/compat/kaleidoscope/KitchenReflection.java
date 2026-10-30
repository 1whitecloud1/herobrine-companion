package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.util.Reflect;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 厨具模组版本差异的兼容边界。
 *
 * <p>实现全部委托 {@link Reflect}：反射细节（父类链查找、失败降级）只有那一份，
 * 这里只保留"用哪个类加载器、哪些成员会被用到"这类烹饪域知识。</p>
 */
final class KitchenReflection {
    private KitchenReflection() {
    }

    static boolean setFieldValue(Object target, String fieldName, Object value) {
        return Reflect.setFieldValue(target, fieldName, value);
    }

    @Nullable
    static Object invokeGetter(Object target, String methodName) {
        return Reflect.invokeGetter(target, methodName);
    }

    /**
     * 按名字和参数个数找方法。厨具模组在 1.6.0 把石磨绑定的形参从 {@code Mob} 改成了
     * {@code LivingEntity}，直接编译期调用会抛 NoSuchMethodError，只能按参数个数反射。
     */
    @Nullable
    static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        return Reflect.findMethod(type, methodName, parameterCount);
    }

    /** 反射调用指定方法，成功返回 true。 */
    static boolean invokeMethod(Object target, String methodName, Object... arguments) {
        return Reflect.invokeMethod(target, methodName, arguments);
    }

    /** 反射调用并取返回值，方法缺失或抛异常时返回 null。 */
    @Nullable
    static Object callMethod(Object target, String methodName, Object... arguments) {
        return Reflect.callMethod(target, methodName, arguments);
    }

    @Nullable
    static Field findField(Class<?> type, String fieldName) {
        return Reflect.findField(type, fieldName);
    }

    @Nullable
    static Object getFieldValue(Object target, String fieldName) {
        return Reflect.getFieldValue(target, fieldName);
    }

    @Nullable
    static Class<?> findOptionalClass(String className) {
        return Reflect.findOptionalClass(className, HeroKaleidoscopeCompat.class.getClassLoader());
    }
}
