package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/** Real Epic Fight loading, pose sampling, blade contacts and root extraction. */
public final class UnityExtrasRuntimeCheck {
    private static final class Fixture extends LivingEntityPatch<LivingEntity> {
        private Fixture() { super(); }
        void use(Armature rig) { this.armature = rig; }
        @Override public void updateMotion(boolean action) { }
        @Override public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stun) { return null; }
        @Override public Faction getFaction() { return null; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, List.of(new net.minecraft.server.packs.PathPackResources(
                        "unity-extras-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally { AnimationManager.setServerResourceManager(null); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify() throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion");
        var field = Armatures.class.getDeclaredField("ARMATURES"); field.setAccessible(true);
        Map<AssetAccessor<?>, Armature> armatures = (Map) field.get(null);
        var heroAccessor = HeroEpicFightBridge.heroNightfallArmature();
        for (var entry : Map.of(Armatures.BIPED, Path.of("build/epicfight-mediapipe/biped.json"),
                heroAccessor, assets.resolve("animmodels/entity/hero_biped_nightfall.json")).entrySet()) {
            try (var input = Files.newInputStream(entry.getValue())) {
                armatures.put(entry.getKey(), new JsonAssetLoader(input, entry.getKey().registryName()).loadArmature(HumanoidArmature::new));
            }
        }
        var builder = new AnimationManager.AnimationBuilder("herobrine_companion", b -> { });
        UnityScytheExtraAnimations.register(builder, Armatures.BIPED, UnityScytheAnimations.PLAYER_PREFIX);
        UnityScytheExtraAnimations.register(builder, heroAccessor, UnityScytheAnimations.HERO_PREFIX);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var clipField = DynamicAnimation.class.getDeclaredField("animationClip"); clipField.setAccessible(true);
        var reports = new ArrayList<Map<String, Object>>();
        double maximumError = 0;
        for (boolean hero : new boolean[]{false, true}) {
            var armature = hero ? heroAccessor : Armatures.BIPED;
            var rig = (HumanoidArmature) armatures.get(armature);
            var patch = (Fixture) unsafe.allocateInstance(Fixture.class); patch.use(rig);
            var moves = UnityScytheExtraAnimations.moves(hero);
            if (moves.size() != 3) throw new AssertionError("Missing gesture variants");
            for (var move : moves) for (boolean air : new boolean[]{false, true}) {
                var timing = move.timing(air);
                var accessor = move.attack(air);
                var phases = UnityScytheExtraAnimations.phases(move.extra(), air, armature);
                for (var phase : phases) {
                    if (phase.getColliders().length != 1 || phase.getColliders()[0].getFirst() != rig.toolR
                            || phase.getProperty(AttackPhaseProperty.DAMAGE_MODIFIER).isEmpty()
                            || phase.getProperty(AttackPhaseProperty.IMPACT_MODIFIER).isEmpty()) {
                        throw new AssertionError("Extra attack lacks blade-bound damage: " + timing.name());
                    }
                }
                AttackAnimation motion = air ? move.extra().gesture() == 2
                        ? new UnityScythePlungeAnimation(.06F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armature, phases)
                        : new UnityScytheAttackAnimation.Air(.06F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armature, phases)
                        : new UnityScytheAttackAnimation(.07F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armature, phases);
                Path path = assets.resolve("animmodels/animations/" + accessor.registryName().getPath() + ".json");
                try (var input = Files.newInputStream(path)) {
                    clipField.set(motion, new JsonAssetLoader(input, accessor.registryName()).loadAnimationClip(rig));
                }
                var clip = motion.getAnimationClip();
                UnityModesRuntimeCheck.verifyChainWindow(motion, patch, timing, move.extra().gesture() == 2 ? .1201F : Float.MAX_VALUE);
                if (Math.abs(clip.getClipTime() - timing.duration()) > .00001F || clip.getJointTransforms().size() != 20
                        || motion.getJointMaskEntry(null, false).isPresent() || !motion.shouldPlayerMove(null)
                        || motion.getPlaySpeed(null, null) != 1F
                        || !motion.getProperty(ActionAnimationProperty.MOVE_VERTICAL).orElse(false)
                        || !motion.getProperty(ActionAnimationProperty.NO_GRAVITY_TIME).orElseThrow().isTimeInPairs(timing.duration())) {
                    throw new AssertionError("Extra root/gravity/full-body policy lost: " + timing.name());
                }
                var coords = new TransformSheet(); MoveCoordFunctions.RAW_COORD.set(motion, null, coords);
                double reconstruction = 0, rise = 0;
                for (int sample = 0; sample <= Math.ceil(timing.duration() * 480); sample++) {
                    float time = Math.min(timing.duration(), sample / 480F);
                    var pose = clip.getPoseInTime(time);
                    for (var transform : pose.getJointTransformData().values()) {
                        var p = transform.translation(); var q = transform.rotation(); var s = transform.scale();
                        if (!Float.isFinite(p.x+p.y+p.z+q.x+q.y+q.z+q.w+s.x+s.y+s.z) || Math.abs(q.lengthSquared()-1F)>.002F
                                || s.x<=0 || s.y<=0 || s.z<=0) throw new AssertionError("Invalid extra pose");
                    }
                    var before = rig.getBoundTransformFor(pose, rig.rootJoint).toTranslationVector();
                    var position = coords.getInterpolatedTranslation(time);
                    motion.correctRootJoint(motion, pose, patch, time, 1F);
                    var after = rig.getBoundTransformFor(pose, rig.rootJoint).toTranslationVector();
                    reconstruction = Math.max(reconstruction, Math.max(Math.abs(before.x-after.x-position.x),
                            Math.max(Math.abs(before.y-after.y-position.y), Math.abs(before.z-after.z-position.z))));
                    rise = Math.max(rise, position.y);
                }
                System.out.println("EXTRA_ROOT_CHECK " + timing.name() + " error=" + reconstruction);
                if (reconstruction > .006) throw new AssertionError("Extra root reconstruction failed: " + timing.name() + " error=" + reconstruction);
                double error = UnityModesRuntimeCheck.compareMatrices(path, rig, motion);
                maximumError = Math.max(maximumError, error);
                if (error > .003) throw new AssertionError("Extra preview differs from Epic Fight: " + error);
                if (move.extra().gesture() == 2 && (phases.length != 1 || move.extra().damage() < 1.5F)) {
                    throw new AssertionError("Heavy strike does not have an independent damage phase");
                }
                var first = coords.getInterpolatedTranslation(0F); var last = coords.getInterpolatedTranslation(timing.duration());
                var row = new LinkedHashMap<String, Object>(Map.of("clip", accessor.registryName().toString(), "duration", timing.duration(), "hit_phases", phases.length,
                        "native_entity_lateral_blocks", last.x-first.x, "native_entity_forward_blocks", first.z-last.z,
                        "native_entity_rise_blocks", rise, "max_preview_engine_matrix_error", error,
                        "max_root_reconstruction_error", reconstruction, "terrain_following_plunge", motion instanceof UnityScythePlungeAnimation));
                row.put("chain_window_seconds", timing.recovery());
                row.put("last_contact_end", timing.contacts().get(timing.contacts().size() - 1).end());
                reports.add(row);
            }
        }
        if (reports.size() != 12) throw new AssertionError("Wrong extra clip count");
        Path report = Path.of("build/scythe_unity_pack_09/unity_extras_runtime_validation.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "attack_clips_checked", reports.size(), "engine_samples_hz", 480,
                "maximum_preview_engine_matrix_error", maximumError, "clips", reports, "live_gameplay_tested", false)) + "\n");
        System.out.println("UNITY_EXTRAS_RUNTIME_OK: 12 clips; three gestures; heavy blade phases; full-body root/gravity; EF matrices match");
    }
}
