package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.MainFrameAnimation;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.api.animation.types.StateSpectrum;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation.JointColliderPair;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.model.armature.HumanoidArmature;

final class HeroNightfallAnimationRegistry {
   private static final String EFN_MOD_ID = "efn";
   private static final String REBIND_PATH_PREFIX = "nightfall_rebind/";
   private static final Map<ResourceLocation, AnimationManager.AnimationAccessor<? extends StaticAnimation>> REMAPPED_BY_ORIGINAL_ID = new ConcurrentHashMap();
   private static final Field STATIC_PROPERTIES_FIELD = getField(StaticAnimation.class, "properties");
   private static final Field STATE_BLUEPRINT_FIELD = getField(StaticAnimation.class, "stateSpectrumBlueprint");
   private static final Field BLUEPRINT_CURRENT_STATE_FIELD = getField(StateSpectrum.Blueprint.class, "currentState");
   private static final Field BLUEPRINT_TIME_PAIRS_FIELD = getField(StateSpectrum.Blueprint.class, "timePairs");
   private static final Field PHASE_PROPERTIES_FIELD = getField(AttackAnimation.Phase.class, "properties");

   private HeroNightfallAnimationRegistry() {
   }

   static void onAnimationRegistry(AnimationManager.AnimationRegistryEvent event) {
      if (event != null) {
         event.newBuilder("herobrine_companion", HeroNightfallAnimationRegistry::buildHeroNightfallAnimations);
      }
   }

   @Nullable
   static AnimationManager.@Nullable AnimationAccessor<? extends StaticAnimation> remap(@Nullable AnimationManager.@Nullable AnimationAccessor<? extends StaticAnimation> originalAccessor) {
      if (originalAccessor != null && !originalAccessor.isEmpty()) {
         return !"efn".equals(originalAccessor.registryName().getNamespace()) ? originalAccessor : (AnimationManager.AnimationAccessor)REMAPPED_BY_ORIGINAL_ID.getOrDefault(originalAccessor.registryName(), originalAccessor);
      } else {
         return originalAccessor;
      }
   }

   private static void buildHeroNightfallAnimations(AnimationManager.AnimationBuilder builder) {
      AssetAccessor<HumanoidArmature> heroArmature = HeroEpicFightBridge.heroNightfallArmature();
      if (builder != null && heroArmature != null) {
         for(AnimationManager.AnimationAccessor<? extends StaticAnimation> originalAccessor : HeroNightfallMovesets.collectReferencedOriginalAnimations()) {
            if (!AnimationManager.checkNull(originalAccessor) && "efn".equals(originalAccessor.registryName().getNamespace())) {
               ResourceLocation originalId = originalAccessor.registryName();
               if (!REMAPPED_BY_ORIGINAL_ID.containsKey(originalId)) {
                  String var10000 = originalId.getNamespace();
                  String clonePath = "nightfall_rebind/" + var10000 + "/" + originalId.getPath();
                  AnimationManager.AnimationAccessor<? extends StaticAnimation> cloneAccessor = builder.nextAccessor(clonePath, (accessor) -> {
                     StaticAnimation original = (StaticAnimation)originalAccessor.get();
                     if (original == null) {
                        throw new IllegalStateException("Missing original animation for rebinding: " + String.valueOf(originalId));
                     } else {
                        return createClone(original, accessor, heroArmature);
                     }
                  });
                  REMAPPED_BY_ORIGINAL_ID.put(originalId, cloneAccessor);
                  cloneAccessor.get();
               }
            }
         }

      }
   }

