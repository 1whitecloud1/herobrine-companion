package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.util.Optional;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.ComboAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.datastructure.ParameterizedHashMap;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Full body polearm steps must survive Epic Fight's optional mobile-combo mode. */
final class MediaPipeScytheAttackAnimation extends ComboAttackAnimation {
    MediaPipeScytheAttackAnimation(float transition,
            AnimationManager.AnimationAccessor<? extends ComboAttackAnimation> accessor,
            AssetAccessor<? extends Armature> armature, Phase... phases) {
        super(transition, accessor, armature, phases);
        addProperty(AnimationProperty.ActionAnimationProperty.CANCELABLE_MOVE, false);
    }

    @Override
    public Optional<JointMaskEntry> getJointMaskEntry(LivingEntityPatch<?> patch, boolean useCurrentMotion) {
        // The standard combo mask drops thighs and legs at HIGHEST priority.
        return Optional.empty();
    }

    @Override
    public boolean shouldPlayerMove(LocalPlayerPatch patch) {
        // This is authored attack displacement, independent of stiffComboAttacks.
        return true;
    }

    @Override
    public ParameterizedHashMap<EntityState.StateFactor<?>> getStatesMap(LivingEntityPatch<?> patch, float time) {
        // Keep the phase's movement lock/living-motion state. The superclass
        // replaces these with walking state when stiffComboAttacks is disabled.
        return this.stateSpectrum.getStateMap(patch, time);
    }

    @Override
    public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
        return 1F;
    }
}

