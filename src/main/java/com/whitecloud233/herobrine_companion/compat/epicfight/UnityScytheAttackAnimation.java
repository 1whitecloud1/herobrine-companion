package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.util.Optional;
import java.util.ArrayList;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Keyframe;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.ComboAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.TimePairList;
import yesman.epicfight.api.utils.datastructure.ParameterizedHashMap;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Native footwork and jump arcs remain active with mobile combos enabled. */
final class UnityScytheAttackAnimation extends ComboAttackAnimation {
    private final RootMotion rootMotion = new RootMotion();

    UnityScytheAttackAnimation(float transition, float duration,
            AnimationManager.AnimationAccessor<? extends ComboAttackAnimation> accessor,
            AssetAccessor<? extends Armature> armature, Phase... phases) {
        super(transition, accessor, armature, phases);
        movement(this, duration);
    }

    private static void movement(AttackAnimation animation, float duration) {
        animation.addProperty(ActionAnimationProperty.MOVE_VERTICAL, true);
        animation.addProperty(ActionAnimationProperty.CANCELABLE_MOVE, false);
        animation.addProperty(ActionAnimationProperty.STOP_MOVEMENT, true);
        animation.addProperty(ActionAnimationProperty.REMOVE_DELTA_MOVEMENT, true);
        // The animation interval owns gravity; interrupting it cannot leave a
        // persistent noGravity flag on the player or companion.
        animation.addProperty(ActionAnimationProperty.NO_GRAVITY_TIME,
                TimePairList.create(0F, duration + .05F));
    }

    private static final class RootMotion {
        private TransformSheet source;
        private TransformSheet coordinates;

        TransformSheet from(TransformSheet sheet) {
            if (source != sheet) {
                source = sheet;
                var keys = sheet.getKeyframes();
                var result = new ArrayList<Keyframe>();
                for (int i = 0; i < keys.length; i++) {
                    var copy = new Keyframe(keys[i]);
                    float y = keys[i].transform().translation().y;
                    copy.transform().translation().y = Math.max(0F, y);
                    result.add(copy);
                    if (i + 1 < keys.length) {
                        float nextY = keys[i + 1].transform().translation().y;
                        if (y * nextY < 0F) {
                            // Clamp the interpolated trajectory at its actual
                            // zero crossing, not just its two sample endpoints.
                            // Otherwise a fast launch gains artificial height.
                            float time = keys[i].time() + (keys[i + 1].time() - keys[i].time()) * (-y / (nextY - y));
                            var crossing = sheet.getInterpolatedTransform(time);
                            crossing.translation().y = 0F;
                            result.add(new Keyframe(time, crossing));
                        }
                    }
                }
                coordinates = new TransformSheet(result);
            }
            return coordinates;
        }
    }

    @Override public TransformSheet getCoord() { return rootMotion.from(super.getCoord()); }
    @Override public Optional<JointMaskEntry> getJointMaskEntry(LivingEntityPatch<?> patch, boolean current) { return Optional.empty(); }
    @Override public boolean shouldPlayerMove(LocalPlayerPatch patch) { return true; }
    @Override public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) { return 1F; }

    @Override
    public ParameterizedHashMap<EntityState.StateFactor<?>> getStatesMap(LivingEntityPatch<?> patch, float time) {
        return this.stateSpectrum.getStateMap(patch, time);
    }

    static final class Dash extends DashAttackAnimation {
        private final RootMotion rootMotion = new RootMotion();

        Dash(float transition, float duration,
                AnimationManager.AnimationAccessor<? extends DashAttackAnimation> accessor,
                AssetAccessor<? extends Armature> armature, Phase... phases) {
            super(transition, accessor, armature, phases);
            movement(this, duration);
        }

        @Override public TransformSheet getCoord() { return rootMotion.from(super.getCoord()); }
        @Override public Optional<JointMaskEntry> getJointMaskEntry(LivingEntityPatch<?> patch, boolean current) { return Optional.empty(); }
        @Override public boolean shouldPlayerMove(LocalPlayerPatch patch) { return true; }
        @Override public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) { return 1F; }
    }

    static class Air extends AirSlashAnimation {
        private final RootMotion rootMotion = new RootMotion();

        Air(float transition, float duration,
                AnimationManager.AnimationAccessor<? extends AirSlashAnimation> accessor,
                AssetAccessor<? extends Armature> armature, Phase... phases) {
            super(transition, accessor, armature, phases);
            movement(this, duration);
        }

        @Override public TransformSheet getCoord() { return rootMotion.from(super.getCoord()); }
        @Override public Optional<JointMaskEntry> getJointMaskEntry(LivingEntityPatch<?> patch, boolean current) { return Optional.empty(); }
        @Override public boolean shouldPlayerMove(LocalPlayerPatch patch) { return true; }
        @Override public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) { return 1F; }
    }
}
