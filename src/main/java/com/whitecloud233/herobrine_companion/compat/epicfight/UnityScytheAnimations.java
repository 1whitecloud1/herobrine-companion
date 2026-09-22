package com.whitecloud233.herobrine_companion.compat.epicfight;

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
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;

/** Four independently authored native scythe sets, at their original speed. */
final class UnityScytheAnimations {
    static final int MODE_COUNT = 4;
    static final int COMBO_COUNT = 4;
    static final List<String> MODE_KEYS = List.of("normal", "realm_breaker", "thunder", "void_shatter");
    static final String PLAYER_PREFIX = "player/poem_unity09/";
    static final String HERO_PREFIX = "hero/poem_unity09/";
    // Calibrated to the rendered Poem blade in the animated Tool_R socket.
    private static final Collider BLADE = new MultiOBBCollider(3,
            .190283D, 1.481955D, .747754D, -.026746D, -.473613D, -2.071904D);

    private UnityScytheAnimations() { }

    static List<MotionSet> registerPlayer(AnimationManager.AnimationBuilder builder) {
        return register(builder, Armatures.BIPED, PLAYER_PREFIX);
    }

    static List<MotionSet> registerHero(AnimationManager.AnimationBuilder builder) {
        return register(builder, HeroEpicFightBridge.heroNightfallArmature(), HERO_PREFIX);
    }

    private static List<MotionSet> register(AnimationManager.AnimationBuilder builder,
            AssetAccessor<HumanoidArmature> armature, String actorPrefix) {
        List<MotionSet> sets = new ArrayList<>();
        for (Mode mode : readTimeline().modes()) {
            String prefix = actorPrefix + mode.key() + "/";
            List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> attacks = new ArrayList<>();
            for (Timing timing : mode.segments()) {
                boolean first = attacks.isEmpty();
                var accessor = builder.<UnityScytheAttackAnimation>nextAccessor(prefix + timing.name(),
                        a -> configure(new UnityScytheAttackAnimation(first ? .10F : .035F, timing.duration(), a,
                                armature, phases(timing, 0F, armature)), first));
                accessor.get();
                attacks.add(accessor);
            }
            Timing dash = mode.specials().get(0);
            var dashAccessor = builder.<UnityScytheAttackAnimation.Dash>nextAccessor(prefix + "dash",
                    a -> configure(new UnityScytheAttackAnimation.Dash(.06F, dash.duration(), a,
                            armature, phases(dash, 0F, armature)), true));
            dashAccessor.get();
            Timing air = mode.specials().get(1);
            var airAccessor = builder.<UnityScytheAttackAnimation.Air>nextAccessor(prefix + "air",
                    a -> configure(new UnityScytheAttackAnimation.Air(.06F, air.duration(), a,
                            armature, phases(air, 0F, armature)), true));
            airAccessor.get();
            // Epic Fight interprets the last two ComboAttacks slots as dash/air.
            attacks.add(dashAccessor);
            attacks.add(airAccessor);
            var ready = builder.<StaticAnimation>nextAccessor(prefix + "ready",
                    a -> new StaticAnimation(.2F, true, a, armature));
            var hold = builder.<StaticAnimation>nextAccessor(prefix + "hold",
                    a -> new StaticAnimation(.2F, true, a, armature));
            ready.get();
            hold.get();
            builder.<UnityScytheAttackAnimation>nextAccessor(prefix + "full", a -> {
                List<AttackAnimation.Phase> all = new ArrayList<>();
                for (Timing t : mode.segments()) all.addAll(List.of(phases(t, t.start(), armature)));
                float duration = mode.segments().stream().map(Timing::duration).reduce(0F, Float::sum);
                return configure(new UnityScytheAttackAnimation(.08F, duration, a, armature,
                        all.toArray(AttackAnimation.Phase[]::new)), true);
            }).get();
            sets.add(new MotionSet(mode.mode(), mode.key(), List.copyOf(attacks), ready, hold));
        }
        UnityScytheExtraAnimations.register(builder, armature, actorPrefix);
        return List.copyOf(sets);
    }

