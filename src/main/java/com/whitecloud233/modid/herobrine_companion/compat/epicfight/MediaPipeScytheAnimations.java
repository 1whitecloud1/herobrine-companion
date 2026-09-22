package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Reviewed MediaPipe motion: left hand on the shaft, right hand driving its tail. */
final class MediaPipeScytheAnimations {
    static final String PLAYER_PREFIX = "player/poem_mediapipe/";
    static final String HERO_PREFIX = "hero/poem_mediapipe/";
    static final int COMBO_COUNT = 8;
    private static final Collider BLADE = new MultiOBBCollider(3,
            .190283D, 1.481955D, .747754D, -.026746D, -.473613D, -2.071904D);
    // Keep the established forward melee reach while the calibrated Tool_R
    // collider follows the visible blade. Both share one phase's hit tracking.
    private static final Collider FRONT_SWEEP = new MultiOBBCollider(3,
            1.25D, 1.25D, 2.35D, 0D, .85D, -2.15D);

    private MediaPipeScytheAnimations() { }

    static MotionSet registerPlayer(AnimationManager.AnimationBuilder builder) {
        return register(builder, Armatures.BIPED, PLAYER_PREFIX, true);
    }

    static MotionSet registerHero(AnimationManager.AnimationBuilder builder) {
        return register(builder, HeroEpicFightBridge.heroNightfallArmature(), HERO_PREFIX, false);
    }

