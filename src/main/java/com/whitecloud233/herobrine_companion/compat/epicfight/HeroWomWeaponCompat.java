package com.whitecloud233.herobrine_companion.compat.epicfight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.ex_cap.data.Moveset;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weapons of Miracles（WOM）的 Epic Fight 物品能力适配器。
 *
 * <p>职责单一：只负责「WOM 物品识别」与「WOM 原生 Epic Fight 能力解析」。
 * 战斗 AI（连招行为包装）在 {@link HeroWomCombatBehaviors}，渲染与技能树不属于本类。
 * WOM 能力类通过反射延迟读取，WOM 未安装或版本不匹配时静默回退，不影响 Herobrine 主体。</p>
 *
 * <p>物品→预设映射以 WOM 2.0.176 数据包 {@code data/wom/capabilities/weapons/*.json}
 * 的 {@code type} 字段为准：只有声明为 {@code wom:*} 预设的武器才走 WOM 原生能力；
 * 声明为 {@code epicfight:greatsword}（巨斧）与 {@code epicfight:longsword}（空洞长剑）的
 * 武器由 Epic Fight 标准数据包能力覆盖，无需 WOM 反射。</p>
 *
 * <p>1.21.1 适配：WOM 2.0.176 的 {@link #WOM_PRESETS_CLASS} 预设字段类型从 1.20.1 的
 * {@code Function<Item, ?>} 变为 epicfight 注册表持有者
 * {@code DeferredWeapon / DeferredPreset}（{@code value()} 返回
 * {@code WeaponCapability.Builder} 模板）。本类通过反射读取字段 → {@code value()} →
 * 调用 epicfight 官方 {@code WeaponCapabilityPresets.exCapRegistration} 生成按物品复制
 * 的 Builder → {@code build()}，与数据包装配路径的结果一致。</p>
 */
public final class HeroWomWeaponCompat {
    private static final String WOM_MOD_ID = "wom";
    private static final String WOM_NAMESPACE = "wom";
    private static final String WOM_PRESETS_CLASS =
            "reascer.wom.world.capabilities.item.WOMWeaponCapabilityPresets";
    private static final String AUTO_ATTACK_MOTIONS_FIELD = "autoAttackMotions";
    private static final String EX_CAP_REGISTRATION_CLASS =
            "yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets";
    private static final String EX_CAP_REGISTRATION_METHOD = "exCapRegistration";
    /** WOM 自定义类别武器的默认追击半径（米）。 */
    private static final double WOM_ATTACK_RADIUS = 3.0D;

    /** 权威映射：WOM 数据包中 {@code type} 为 {@code wom:*} 的武器路径 → 预设字段名。 */
    private static final Map<String, String> PRESET_BY_ITEM_PATH = Map.ofEntries(
            Map.entry("agony", "AGONY"),
            Map.entry("tormented_mind", "TORMENT"),
            Map.entry("ruine", "RUINE"),
            Map.entry("satsujin", "SATSUJIN"),
            Map.entry("ender_blaster", "ENDER_BLASTER"),
            Map.entry("antitheus", "ANTITHEUS"),
            Map.entry("herrscher", "HERRSCHER"),
            Map.entry("gesetz", "GESETZ"),
            Map.entry("moonless", "MOONLESS"),
            Map.entry("solar", "SOLAR"),
            Map.entry("napoleon", "NAPOLEON"),
            Map.entry("evil_tachi", "EVIL_TACHI"),
            Map.entry("orbit", "ORBIT"),
            Map.entry("nova", "NOVA"),
            Map.entry("blackstar", "BLACKSTAR"),
            Map.entry("jabberwocky", "CLAWED_GAUNTLE"),
            Map.entry("wooden_staff", "STAFF"),
            Map.entry("stone_staff", "STAFF"),
            Map.entry("iron_staff", "STAFF"),
            Map.entry("golden_staff", "STAFF"),
            Map.entry("diamond_staff", "STAFF"),
            Map.entry("netherite_staff", "STAFF")
    );

    private static final Map<Item, CapabilityItem> NATIVE_CAPABILITIES = new ConcurrentHashMap<>();
    private static volatile boolean presetLookupAttempted;
    private static volatile Class<?> presetsClass;
    private static volatile Method exCapRegistrationMethod;

    private HeroWomWeaponCompat() {
    }

    /** 是否是 WOM 自定义预设武器（WOM 数据包声明为 {@code wom:*} 类型）。 */
    public static boolean isSupported(ItemStack stack) {
        if (!isWomNamespace(stack)) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && PRESET_BY_ITEM_PATH.containsKey(itemId.getPath());
    }

    /** 是否是 WOM 命名空间下的任意物品（含标准 Epic Fight 类型的 WOM 武器）。 */
    public static boolean isWomNamespace(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ModList.get().isLoaded(WOM_MOD_ID)) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && WOM_NAMESPACE.equals(itemId.getNamespace());
    }

    /**
     * 解析 WOM 武器的 Epic Fight 能力。
     *
     * <p>优先返回 Epic Fight 数据包已装配的能力（含 WOM 数据包属性）；数据包尚未加载时
     * 通过反射调用 WOM 预设构建原生能力兜底。非 WOM 物品原样返回既有能力。</p>
     */
    public static CapabilityItem resolveCapability(ItemStack stack, CapabilityItem existingCapability) {
        if (!isWomNamespace(stack)) {
            return existingCapability != null ? existingCapability : CapabilityItem.EMPTY;
        }
        if (existingCapability != null && !existingCapability.isEmpty()) {
            return existingCapability;
        }
        CapabilityItem nativeCapability = resolveNativeCapability(stack);
        return nativeCapability != null ? nativeCapability : CapabilityItem.EMPTY;
    }

    /**
     * 取 WOM 能力当前握持风格的连招动画。
     *
     * <p>1.21.1 epicfight 21.x 的连招权威来源是 {@link MoveSet#getComboAttackAnimations()}
     * （WOM 2.0.176 预设全部走新 MoveSet 系统）；{@link WeaponCapability} 上遗留的
     * {@code autoAttackMotions} 字段（旧 {@code autoAttackMotionMap}）在 MoveSet 存在时
     * 可能为空或被默认拳击动画填充，不能作为主来源。优先 MoveSet，遗留字段仅作回退。</p>
     */
    public static List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> getAutoAttackMotions(
            CapabilityItem capability, LivingEntityPatch<?> patch) {
        if (capability == null || capability.isEmpty() || patch == null) {
            return List.of();
        }

        // 新 API：Moveset.comboAttackAnimations
        try {
            if (capability instanceof WeaponCapability weaponCapability) {
                Moveset set = weaponCapability.getCurrentSet(patch);
                if (set != null) {
                    List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combos =
                            set.getComboAttackAnimations();
                    if (combos != null && !combos.isEmpty()) {
                        return List.copyOf(combos);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }

        // 遗留回退：反射读取 autoAttackMotions 字段
        try {
            Field field = WeaponCapability.class.getDeclaredField(AUTO_ATTACK_MOTIONS_FIELD);
            field.setAccessible(true);
            Object rawMotions = field.get(capability);
            if (!(rawMotions instanceof Map<?, ?> motionsByStyle)) {
                return List.of();
            }

            Style style = capability.getStyle(patch);
            List<?> motions = asComboList(motionsByStyle.get(style));
            if (motions == null || motions.isEmpty()) {
                motions = asComboList(motionsByStyle.get(CapabilityItem.Styles.COMMON));
            }
            if (motions == null || motions.isEmpty()) {
                for (Object value : motionsByStyle.values()) {
                    List<?> candidate = asComboList(value);
                    if (candidate != null && !candidate.isEmpty()) {
                        motions = candidate;
                        break;
                    }
                }
            }
            if (motions == null || motions.isEmpty()) {
                return List.of();
            }

            List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> result = new ArrayList<>();
            for (Object motion : motions) {
                if (motion instanceof AnimationManager.AnimationAccessor<?> accessor) {
                    result.add(castAttackAnimationAccessor(accessor));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    /** WOM 自定义类别武器没有标准玩家式连招，统一提供一个可用的追击半径。 */
    public static double getAttackRadius(ItemStack stack, double fallback) {
        return isSupported(stack) ? WOM_ATTACK_RADIUS : fallback;
    }

    /**
     * 取 WOM 能力的"长按左键"终结技动画。
     *
     * <p>epicfight 21.x 没有独立的"长按攻击"槽位：长按左键等价于持续推进连招表，
     * WOM 武器的连招尾段就是其蓄力终结技（如 antitheus 的 {@code guillotine}、
     * blackstar 的 {@code gravity}）。这里取连招表最后一段作为终结技动画，
     * 供英雄在连招间隙作为独立蓄力动作打出。无连招表或最后一段为空时返回
     * {@code null}，由调用方决定是否启用终结技行为。</p>
     */
    public static AnimationManager.AnimationAccessor<? extends AttackAnimation> getHoldAttackMotion(
            CapabilityItem capability, LivingEntityPatch<?> patch) {
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combos =
                getAutoAttackMotions(capability, patch);
        if (combos.isEmpty()) {
            return null;
        }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> finisher =
                combos.get(combos.size() - 1);
        return (finisher == null || finisher.isEmpty()) ? null : finisher;
    }

    private static CapabilityItem resolveNativeCapability(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return CapabilityItem.EMPTY;
        }
        String presetName = PRESET_BY_ITEM_PATH.get(itemId.getPath());
        if (presetName == null) {
            return CapabilityItem.EMPTY;
        }
        return NATIVE_CAPABILITIES.computeIfAbsent(stack.getItem(), item -> buildNativeCapability(item, presetName));
    }

    private static CapabilityItem buildNativeCapability(Item item, String presetName) {
        try {
            ensurePresetLookup();
            if (presetsClass == null) {
                return CapabilityItem.EMPTY;
            }

            Field presetField = presetsClass.getField(presetName);
            Object preset = presetField.get(null);
            if (preset == null) {
                return CapabilityItem.EMPTY;
            }

            // WOM 2.0.176：预设字段是 epicfight 注册表持有者（DeferredWeapon/DeferredPreset），
            // value() 返回 WeaponCapability.Builder 模板（GESETZ 亦是，见 GesetzCapability 构造器）。
            Method valueMethod = preset.getClass().getMethod("value");
            Object builder = valueMethod.invoke(preset);
            if (builder == null) {
                return CapabilityItem.EMPTY;
            }

            // 与 epicfight 官方路径一致：exCapRegistration 复制模板并应用物品/层数据，再 build()。
            Method exCapRegistration = ensureExCapRegistrationLookup();
            if (exCapRegistration == null) {
                return CapabilityItem.EMPTY;
            }
            ResourceLocation presetId = ResourceLocation.fromNamespaceAndPath(
                    WOM_NAMESPACE, presetName.toLowerCase(Locale.ROOT));
            Object perItemBuilder = exCapRegistration.invoke(null, Map.entry(presetId, builder), item);
            if (perItemBuilder == null) {
                return CapabilityItem.EMPTY;
            }

            Method buildMethod = perItemBuilder.getClass().getMethod("build");
            Object built = buildMethod.invoke(perItemBuilder);
            return built instanceof CapabilityItem capability ? capability : CapabilityItem.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 反射失败视为不支持，静默回退。
        }
        return CapabilityItem.EMPTY;
    }

    private static synchronized void ensurePresetLookup() {
        if (presetLookupAttempted) {
            return;
        }
        presetLookupAttempted = true;
        try {
            presetsClass = Class.forName(WOM_PRESETS_CLASS, false, HeroWomWeaponCompat.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            presetsClass = null;
        }
    }

    private static synchronized Method ensureExCapRegistrationLookup() {
        if (exCapRegistrationMethod != null) {
            return exCapRegistrationMethod;
        }
        try {
            Class<?> exCapClass = Class.forName(EX_CAP_REGISTRATION_CLASS, false,
                    HeroWomWeaponCompat.class.getClassLoader());
            exCapRegistrationMethod = exCapClass.getMethod(EX_CAP_REGISTRATION_METHOD, Map.Entry.class, Item.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            exCapRegistrationMethod = null;
        }
        return exCapRegistrationMethod;
    }

    private static List<?> asComboList(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    @SuppressWarnings("unchecked")
    private static AnimationManager.AnimationAccessor<? extends AttackAnimation> castAttackAnimationAccessor(
            AnimationManager.AnimationAccessor<?> accessor) {
        return (AnimationManager.AnimationAccessor<? extends AttackAnimation>) accessor;
    }
}
