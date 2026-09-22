package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.util.Optional;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.TimePairList;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Carries the complete tilted body/weapon spin along its authored jump arc. */
final class MediaPipeScytheDashAnimation extends DashAttackAnimation {
    private TransformSheet sourceCoords;
    private TransformSheet jumpCoords;

    MediaPipeScytheDashAnimation(float transition, float duration,
            AnimationManager.AnimationAccessor<? extends DashAttackAnimation> accessor,
            AssetAccessor<? extends Armature> armature, Phase... phases) {
        super(transition, accessor, armature, phases);
        addProperty(AnimationProperty.ActionAnimationProperty.MOVE_VERTICAL, true);
        addProperty(AnimationProperty.ActionAnimationProperty.CANCELABLE_MOVE, false);
        addProperty(AnimationProperty.ActionAnimationProperty.STOP_MOVEMENT, true);
        addProperty(AnimationProperty.ActionAnimationProperty.REMOVE_DELTA_MOVEMENT, true);
        // Epic Fight controls gravity for this interval without persisting a
        // noGravity flag on the entity, including when an attack is interrupted.
        addProperty(AnimationProperty.ActionAnimationProperty.NO_GRAVITY_TIME,
                TimePairList.create(0F, duration + .05F));
    }

    static TransformSheet jumpCoordinates(TransformSheet source) {
        TransformSheet result = source.copyAll();
        for (var key : result.getKeyframes()) {
            // correctRootJoint consumes positive Root height, while retaining
            // crouch below the bind pelvis. Match that split in entity motion:
            // pushing a crouch into the floor would add height on the next tick.
            var translation = key.transform().translation();
            translation.y = Math.max(0F, translation.y);
        }
        return result;
    }

    @Override
    public TransformSheet getCoord() {
        TransformSheet source = super.getCoord();
        if (source != sourceCoords) {
            sourceCoords = source;
            jumpCoords = jumpCoordinates(source);
        }
        return jumpCoords;
    }

    @Override
    public Optional<JointMaskEntry> getJointMaskEntry(LivingEntityPatch<?> patch, boolean currentMotion) {
        return Optional.empty();
    }

    @Override
    public boolean shouldPlayerMove(LocalPlayerPatch patch) {
        return true;
    }

    @Override
    public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
        return 1F;
    }
}
