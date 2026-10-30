package com.whitecloud233.herobrine_companion.util;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * 「用 {@link Display.BlockDisplay} 假装一块方块」的统一实现。
 *
 * <p>断刃/断罪的地面碎裂、竞技场切片两个技能各自抄了一份一模一样的实现：
 * 9 个反射 {@code Method} 缓存、{@code Transformation} 拼装、NBT 兜底、宽度/阴影常量。
 * 复制之后已经走样了——1.21.1 这边碎裂用的是
 * {@code setTransformationInterpolationDuration}（1.21 改名后的正确名字），
 * 切片还留着 1.20.1 的 {@code setInterpolationDuration}，在 1.21 上恒解析为 {@code null}，
 * 于是切片每次都在偷偷走 NBT 兜底。收成一份之后不会再出现这种只改一边的漂移。</p>
 *
 * <p><b>为什么还要反射</b>：1.21.1 里 {@code Display} 的这些 setter 仍是 private/protected
 * （{@code BlockDisplay#setBlockState} 更是 private），直接调编译不过。这里先反射调用，
 * 全部失败时退回原版 NBT {@code load(...)}——两条路任一成功即可。</p>
 *
 * <p><b>方法名与 1.20.1 不同</b>：{@code setTransformationInterpolationDuration} /
 * {@code setTransformationInterpolationDelay}（1.20.1 是 {@code setInterpolationDuration} /
 * {@code setInterpolationDelay}）。NBT 键也跟着变成
 * {@code transformation_interpolation_duration}，且 {@code view_range/width/height/shadow_*}
 * 在 1.21 没有公开常量，只能写字面量。</p>
 */
public final class BlockDisplayEffect {

    /** 视距：256 格足够让远处的碎裂块仍然可见，又不至于把整片区域都算进渲染。 */
    public static final float VIEW_RANGE = 256.0F;
    /** 显示块的宽高。略大于 1 是为了盖住方块之间的接缝，不然碎块之间会漏出天空。 */
    public static final float BLOCK_SIZE = 1.15F;

    /** 反射缓存：类加载时解析一次，失败保持 null（之后走 NBT 兜底）。 */
    @Nullable
    private static final Method SET_TRANSFORMATION =
            findDeclaredMethod(Display.class, "setTransformation", Transformation.class);
    @Nullable
    private static final Method SET_INTERPOLATION_DURATION =
            findDeclaredMethod(Display.class, "setTransformationInterpolationDuration", int.class);
    @Nullable
    private static final Method SET_INTERPOLATION_DELAY =
            findDeclaredMethod(Display.class, "setTransformationInterpolationDelay", int.class);
    @Nullable
    private static final Method SET_VIEW_RANGE =
            findDeclaredMethod(Display.class, "setViewRange", float.class);
    @Nullable
    private static final Method SET_WIDTH =
            findDeclaredMethod(Display.class, "setWidth", float.class);
    @Nullable
    private static final Method SET_HEIGHT =
            findDeclaredMethod(Display.class, "setHeight", float.class);
    @Nullable
    private static final Method SET_SHADOW_RADIUS =
            findDeclaredMethod(Display.class, "setShadowRadius", float.class);
    @Nullable
    private static final Method SET_SHADOW_STRENGTH =
            findDeclaredMethod(Display.class, "setShadowStrength", float.class);
    @Nullable
    private static final Method BLOCK_DISPLAY_SET_BLOCK_STATE =
            findDeclaredMethod(Display.BlockDisplay.class, "setBlockState", BlockState.class);

    private BlockDisplayEffect() {
    }

    /**
     * 生成一块「假方块」并加入世界（原始尺寸、无位移）。
     *
     * @param tag 用于后续批量清理的实体 tag
     * @return 世界拒绝生成实体时返回 {@code null}
     */
    @Nullable
    public static Display.BlockDisplay create(ServerLevel level, BlockPos pos, BlockState state, String tag) {
        return create(level, pos, state, tag, Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D));
    }

    /**
     * 生成一块「假方块」并加入世界。
     *
     * @param translation 相对 {@code pos} 的位移（用于把方块顶到半空做漂浮）
     * @param scale       缩放（{@code (1,1,1)} 为原始大小）
     */
    @Nullable
    public static Display.BlockDisplay create(ServerLevel level, BlockPos pos, BlockState state, String tag,
                                              Vec3 translation, Vec3 scale) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.addTag(tag);
        apply(display, state, translation, scale, 0);
        level.addFreshEntity(display);
        return display;
    }

    /** 更新假方块的方块状态与位移，{@code interpolationDuration > 0} 时走插值动画。 */
    public static boolean apply(Display.BlockDisplay display, BlockState state, Vec3 translation,
                                int interpolationDuration) {
        return apply(display, state, translation, new Vec3(1.0D, 1.0D, 1.0D), interpolationDuration);
    }

    /**
     * 更新假方块的方块状态、位移与缩放。
     *
     * @return 反射路径是否全部生效；{@code false} 表示已退回 NBT 兜底（调用方通常不需要处理）
     */
    public static boolean apply(Display.BlockDisplay display, BlockState state, Vec3 translation, Vec3 scale,
                                int interpolationDuration) {
        int duration = Math.max(0, interpolationDuration);

        boolean synced = invokePrivate(BLOCK_DISPLAY_SET_BLOCK_STATE, display, state);

        Transformation transformation = new Transformation(
                new Vector3f((float) translation.x, (float) translation.y, (float) translation.z),
                new Quaternionf(),
                new Vector3f((float) scale.x, (float) scale.y, (float) scale.z),
                new Quaternionf());

        synced |= invokePrivate(SET_TRANSFORMATION, display, transformation);
        synced |= invokePrivate(SET_INTERPOLATION_DURATION, display, duration);
        synced |= invokePrivate(SET_INTERPOLATION_DELAY, display, 0);
        synced |= invokePrivate(SET_VIEW_RANGE, display, VIEW_RANGE);
        synced |= invokePrivate(SET_WIDTH, display, BLOCK_SIZE);
        synced |= invokePrivate(SET_HEIGHT, display, BLOCK_SIZE);
        synced |= invokePrivate(SET_SHADOW_RADIUS, display, 0.0F);
        synced |= invokePrivate(SET_SHADOW_STRENGTH, display, 0.0F);

        if (!synced) {
            // 反射全挂（映射变更/运行时重混淆）时的兜底：走原版反序列化。
            CompoundTag tag = new CompoundTag();
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(state));
            tag.put(Display.TAG_TRANSFORMATION, transformationTag(translation, scale));
            tag.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, duration);
            tag.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
            tag.putFloat("view_range", VIEW_RANGE);
            tag.putFloat("width", BLOCK_SIZE);
            tag.putFloat("height", BLOCK_SIZE);
            tag.putFloat("shadow_radius", 0.0F);
            tag.putFloat("shadow_strength", 0.0F);
            display.load(tag);
        }
        return synced;
    }

    /** {@code Transformation} 的 NBT 形态（纯平移 + 纯缩放，旋转保持单位四元数）。 */
    private static CompoundTag transformationTag(Vec3 translation, Vec3 scale) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floatList((float) translation.x, (float) translation.y, (float) translation.z));
        transformation.put("scale", floatList((float) scale.x, (float) scale.y, (float) scale.z));
        transformation.put("left_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        return transformation;
    }

    private static ListTag floatList(float... values) {
        ListTag listTag = new ListTag();
        for (float value : values) {
            listTag.add(FloatTag.valueOf(value));
        }
        return listTag;
    }

    @Nullable
    private static Method findDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean invokePrivate(@Nullable Method method, Object target, Object... args) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, args);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
