package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.LinkAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Uses the native airborne hold while the entity descends toward real terrain. */
final class UnityScythePlungeAnimation extends UnityScytheAttackAnimation.Air {
    private final Map<LivingEntityPatch<?>, Integer> starts = new WeakHashMap<>();

    UnityScythePlungeAnimation(float transition, float duration,
            AnimationManager.AnimationAccessor<? extends AirSlashAnimation> accessor,
            AssetAccessor<? extends Armature> armature, Phase... phases) {
        super(transition, duration, accessor, armature, phases);
    }

    @Override public void begin(LivingEntityPatch<?> patch) {
        super.begin(patch);
        synchronized (starts) { starts.put(patch, patch.getOriginal().tickCount); }
    }

    @Override public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
        if (patch == null || current instanceof LinkAnimation) return 1F;
        int elapsed;
        synchronized (starts) {
            elapsed = patch.getOriginal().tickCount - starts.getOrDefault(patch, patch.getOriginal().tickCount - 60);
        }
        float time = patch.getAnimator().getPlayerFor(getAccessor()).getElapsedTime();
        var entity = patch.getOriginal();
        boolean nearFloor = entity.onGround() || !entity.level().noCollision(entity,
                entity.getBoundingBox().move(0D, -1D, 0D).deflate(.05D, 0D, .05D));
        return holdForLanding(time, elapsed, nearFloor) ? 0F : 1F;
    }

    static boolean holdForLanding(float time, int elapsedTicks, boolean nearFloor) {
        // Frames 27..35 are outside the heavy hit window. A void or interruption
        // cannot hold the player forever; gravity belongs to this animation only.
        return time >= .45F && time < 35F / 60F && elapsedTicks < 60 && !nearFloor;
    }

    @Override protected Vec3 getCoordVector(LivingEntityPatch<?> patch,
                                           AssetAccessor<? extends DynamicAnimation> current) {
        Vec3 nativeMotion = super.getCoordVector(patch, current);
        float time = patch.getAnimator().getPlayerFor(getAccessor()).getElapsedTime();
        if (time < .30F || patch.getOriginal().onGround()) return nativeMotion;
        // ActionAnimation.move performs normal entity collision and position
        // synchronization on the owning side. No teleport or manual damage.
        return new Vec3(nativeMotion.x, Math.min(nativeMotion.y, -.35D), nativeMotion.z);
    }
}
