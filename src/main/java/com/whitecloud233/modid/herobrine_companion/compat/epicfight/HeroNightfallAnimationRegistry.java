package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.AnimationManager.AnimationBuilder;
import yesman.epicfight.api.animation.AnimationManager.AnimationRegistryEvent;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.MainFrameAnimation;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.api.animation.types.StateSpectrum;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.model.armature.HumanoidArmature;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class HeroNightfallAnimationRegistry {
    private static final String EFN_MOD_ID = "efn";
    private static final String REBIND_PATH_PREFIX = "nightfall_rebind/";

    private static final Map<ResourceLocation, AnimationAccessor<? extends StaticAnimation>> REMAPPED_BY_ORIGINAL_ID = new ConcurrentHashMap<>();
    private static final Field STATIC_PROPERTIES_FIELD = getField(StaticAnimation.class, "properties");
    private static final Field STATE_BLUEPRINT_FIELD = getField(StaticAnimation.class, "stateSpectrumBlueprint");
    private static final Field BLUEPRINT_CURRENT_STATE_FIELD = getField(StateSpectrum.Blueprint.class, "currentState");
    private static final Field BLUEPRINT_TIME_PAIRS_FIELD = getField(StateSpectrum.Blueprint.class, "timePairs");
    private static final Field PHASE_PROPERTIES_FIELD = getField(AttackAnimation.Phase.class, "properties");

    private HeroNightfallAnimationRegistry() {
    }

    static void onAnimationRegistry(AnimationRegistryEvent event) {
        if (event == null) {
            return;
        }

        // Hero 骨架（含爪/刺轮关节）的注册改在 FMLCommonSetupEvent（实体已注册后）进行，
        // 见 HeroEpicFightBridge.onCommonSetup；这里只负责动画克隆
        event.newBuilder(HerobrineCompanion.MODID, HeroNightfallAnimationRegistry::buildHeroNightfallAnimations);
    }

    @Nullable
    static AnimationAccessor<? extends StaticAnimation> remap(@Nullable AnimationAccessor<? extends StaticAnimation> originalAccessor) {
        if (originalAccessor == null || originalAccessor.isEmpty()) {
            // 不调用 AnimationManager.checkNull：它对 null/empty 在 dev 下打印整段栈（刷屏），
            // 且空 accessor 无需重映射，直接原样返回
            return originalAccessor;
        }

        if (!EFN_MOD_ID.equals(originalAccessor.registryName().getNamespace())) {
            return originalAccessor;
        }

        return REMAPPED_BY_ORIGINAL_ID.getOrDefault(originalAccessor.registryName(), originalAccessor);
    }

    private static void buildHeroNightfallAnimations(AnimationBuilder builder) {
        AssetAccessor<HumanoidArmature> heroArmature = HeroEpicFightBridge.heroNightfallArmature();
        if (builder == null || heroArmature == null) {
            return;
        }

        Set<AnimationAccessor<? extends StaticAnimation>> originals = HeroNightfallMovesets.collectReferencedOriginalAnimations();
        for (AnimationAccessor<? extends StaticAnimation> originalAccessor : originals) {
            if (AnimationManager.checkNull(originalAccessor) || !EFN_MOD_ID.equals(originalAccessor.registryName().getNamespace())) {
                continue;
            }

            ResourceLocation originalId = originalAccessor.registryName();
            if (REMAPPED_BY_ORIGINAL_ID.containsKey(originalId)) {
                continue;
            }

            String clonePath = REBIND_PATH_PREFIX + originalId.getNamespace() + "/" + originalId.getPath();
            AnimationAccessor<? extends StaticAnimation> cloneAccessor = builder.nextAccessor(clonePath, accessor -> {
                StaticAnimation original = originalAccessor.get();
                if (original == null) {
                    throw new IllegalStateException("Missing original animation for rebinding: " + originalId);
                }
                return createClone(original, accessor, heroArmature);
            });
            REMAPPED_BY_ORIGINAL_ID.put(originalId, cloneAccessor);
            cloneAccessor.get();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends StaticAnimation> T createClone(StaticAnimation original,
                                                              AnimationAccessor<T> cloneAccessor,
                                                              AssetAccessor<? extends Armature> heroArmature) {
        String originalKey = original.getRegistryName().toString();
        float transitionTime = original.getTransitionTime();

        StaticAnimation clone;
        if (original instanceof AirSlashAnimation airSlashAnimation) {
            clone = new AirSlashAnimation(transitionTime, originalKey, heroArmature, clonePhases(airSlashAnimation, heroArmature));
        } else if (original instanceof DashAttackAnimation dashAttackAnimation) {
            clone = new DashAttackAnimation(transitionTime, originalKey, heroArmature, clonePhases(dashAttackAnimation, heroArmature));
        } else if (original instanceof BasicAttackAnimation basicAttackAnimation) {
            clone = new BasicAttackAnimation(transitionTime, originalKey, heroArmature, clonePhases(basicAttackAnimation, heroArmature));
        } else if (original instanceof AttackAnimation attackAnimation) {
            clone = new AttackAnimation(transitionTime, originalKey, heroArmature, clonePhases(attackAnimation, heroArmature));
        } else if (original instanceof MovementAnimation movementAnimation) {
            clone = new MovementAnimation(transitionTime, movementAnimation.isRepeat(), originalKey, heroArmature);
        } else if (original instanceof ActionAnimation) {
            clone = new ActionAnimation(transitionTime, Float.MAX_VALUE, originalKey, heroArmature);
        } else if (original instanceof MainFrameAnimation) {
            clone = new MainFrameAnimation(transitionTime, originalKey, heroArmature);
        } else {
            clone = new StaticAnimation(transitionTime, original.isRepeat(), originalKey, heroArmature);
        }

        clone.setAccessor(cloneAccessor);
        copyProperties(original, clone);
        copyStateBlueprint(original, clone);
        copyTotalTime(original, clone);
        return (T) clone;
    }

    /**
     * 关键防御：克隆动画的 AnimationClip 是新建的空 clip，clipTime 默认为 0。
     * 当 Hero 用 ConcurrentLinkAnimation 做动作/武器过渡时 {@code elapsed % totalTime} 得 NaN，
     * 进而触发 {@code AnimationClip.getPoseInTime(NaN)} 的二分查找死循环（渲染线程卡死）。
     * 从原动画继承有效 clipTime，避免 totalTime 为 0/NaN。
     */
    private static void copyTotalTime(StaticAnimation original, StaticAnimation clone) {
        float clipTime = original.getTotalTime();
        if (Float.isNaN(clipTime) || clipTime <= 0.0F) {
            clipTime = 1.0F;
        }
        clone.setTotalTime(clipTime);
    }

    private static AttackAnimation.Phase[] clonePhases(AttackAnimation original, AssetAccessor<? extends Armature> heroArmature) {
        Armature armature = heroArmature.get();
        AttackAnimation.Phase[] cloned = new AttackAnimation.Phase[original.phases.length];

        for (int i = 0; i < original.phases.length; i++) {
            AttackAnimation.Phase phase = original.phases[i];
            AttackAnimation.JointColliderPair[] originalColliders = phase.getColliders();
            List<AttackAnimation.JointColliderPair> clonedColliders = new ArrayList<>(originalColliders.length);

            for (int j = 0; j < originalColliders.length; j++) {
                AttackAnimation.JointColliderPair originalPair = originalColliders[j];
                Joint originalJoint = originalPair.getFirst();
                Joint remappedJoint = armature.searchJointByName(originalJoint.getName());
                if (remappedJoint == null) {
                    continue;
                }
                Collider collider = originalPair.getSecond();
                clonedColliders.add(AttackAnimation.JointColliderPair.of(remappedJoint, collider == null ? null : collider.deepCopy()));
            }

            AttackAnimation.Phase clonedPhase = new AttackAnimation.Phase(
                    phase.start,
                    phase.antic,
                    phase.preDelay,
                    phase.contact,
                    phase.recovery,
                    phase.end,
                    phase.noStateBind,
                    phase.hand,
                    clonedColliders.toArray(AttackAnimation.JointColliderPair[]::new)
            );
            copyPhaseProperties(phase, clonedPhase);
            cloned[i] = clonedPhase;
        }

        return cloned;
    }

    @SuppressWarnings("unchecked")
    private static void copyProperties(StaticAnimation original, StaticAnimation clone) {
        try {
            Map<Object, Object> originalProperties = (Map<Object, Object>) STATIC_PROPERTIES_FIELD.get(original);
            Map<Object, Object> cloneProperties = (Map<Object, Object>) STATIC_PROPERTIES_FIELD.get(clone);
            cloneProperties.clear();
            cloneProperties.putAll(originalProperties);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to copy animation properties from " + original.getRegistryName(), exception);
        }
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyStateBlueprint(StaticAnimation original, StaticAnimation clone) {
        try {
            StateSpectrum.Blueprint originalBlueprint = (StateSpectrum.Blueprint) STATE_BLUEPRINT_FIELD.get(original);
            StateSpectrum.Blueprint cloneBlueprint = (StateSpectrum.Blueprint) STATE_BLUEPRINT_FIELD.get(clone);
            BLUEPRINT_CURRENT_STATE_FIELD.set(cloneBlueprint, BLUEPRINT_CURRENT_STATE_FIELD.get(originalBlueprint));
            Set clonePairs = (Set) BLUEPRINT_TIME_PAIRS_FIELD.get(cloneBlueprint);
            clonePairs.clear();
            clonePairs.addAll((Set) BLUEPRINT_TIME_PAIRS_FIELD.get(originalBlueprint));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to copy state spectrum blueprint from " + original.getRegistryName(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void copyPhaseProperties(AttackAnimation.Phase original, AttackAnimation.Phase clone) {
        try {
            Map<Object, Object> originalProperties = (Map<Object, Object>) PHASE_PROPERTIES_FIELD.get(original);
            Map<Object, Object> cloneProperties = (Map<Object, Object>) PHASE_PROPERTIES_FIELD.get(clone);
            cloneProperties.clear();
            cloneProperties.putAll(originalProperties);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to copy attack phase properties", exception);
        }
    }

    private static Field getField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}


