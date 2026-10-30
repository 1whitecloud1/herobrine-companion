package com.whitecloud233.herobrine_companion.util;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 可选依赖的反射网关。
 *
 * <p><b>为什么只有这一份</b>：软依赖模组的字段/方法名会随版本变化（例如厨具模组 1.6.0 把
 * 石磨绑定的形参从 {@code Mob} 改成了 {@code LivingEntity}），编译期直接调用会抛
 * {@code NoSuchMethodError}，只能反射。项目里此前有三套各写各的：
 * {@code compat.kaleidoscope.KitchenReflection}（最完整，已改为委托本类）、
 * {@code integration.epicfight.HeroEpicFightCompat#invokeStatic}（兼容附属）、
 * {@code combat.HeroCombatDebugLog#invokeMethod}。</p>
 *
 * <p><b>后两处刻意没有并进来</b>，它们的语义与本类相反：</p>
 * <ul>
 *   <li>{@code HeroCombatDebugLog#invokeMethod} 是<b>调试诊断</b>路径，
 *       方法缺失要抛 {@code NoSuchMethodException} 并在日志里标明是哪一条链断了；
 *       静默降级成 null 会让"到底哪个反射点失效"变得查不出来。</li>
 *   <li>{@code HeroEpicFightCompat#invokeStatic} 走 {@code getMethod}（public 查找）
 *       并<b>向上抛} {@code ReflectiveOperationException}，由调用方决定降级策略；
 *       本类走 {@code getDeclared*}（能拿到非 public 成员）且就地吞掉异常。
 *       两者查找面与错误传播方向都不同。</li>
 * </ul>
 * <p>只有当它们的语义改成"失败即降级"时才应该合并，否则合并会掩盖诊断信息。</p>
 *
 * <p><b>统一语义</b>：本类所有方法的"目标不存在 / 反射失败"一律降级为
 * {@code false} 或 {@code null}，<b>不抛异常</b>。可选依赖缺失时模组必须还能启动，
 * 这是所有调用方的共同前提。</p>
 *
 * <p><b>查找策略</b>：字段与方法都沿父类链向上找（{@code getDeclared*} 而不是
 * {@code getMethod}），因为模组常把方法定义在中间抽象类上，而 {@code getMethod}
 * 只能拿到 public 且对非 public 成员无效。</p>
 */
public final class Reflect {

    private Reflect() {
    }

    // ---------------- 字段 ----------------

    /** 沿父类链查找字段；找不到返回 null。 */
    @Nullable
    public static Field findField(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        return null;
    }

    /** 读取字段值；字段缺失或访问失败返回 null。 */
    @Nullable
    public static Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    /** 写入字段值；成功返回 true，字段缺失或访问失败返回 false。 */
    public static boolean setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) {
            return false;
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    // ---------------- 方法 ----------------

    /**
     * 按名字和参数个数查找方法。
     *
     * <p>刻意只按<b>个数</b>匹配而不比对参数类型：可选依赖换版本时最常变的就是形参类型
     * （见类注释里的例子），按类型匹配反而会全部落空。</p>
     */
    @Nullable
    public static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
        }
        return null;
    }

    /** 调用并取返回值；方法缺失或执行失败返回 null。 */
    @Nullable
    public static Object callMethod(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName, arguments == null ? 0 : arguments.length);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** 调用；只关心是否成功时用它。 */
    public static boolean invokeMethod(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return false;
        }
        Method method = findMethod(target.getClass(), methodName, arguments == null ? 0 : arguments.length);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(target, arguments);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** 无参 getter 的简写。 */
    @Nullable
    public static Object invokeGetter(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // ---------------- 类 ----------------

    /** 按名加载类（不初始化）；缺失或链接错误返回 null。 */
    @Nullable
    public static Class<?> findOptionalClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }
}
