package com.whitecloud233.herobrine_companion.compat.epicfight;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画常量查找器（单一职责）：把 EFN 动画类的静态字段名解析成 {@link AnimationAccessor}，
 * 带缓存；是唯一接触"EFN 动画类反射"的实现类，其它层只通过它取值。
 */
final class HeroNightfallAnimationLookup {
    private static final String EFN_MOD_ID = "efn";

    private static final Map<String, AnimationAccessor<? extends StaticAnimation>> ANIMATION_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_FIELDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MISSING_FIELDS = ConcurrentHashMap.newKeySet();

    private HeroNightfallAnimationLookup() {
    }

    static AnimationAccessor<? extends StaticAnimation> resolve(String owner, String fieldName) {
        AnimationAccessor<? extends StaticAnimation> original = resolveOriginal(owner, fieldName);
        if (original == null || original.isEmpty()) {
            // 跳过空/延迟动画字段：避免 remap 触发 EF AnimationManager.checkNull 的 dev 栈刷屏
            // （空 accessor 本就不能播放，视为无此动画即可）
            if (LOGGED_MISSING_FIELDS.add(owner + "#" + fieldName)) {
                HeroEpicFightDebugLog.event(null, "nightfallAnimLookup", "missing/empty animation field: " + owner + "#" + fieldName);
            }
            return null;
        }
        return HeroNightfallAnimationRegistry.remap(original);
    }

    static AnimationAccessor<? extends StaticAnimation> resolveOriginal(String owner, String fieldName) {
        String key = owner + "#" + fieldName;
        AnimationAccessor<? extends StaticAnimation> cached = ANIMATION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED_FIELDS.contains(key)) {
            return null;
        }

        try {
            Class<?> ownerClass = Class.forName(owner, false, HeroNightfallAnimationLookup.class.getClassLoader());
            Field field;
            try {
                field = ownerClass.getField(fieldName);
            } catch (NoSuchFieldException ignored) {
                field = ownerClass.getDeclaredField(fieldName);
                field.setAccessible(true);
            }
            Object value = field.get(null);
            if (value instanceof AnimationAccessor<?> animation && (!animation.isEmpty() || isDeferredEfnAnimationOwner(owner))) {
                @SuppressWarnings("unchecked")
                AnimationAccessor<? extends StaticAnimation> casted = (AnimationAccessor<? extends StaticAnimation>) animation;
                ANIMATION_CACHE.put(key, casted);
                return casted;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        try {
            AnimationAccessor<? extends StaticAnimation> animation = AnimationManager.byKey(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(EFN_MOD_ID, fieldName.toLowerCase(Locale.ROOT)));
            if (animation != null && !animation.isEmpty()) {
                ANIMATION_CACHE.put(key, animation);
                return animation;
            }
        } catch (RuntimeException ignored) {
        }

        if (!isDeferredEfnAnimationOwner(owner)) {
            FAILED_FIELDS.add(key);
        }
        return null;
    }

    static Set<AnimationAccessor<? extends StaticAnimation>> collectReferencedOriginalAnimations(Collection<HeroNightfallProfile> profiles) {
        LinkedHashSet<AnimationAccessor<? extends StaticAnimation>> animations = new LinkedHashSet<>();
        for (HeroNightfallProfile profile : profiles) {
            profile.livingOverrides().values().forEach(field -> addResolvedOriginal(animations, field));
            profile.comboAnimations().forEach(field -> addResolvedOriginal(animations, field));
            for (HeroNightfallSkillSeries series : profile.skillSeries()) {
                series.animations().forEach(field -> addResolvedOriginal(animations, field));
            }
        }
        return Set.copyOf(animations);
    }

    private static void addResolvedOriginal(Set<AnimationAccessor<? extends StaticAnimation>> animations, HeroNightfallAnimationField field) {
        AnimationAccessor<? extends StaticAnimation> animation = field.resolveOriginal();
        if (animation != null && !animation.isEmpty() && EFN_MOD_ID.equals(animation.registryName().getNamespace())) {
            animations.add(animation);
        }
    }

    static boolean isDeferredEfnAnimationOwner(String owner) {
        return owner != null && owner.startsWith("com.hm.efn.gameasset.animations.");
    }
}