    private static MotionSet register(AnimationManager.AnimationBuilder builder,
                                      AssetAccessor<HumanoidArmature> armature, String prefix, boolean player) {
        Timeline timeline = readTimeline();
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> attacks = new ArrayList<>();
        for (Timing timing : timeline.segments) {
            boolean first = attacks.isEmpty();
            AnimationManager.AnimationAccessor<? extends AttackAnimation> accessor;
            if (player) {
                accessor = builder.<MediaPipeScytheAttackAnimation>nextAccessor(prefix + timing.name,
                        a -> configure(new MediaPipeScytheAttackAnimation(.06F, a, armature,
                                phases(timing, 0F, armature)), true));
            } else {
                accessor = builder.<AttackAnimation>nextAccessor(prefix + timing.name,
                        a -> configure(new AttackAnimation(first ? .1F : 0F, a, armature, phases(timing, 0F, armature)) {
                        @Override
                        public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
                            return 1F;
                        }
                    }, first));
            }
            accessor.get();
            attacks.add(accessor);
        }
        if (player) {
            Timing dash = timeline.specials.get(0);
            var dashAccessor = builder.<MediaPipeScytheDashAnimation>nextAccessor(prefix + "dash",
                    a -> configure(new MediaPipeScytheDashAnimation(.06F, dash.duration, a, armature,
                            phases(dash, 0F, armature)), true));
            dashAccessor.get();
            Timing air = timeline.specials.get(1);
            var airAccessor = builder.<AirSlashAnimation>nextAccessor(prefix + "air",
                    a -> configure(new AirSlashAnimation(.06F, a, armature, phases(air, 0F, armature)) {
                        @Override
                        public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
                            return 1F;
                        }
                    }, true));
            airAccessor.get();
            // Epic Fight reserves the last two combo slots for sprint and air.
            attacks.add(dashAccessor);
            attacks.add(airAccessor);
        }
        var ready = builder.<StaticAnimation>nextAccessor(prefix + "ready",
                a -> new StaticAnimation(.2F, true, a, armature));
        var hold = builder.<StaticAnimation>nextAccessor(prefix + "hold",
                a -> new StaticAnimation(.2F, true, a, armature));
        ready.get();
        hold.get();
        builder.<AttackAnimation>nextAccessor(prefix + "full", a -> {
            List<AttackAnimation.Phase> phases = new ArrayList<>();
            for (Timing t : timeline.segments) {
                phases.addAll(List.of(phases(t, t.start, armature)));
            }
            return configure(new AttackAnimation(.1F, a, armature, phases.toArray(AttackAnimation.Phase[]::new)) {
                @Override
                public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation current) {
                    return 1F;
                }
            }, true);
        }).get();
        return new MotionSet(List.copyOf(attacks), ready, hold);
    }

    private static AttackAnimation.Phase[] phases(Timing timing, float offset,
                                                   AssetAccessor<HumanoidArmature> armature) {
        var rig = armature.get();
        var phases = new AttackAnimation.Phase[timing.contacts.size()];
        float start = 0F;
        for (int i = 0; i < phases.length; i++) {
            Contact hit = timing.contacts.get(i);
            boolean last = i == phases.length - 1;
            var colliders = timing.aerial
                    ? new AttackAnimation.JointColliderPair[]{AttackAnimation.JointColliderPair.of(rig.toolR, BLADE)}
                    : new AttackAnimation.JointColliderPair[]{AttackAnimation.JointColliderPair.of(rig.toolR, BLADE),
                            AttackAnimation.JointColliderPair.of(rig.rootJoint, FRONT_SWEEP)};
            phases[i] = new AttackAnimation.Phase(offset + start, offset + hit.start, offset + hit.start,
                    offset + hit.end, offset + (last ? timing.recovery : hit.end),
                    offset + (last ? timing.duration : hit.end), InteractionHand.MAIN_HAND,
                    colliders);
            start = hit.end;
        }
        return phases;
    }

    private static <T extends AttackAnimation> T configure(T animation, boolean allowAim) {
        animation.addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1F);
        animation.addProperty(AnimationProperty.AttackAnimationProperty.ATTACK_SPEED_FACTOR, 0F);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.RAW_COORD);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_TICK, MoveCoordFunctions.RAW_COORD);
        // Root carries authored footstep travel; do not scale it to target distance.
        animation.addProperty(AnimationProperty.AttackAnimationProperty.FIXED_MOVE_DISTANCE, true);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.MOVE_VERTICAL,
                animation instanceof MediaPipeScytheDashAnimation);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.MOVE_ON_LINK, false);
        animation.addProperty(AnimationProperty.StaticAnimationProperty.FIXED_HEAD_ROTATION, true);
        animation.addProperty(AnimationProperty.StaticAnimationProperty.POSE_MODIFIER,
                (self, pose, patch, time, partialTicks) -> { });
        if (!allowAim) {
            animation.addProperty(AnimationProperty.ActionAnimationProperty.ENTITY_YROT_PROVIDER,
                    (self, patch) -> patch.getYRot());
        }
        List<TrailInfo> trails = new ArrayList<>();
        for (AttackAnimation.Phase phase : animation.phases) {
            trails.add(TrailInfo.builder().joint("Tool_R").time(phase.preDelay, phase.contact)
                    .startPos(new Vec3(-.026746D, -1.835568D, -2.071904D))
                    .endPos(new Vec3(-.026746D, .888342D, -2.071904D))
                    .itemSkinHand(InteractionHand.MAIN_HAND).create());
        }
        animation.addProperty(ClientAnimationProperties.TRAIL_EFFECT, List.copyOf(trails));
        return animation;
    }

    private static Timeline readTimeline() {
        try (var input = Objects.requireNonNull(MediaPipeScytheAnimations.class.getResourceAsStream(
                "/assets/herobrine_companion/epicfight/poem_mediapipe_timing.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Timeline t = new Gson().fromJson(reader, Timeline.class);
            if (t.segments.size() != COMBO_COUNT || t.specials.size() != 2
                    || !t.specials.get(0).name.equals("dash") || !t.specials.get(1).name.equals("air")) {
                throw new IllegalStateException("Invalid MediaPipe scythe combo layout");
            }
            float end = 0F;
            for (int i = 0; i < t.segments.size(); i++) {
                Timing s = t.segments.get(i);
                if (!s.name.equals("combo_%02d".formatted(i + 1)) || Math.abs(s.start - end) > .0001F) {
                    throw new IllegalStateException("Discontinuous MediaPipe scythe combo: " + s.name);
                }
                end = s.start + s.duration;
            }
            for (Timing s : java.util.stream.Stream.concat(t.segments.stream(), t.specials.stream()).toList()) {
                if (s.contacts.isEmpty() || !(s.recovery < s.duration)) {
                    throw new IllegalStateException("Missing scythe contact window: " + s.name);
                }
                float previous = 0F;
                for (Contact h : s.contacts) {
                    if (!(previous <= h.start && h.start < h.end && h.end < s.recovery)) {
                        throw new IllegalStateException("Invalid scythe contact window: " + s.name);
                    }
                    previous = h.end;
                }
            }
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load MediaPipe scythe timing", e);
        }
    }

    record MotionSet(List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combo,
                     AnimationManager.AnimationAccessor<StaticAnimation> ready,
                     AnimationManager.AnimationAccessor<StaticAnimation> hold) { }
    private record Timeline(List<Timing> segments, List<Timing> specials) { }
    private record Timing(String name, float start, float duration, float recovery, List<Contact> contacts, boolean aerial) { }
    private record Contact(float start, float end) { }
}
