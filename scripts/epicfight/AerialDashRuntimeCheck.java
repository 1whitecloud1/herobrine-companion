package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
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

/** Tests the production jump-coordinate and root-pose split with Epic Fight. */
public final class AerialDashRuntimeCheck {
    private static final class ArmatureFixture extends LivingEntityPatch<LivingEntity> {
        private ArmatureFixture() { super(); }
        void use(Armature value) { this.armature = value; }
        @Override public void updateMotion(boolean considerInaction) { }
        @Override public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stun) { return null; }
        @Override public Faction getFaction() { return null; }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources("aerial-dash-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally {
            AnimationManager.setServerResourceManager(null);
        }
    }

    private static void verify() throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion");
        HumanoidArmature armature;
        try (var input = Files.newInputStream(Path.of("build/epicfight-mediapipe/biped.json"))) {
            armature = new JsonAssetLoader(input, Armatures.BIPED.registryName()).loadArmature(HumanoidArmature::new);
        }
        var armaturesField = Armatures.class.getDeclaredField("ARMATURES");
        armaturesField.setAccessible(true);
        ((Map<AssetAccessor<?>, Armature>) armaturesField.get(null)).put(Armatures.BIPED, armature);
        var readTimeline = MediaPipeScytheAnimations.class.getDeclaredMethod("readTimeline");
        readTimeline.setAccessible(true);
        Object timeline = readTimeline.invoke(null);
        var specialsMethod = timeline.getClass().getDeclaredMethod("specials");
        specialsMethod.setAccessible(true);
        Object dash = ((List<?>) specialsMethod.invoke(timeline)).get(0);
        var durationMethod = dash.getClass().getDeclaredMethod("duration");
        durationMethod.setAccessible(true);
        float duration = (Float) durationMethod.invoke(dash);
        var phasesMethod = MediaPipeScytheAnimations.class.getDeclaredMethod("phases", dash.getClass(), float.class, AssetAccessor.class);
        phasesMethod.setAccessible(true);
        var phases = (AttackAnimation.Phase[]) phasesMethod.invoke(null, dash, 0F, Armatures.BIPED);
        if (phases.length != 1 || phases[0].getColliders().length != 1
                || phases[0].getColliders()[0].getFirst() != armature.toolR) {
            throw new AssertionError("The airborne attack must hit through the visible blade");
        }
        var builder = new AnimationManager.AnimationBuilder("herobrine_companion", b -> { });
        var accessor = builder.<MediaPipeScytheDashAnimation>nextAccessor("player/poem_mediapipe/dash", a -> null);
        var motion = new MediaPipeScytheDashAnimation(.06F, duration, accessor, Armatures.BIPED, phases);
        try (var input = Files.newInputStream(assets.resolve("animmodels/animations/player/poem_mediapipe/dash.json"))) {
            var clip = new JsonAssetLoader(input, accessor.registryName()).loadAnimationClip(armature);
            var clipField = DynamicAnimation.class.getDeclaredField("animationClip");
            clipField.setAccessible(true);
            clipField.set(motion, clip);
        }
        // correctRootJoint only reads the armature. Allocate a fixture without
        // starting Minecraft's entity/synced-data bootstrap, and call EF itself.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var patch = (ArmatureFixture) unsafe.allocateInstance(ArmatureFixture.class);
        patch.use(armature);
        var coords = new TransformSheet();
        MoveCoordFunctions.RAW_COORD.set(motion, null, coords);
        if (!motion.getProperty(ActionAnimationProperty.MOVE_VERTICAL).orElse(false)
                || !motion.getProperty(ActionAnimationProperty.REMOVE_DELTA_MOVEMENT).orElse(false)
                || motion.getProperty(ActionAnimationProperty.CANCELABLE_MOVE).orElse(true)
                || !motion.getProperty(ActionAnimationProperty.NO_GRAVITY_TIME).orElseThrow().isTimeInPairs(duration)
                || motion.getJointMaskEntry(null, false).isPresent() || !motion.shouldPlayerMove(null)
                || motion.getPlaySpeed(null, null) != 1F) {
            throw new AssertionError("Aerial dash lost its full-body/vertical movement policy");
        }
        double maxRise = 0D, maxReconstructionError = 0D;
        var origin = coords.getInterpolatedTranslation(0F);
        for (int sample = 0; sample <= Math.round(duration * 960); sample++) {
            float time = Math.min(duration, sample / 960F);
            var pose = motion.getAnimationClip().getPoseInTime(time);
            var before = armature.getBoundTransformFor(pose, armature.rootJoint).toTranslationVector();
            var position = coords.getInterpolatedTranslation(time);
            motion.correctRootJoint(motion, pose, patch, time, 1F);
            var after = armature.getBoundTransformFor(pose, armature.rootJoint).toTranslationVector();
            maxReconstructionError = Math.max(maxReconstructionError, Math.abs(before.x - after.x - position.x));
            maxReconstructionError = Math.max(maxReconstructionError, Math.abs(before.y - after.y - position.y));
            maxReconstructionError = Math.max(maxReconstructionError, Math.abs(before.z - after.z - position.z));
            if (position.y < -1e-6F) throw new AssertionError("Crouch moves the entity into the floor");
            maxRise = Math.max(maxRise, position.y);
        }
        double maxHorizontalTick = 0D, maxVerticalTick = 0D;
        var previous = origin;
        for (int tick = 1; tick <= Math.ceil(duration * 20); tick++) {
            var current = coords.getInterpolatedTranslation(Math.min(duration, tick / 20F));
            maxHorizontalTick = Math.max(maxHorizontalTick, Math.hypot(current.x - previous.x, current.z - previous.z));
            maxVerticalTick = Math.max(maxVerticalTick, Math.abs(current.y - previous.y));
            previous = current;
        }
        double forward = origin.z - previous.z;
        if (Math.abs(forward - 3.7D) > .00002D || Math.abs(previous.y) > .00001F || maxRise < 1D
                || maxReconstructionError > .006D || maxHorizontalTick > .3D || maxVerticalTick > .45D) {
            throw new AssertionError("Aerial runtime mismatch: forward=" + forward + ", rise=" + maxRise
                    + ", root reconstruction=" + maxReconstructionError + ", vertical tick=" + maxVerticalTick);
        }
        // Coordinate extraction must leave the authored crouch/landing pose intact.
        if (motion.getAnimationClip().getJointTransforms().get("Root").getInterpolatedTranslation(.1F).y >= 0F) {
            throw new AssertionError("Jump coordinate conversion mutated the source Root track");
        }
        var result = Map.of("forward_blocks", forward, "entity_rise_blocks", maxRise,
                "landing_height_blocks", previous.y, "maximum_root_reconstruction_error_blocks", maxReconstructionError,
                "maximum_horizontal_20hz_step_blocks", maxHorizontalTick, "maximum_vertical_20hz_step_blocks", maxVerticalTick,
                "full_body_and_gravity_policy_checked", true, "production_blade_hit_phase_checked", true,
                "live_gameplay_tested", false);
        Files.writeString(Path.of("build/epicfight-mediapipe/aerial_dash_runtime_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        System.out.println("AERIAL_DASH_RUNTIME_OK: actual EF Root correction + RAW_COORD, " + forward
                + " blocks forward, " + maxRise + " blocks rise, landed at zero");
    }
}
