package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.model.armature.HumanoidArmature;

/** Extra attacks are separate from the four ground combo/dash/air slot arrays. */
final class UnityScytheExtraAnimations {
    private static List<MoveSet> playerMoves = List.of();
    private static List<MoveSet> heroMoves = List.of();

    private UnityScytheExtraAnimations() { }

    static void register(AnimationManager.AnimationBuilder builder, AssetAccessor<HumanoidArmature> armature,
                         String actorPrefix) {
        List<MoveSet> moves = new ArrayList<>();
        for (Extra move : readTimeline().moves()) {
            String prefix = actorPrefix + "extra/";
            var ground = builder.<UnityScytheAttackAnimation>nextAccessor(prefix + move.ground().name(),
                    a -> UnityScytheAnimations.configure(new UnityScytheAttackAnimation(.07F, move.ground().duration(),
                            a, armature, phases(move, false, armature)), true));
            var air = builder.<UnityScytheAttackAnimation.Air>nextAccessor(prefix + move.air().name(),
                    a -> UnityScytheAnimations.configure(move.gesture() == 2
                            ? new UnityScythePlungeAnimation(.06F, move.air().duration(), a, armature, phases(move, true, armature))
                            : new UnityScytheAttackAnimation.Air(.06F, move.air().duration(), a, armature, phases(move, true, armature)), true));
            ground.get(); air.get();
            moves.add(new MoveSet(move, ground, air));
        }
        if (actorPrefix.equals(UnityScytheAnimations.PLAYER_PREFIX)) playerMoves = List.copyOf(moves);
        else heroMoves = List.copyOf(moves);
    }

    static List<MoveSet> moves(boolean hero) { return hero ? heroMoves : playerMoves; }

    static AttackAnimation.Phase[] phases(Extra move, boolean air, AssetAccessor<HumanoidArmature> armature) {
        var phases = UnityScytheAnimations.phases(air ? move.air() : move.ground(), 0F, armature);
        for (var phase : phases) {
            phase.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(move.damage()));
            phase.addProperty(AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(move.impact()));
        }
        return phases;
    }

    static Timeline readTimeline() {
        try (var input = Objects.requireNonNull(UnityScytheExtraAnimations.class.getResourceAsStream(
                "/assets/herobrine_companion/epicfight/poem_unity09_extras.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Timeline timeline = new Gson().fromJson(reader, Timeline.class);
            if (timeline.moves().size() != 3) throw new IllegalStateException("Missing scythe gestures");
            var names = new HashSet<String>();
            for (int i = 0; i < 3; i++) {
                Extra extra = timeline.moves().get(i);
                String key = List.of("flurry", "uppercut", "heavy").get(i);
                if (extra.gesture() != i || !extra.key().equals(key) || !(extra.damage() > 0F && extra.impact() > 0F)) {
                    throw new IllegalStateException("Invalid scythe gesture: " + i);
                }
                for (var timing : List.of(extra.ground(), extra.air())) {
                    if (!names.add(timing.name()) || !timing.name().startsWith(key + "_")
                            || timing.start() != 0F || timing.contacts().isEmpty()
                            || !(timing.recovery() < timing.duration())) throw new IllegalStateException("Invalid extra motion");
                    float previous = 0F;
                    for (var hit : timing.contacts()) {
                        if (!(previous <= hit.start() && hit.start() < hit.end() && hit.end() <= timing.recovery())) {
                            throw new IllegalStateException("Invalid extra blade phase");
                        }
                        previous = hit.end();
                    }
                }
            }
            return timeline;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the native scythe gestures", exception);
        }
    }

    record Timeline(List<Extra> moves) { }
    record Extra(int gesture, String key, float damage, float impact,
                 UnityScytheAnimations.Timing ground, UnityScytheAnimations.Timing air) { }
    record MoveSet(Extra extra,
                   AnimationManager.AnimationAccessor<? extends AttackAnimation> ground,
                   AnimationManager.AnimationAccessor<? extends AttackAnimation> air) {
        UnityScytheAnimations.Timing timing(boolean airborne) { return airborne ? extra.air() : extra.ground(); }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> attack(boolean airborne) { return airborne ? air : ground; }
    }
}