   private static <T extends StaticAnimation> T createClone(StaticAnimation original, AnimationManager.AnimationAccessor<T> cloneAccessor, AssetAccessor<? extends Armature> heroArmature) {
      String originalKey = original.getRegistryName().toString();
      float transitionTime = original.getTransitionTime();
      StaticAnimation clone;
      if (original instanceof AirSlashAnimation airSlashAnimation) {
         clone = new AirSlashAnimation(transitionTime, originalKey, heroArmature, clonePhases(airSlashAnimation, heroArmature));
      } else if (original instanceof DashAttackAnimation dashAttackAnimation) {
         clone = new DashAttackAnimation(transitionTime, originalKey, heroArmature, clonePhases(dashAttackAnimation, heroArmature));
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
      return (T)clone;
   }

   private static void copyTotalTime(StaticAnimation original, StaticAnimation clone) {
      float clipTime = original.getTotalTime();
      if (Float.isNaN(clipTime) || clipTime <= 0.0F) {
         clipTime = 1.0F;
      }

      clone.setTotalTime(clipTime);
   }

   private static AttackAnimation.Phase[] clonePhases(AttackAnimation original, AssetAccessor<? extends Armature> heroArmature) {
      Armature armature = (Armature)heroArmature.get();
      AttackAnimation.Phase[] cloned = new AttackAnimation.Phase[original.phases.length];

      for(int i = 0; i < original.phases.length; ++i) {
         AttackAnimation.Phase phase = original.phases[i];
         AttackAnimation.JointColliderPair[] originalColliders = phase.getColliders();
         List<AttackAnimation.JointColliderPair> clonedColliders = new ArrayList(originalColliders.length);

         for(int j = 0; j < originalColliders.length; ++j) {
            AttackAnimation.JointColliderPair originalPair = originalColliders[j];
            Joint originalJoint = (Joint)originalPair.getFirst();
            Joint remappedJoint = armature.searchJointByName(originalJoint.getName());
            if (remappedJoint != null) {
               Collider collider = (Collider)originalPair.getSecond();
               clonedColliders.add(JointColliderPair.of(remappedJoint, collider == null ? null : collider.deepCopy()));
            }
         }

         AttackAnimation.Phase clonedPhase = new AttackAnimation.Phase(phase.start, phase.antic, phase.preDelay, phase.contact, phase.recovery, phase.end, phase.noStateBind, phase.hand, (AttackAnimation.JointColliderPair[])clonedColliders.toArray((x$0) -> new AttackAnimation.JointColliderPair[x$0]));
         copyPhaseProperties(phase, clonedPhase);
         cloned[i] = clonedPhase;
      }

      return cloned;
   }

   private static void copyProperties(StaticAnimation original, StaticAnimation clone) {
      try {
         Map<Object, Object> originalProperties = (Map)STATIC_PROPERTIES_FIELD.get(original);
         Map<Object, Object> cloneProperties = (Map)STATIC_PROPERTIES_FIELD.get(clone);
         cloneProperties.clear();
         cloneProperties.putAll(originalProperties);
      } catch (IllegalAccessException exception) {
         throw new IllegalStateException("Failed to copy animation properties from " + String.valueOf(original.getRegistryName()), exception);
      }
   }

   private static void copyStateBlueprint(StaticAnimation original, StaticAnimation clone) {
      try {
         StateSpectrum.Blueprint originalBlueprint = (StateSpectrum.Blueprint)STATE_BLUEPRINT_FIELD.get(original);
         StateSpectrum.Blueprint cloneBlueprint = (StateSpectrum.Blueprint)STATE_BLUEPRINT_FIELD.get(clone);
         BLUEPRINT_CURRENT_STATE_FIELD.set(cloneBlueprint, BLUEPRINT_CURRENT_STATE_FIELD.get(originalBlueprint));
         Set clonePairs = (Set)BLUEPRINT_TIME_PAIRS_FIELD.get(cloneBlueprint);
         clonePairs.clear();
         clonePairs.addAll((Set)BLUEPRINT_TIME_PAIRS_FIELD.get(originalBlueprint));
      } catch (IllegalAccessException exception) {
         throw new IllegalStateException("Failed to copy state spectrum blueprint from " + String.valueOf(original.getRegistryName()), exception);
      }
   }

   private static void copyPhaseProperties(AttackAnimation.Phase original, AttackAnimation.Phase clone) {
      try {
         Map<Object, Object> originalProperties = (Map)PHASE_PROPERTIES_FIELD.get(original);
         Map<Object, Object> cloneProperties = (Map)PHASE_PROPERTIES_FIELD.get(clone);
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