    static AttackAnimation.Phase[] phases(Timing timing, float offset, AssetAccessor<HumanoidArmature> armature) {
        var phases = new AttackAnimation.Phase[timing.contacts().size()];
        float start = 0F;
        for (int i = 0; i < phases.length; i++) {
            Contact hit = timing.contacts().get(i);
            boolean last = i == phases.length - 1;
            phases[i] = new AttackAnimation.Phase(offset + start, offset + hit.start(), offset + hit.start(),
                    offset + hit.end(), offset + (last ? timing.recovery() : hit.end()),
                    offset + (last ? timing.duration() : hit.end()), InteractionHand.MAIN_HAND,
                    AttackAnimation.JointColliderPair.of(armature.get().toolR, BLADE));
            start = hit.end();
        }
        return phases;
    }

    static <T extends AttackAnimation> T configure(T animation, boolean allowAim) {
        animation.addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1F);
        animation.addProperty(AnimationProperty.AttackAnimationProperty.ATTACK_SPEED_FACTOR, 0F);
        animation.addProperty(AnimationProperty.AttackAnimationProperty.FIXED_MOVE_DISTANCE, true);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.RAW_COORD);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_TICK, MoveCoordFunctions.RAW_COORD);
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

    static Timeline readTimeline() {
        try (var input = Objects.requireNonNull(UnityScytheAnimations.class.getResourceAsStream(
                "/assets/herobrine_companion/epicfight/poem_unity09_timing.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Timeline timeline = new Gson().fromJson(reader, Timeline.class);
            if (timeline.modes().size() != MODE_COUNT) throw new IllegalStateException("Missing scythe modes");
            for (int i = 0; i < MODE_COUNT; i++) {
                Mode mode = timeline.modes().get(i);
                if (mode.mode() != i || !mode.key().equals(MODE_KEYS.get(i)) || mode.segments().size() != COMBO_COUNT
                        || mode.specials().size() != 2 || !mode.specials().get(0).name().equals("dash")
                        || !mode.specials().get(1).name().equals("air")) {
                    throw new IllegalStateException("Invalid scythe mode layout: " + i);
                }
                float end = 0F;
                for (int j = 0; j < COMBO_COUNT; j++) {
                    Timing timing = mode.segments().get(j);
                    if (!timing.name().equals("combo_%02d".formatted(j + 1)) || Math.abs(timing.start() - end) > .0001F) {
                        throw new IllegalStateException("Discontinuous native combo: " + mode.key());
                    }
                    end = timing.start() + timing.duration();
                }
                for (Timing t : java.util.stream.Stream.concat(mode.segments().stream(), mode.specials().stream()).toList()) {
                    if (t.contacts().isEmpty() || !(t.recovery() < t.duration())) throw new IllegalStateException("Missing scythe recovery");
                    float previous = 0F;
                    for (Contact c : t.contacts()) {
                        if (!(previous <= c.start() && c.start() < c.end() && c.end() <= t.recovery())) {
                            throw new IllegalStateException("Invalid native contact window: " + mode.key() + "/" + t.name());
                        }
                        previous = c.end();
                    }
                }
            }
            return timeline;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load the four native scythe modes", e);
        }
    }

    record MotionSet(int mode, String key, List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> attacks,
                     AnimationManager.AnimationAccessor<StaticAnimation> ready,
                     AnimationManager.AnimationAccessor<StaticAnimation> hold) {
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combo() { return attacks.subList(0, COMBO_COUNT); }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> dash() { return attacks.get(COMBO_COUNT); }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> air() { return attacks.get(COMBO_COUNT + 1); }
    }
    record Timeline(List<Mode> modes) { }
    record Mode(int mode, String key, List<Timing> segments, List<Timing> specials) { }
    record Timing(String name, float start, float duration, float recovery, List<Contact> contacts) { }
    record Contact(float start, float end) { }
}
