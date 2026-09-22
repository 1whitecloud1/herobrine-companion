package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.damagesource.StunType;

/** Uses the installed EF loader, skeleton, interpolation and root-motion split. */
public final class UnityModesRuntimeCheck {
    private static final class Fixture extends LivingEntityPatch<LivingEntity> {
        private Fixture() { super(); }
        void use(Armature value) { this.armature = value; }
        @Override public void updateMotion(boolean considerInaction) { }
        @Override public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stun) { return null; }
        @Override public Faction getFaction() { return null; }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return (T) f.get(null);
    }

    public static void main(String[] args) throws Exception {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, List.of(new net.minecraft.server.packs.PathPackResources(
                        "unity-modes-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally { AnimationManager.setServerResourceManager(null); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify() throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion");
        Path output = Path.of("build/scythe_unity_pack_09"); Files.createDirectories(output);
        PoemScythePlayerAnimations.registerStyles(); Style.ENUM_MANAGER.loadEnum();
        var builder = new AnimationManager.AnimationBuilder("herobrine_companion", b -> { });
        PoemScythePlayerAnimations.registerAnimations(builder);
        HeroScytheComboBehaviors.registerAnimation(builder);
        List<UnityScytheAnimations.MotionSet> players = field(PoemScythePlayerAnimations.class, "unityModes");
        if (players.size() != 4) throw new AssertionError("Four player modes were not registered");
        var styles = new HashSet<Style>();
        for (int mode : new int[]{0, 3, 1, 2, 0, 2, 1, 3}) {
            Style style = PoemScythePlayerAnimations.styleForMode(mode);
            styles.add(style);
            if (style.canUseOffhand() || Style.ENUM_MANAGER.getOrThrow(style.universalOrdinal()) != style
                    || HeroScytheComboBehaviors.motionSetForMode(mode).mode() != mode) {
                throw new AssertionError("Mode switch failed: " + mode);
            }
        }
        if (styles.size() != 4) throw new AssertionError("Modes share an active style");
        Map<AssetAccessor<?>, Armature> armatures = field(Armatures.class, "ARMATURES");
        var heroAccessor = HeroEpicFightBridge.heroNightfallArmature();
        for (var entry : Map.of(Armatures.BIPED, Path.of("build/epicfight-mediapipe/biped.json"),
                heroAccessor, assets.resolve("animmodels/entity/hero_biped_nightfall.json")).entrySet()) {
            try (var input = Files.newInputStream(entry.getValue())) {
                armatures.put(entry.getKey(), new JsonAssetLoader(input, entry.getKey().registryName()).loadArmature(HumanoidArmature::new));
            }
        }
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var clipField = DynamicAnimation.class.getDeclaredField("animationClip"); clipField.setAccessible(true);
        var timeline = UnityScytheAnimations.readTimeline();
        var reports = new ArrayList<Map<String, Object>>();
        int staticClips = 0;
        double largestMatrixError = 0D;
        for (boolean hero : new boolean[]{false, true}) {
            var armatureAccessor = hero ? heroAccessor : Armatures.BIPED;
            var rig = (HumanoidArmature) armatures.get(armatureAccessor);
            var patch = (Fixture) unsafe.allocateInstance(Fixture.class); patch.use(rig);
            String prefix = hero ? UnityScytheAnimations.HERO_PREFIX : UnityScytheAnimations.PLAYER_PREFIX;
            for (var mode : timeline.modes()) {
                var set = hero ? HeroScytheComboBehaviors.motionSetForMode(mode.mode()) : players.get(mode.mode());
                if (set.attacks().size() != 6 || set.combo().size() != 4
                        || !set.dash().registryName().getPath().endsWith("/dash")
                        || !set.air().registryName().getPath().endsWith("/air")) throw new AssertionError("Wrong special slots");
                String folder = prefix + mode.key() + "/";
                if (AnimationManager.getInstance().getAnimations(a -> a.registryName().getPath().startsWith(folder)).size() != 9) {
                    throw new AssertionError("Missing mode registration: " + folder);
                }
                List<UnityScytheAnimations.Timing> timings = new ArrayList<>(mode.segments()); timings.addAll(mode.specials());
                for (int i = 0; i < timings.size(); i++) {
                    var timing = timings.get(i);
                    var accessor = set.attacks().get(i);
                    if (!accessor.registryName().getPath().equals(folder + timing.name())) throw new AssertionError("Wrong motion source");
                    var phases = UnityScytheAnimations.phases(timing, 0F, armatureAccessor);
                    for (var phase : phases) {
                        if (phase.getColliders().length != 1 || phase.getColliders()[0].getFirst() != rig.toolR
                                || !(phase.preDelay < phase.contact && phase.contact <= phase.recovery && phase.recovery <= phase.end)) {
                            throw new AssertionError("Wrong blade contact phase: " + folder + timing.name());
                        }
                    }
                    AttackAnimation motion = i < 4
                            ? new UnityScytheAttackAnimation(.06F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armatureAccessor, phases)
                            : i == 4 ? new UnityScytheAttackAnimation.Dash(.06F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armatureAccessor, phases)
                            : new UnityScytheAttackAnimation.Air(.06F, timing.duration(), (AnimationManager.AnimationAccessor) accessor, armatureAccessor, phases);
                    Path file = assets.resolve("animmodels/animations/" + folder + timing.name() + ".json");
                    try (var input = Files.newInputStream(file)) {
                        clipField.set(motion, new JsonAssetLoader(input, accessor.registryName()).loadAnimationClip(rig));
                    }
                    var clip = motion.getAnimationClip();
                    if (i < 4 || i == 5) verifyChainWindow(motion, patch, timing, i == 3 ? .1001F : Float.MAX_VALUE);
                    if (Math.abs(clip.getClipTime() - timing.duration()) > .00001F || clip.getJointTransforms().size() != 20
                            || motion.getJointMaskEntry(null, false).isPresent() || !motion.shouldPlayerMove(null)
                            || motion.getPlaySpeed(null, null) != 1F || !motion.getProperty(ActionAnimationProperty.MOVE_VERTICAL).orElse(false)
                            || !motion.getProperty(ActionAnimationProperty.STOP_MOVEMENT).orElse(false)
                            || !motion.getProperty(ActionAnimationProperty.REMOVE_DELTA_MOVEMENT).orElse(false)
                            || motion.getProperty(ActionAnimationProperty.CANCELABLE_MOVE).orElse(true)
                            || !motion.getProperty(ActionAnimationProperty.NO_GRAVITY_TIME).orElseThrow().isTimeInPairs(timing.duration())) {
                        throw new AssertionError("Native movement policy lost: " + folder + timing.name());
                    }
                    var coords = new TransformSheet(); MoveCoordFunctions.RAW_COORD.set(motion, null, coords);
                    double reconstruction = 0, rise = 0;
                    for (int sample = 0; sample <= Math.ceil(timing.duration() * 480); sample++) {
                        float time = Math.min(timing.duration(), sample / 480F);
                        var pose = clip.getPoseInTime(time);
                        for (var transform : pose.getJointTransformData().values()) {
                            var p = transform.translation(); var q = transform.rotation(); var s = transform.scale();
                            if (!Float.isFinite(p.x+p.y+p.z+q.x+q.y+q.z+q.w+s.x+s.y+s.z)
                                    || Math.abs(q.lengthSquared()-1F)>.002F || s.x<=0 || s.y<=0 || s.z<=0) {
                                throw new AssertionError("Invalid engine interpolation: " + file + " @ " + time);
                            }
                        }
                        var before = rig.getBoundTransformFor(pose, rig.rootJoint).toTranslationVector();
                        var position = coords.getInterpolatedTranslation(time);
                        motion.correctRootJoint(motion, pose, patch, time, 1F);
                        var after = rig.getBoundTransformFor(pose, rig.rootJoint).toTranslationVector();
                        reconstruction = Math.max(reconstruction, Math.max(Math.abs(before.x-after.x-position.x),
                                Math.max(Math.abs(before.y-after.y-position.y), Math.abs(before.z-after.z-position.z))));
                        if (position.y < -.000001F) throw new AssertionError("Crouch entered entity travel");
                        rise = Math.max(rise, position.y);
                    }
                    if (reconstruction > .006) throw new AssertionError("Root reconstruction failed: " + reconstruction);
                    double matrixError = compareMatrices(file, rig, motion);
                    largestMatrixError = Math.max(largestMatrixError, matrixError);
                    if (matrixError > .003) throw new AssertionError("EF skeleton differs from the preview: " + file + " error=" + matrixError);
                    var start = coords.getInterpolatedTranslation(0F); var end = coords.getInterpolatedTranslation(timing.duration());
                    reports.add(Map.of("clip", folder+timing.name(), "duration", timing.duration(), "hit_phases", phases.length,
                            "entity_lateral_blocks", end.x-start.x, "entity_forward_blocks", start.z-end.z,
                            "entity_rise_blocks", rise, "max_root_reconstruction_error", reconstruction,
                            "max_preview_engine_matrix_error", matrixError,
                            "chain_window_seconds", timing.recovery(),
                            "last_contact_end", timing.contacts().get(timing.contacts().size() - 1).end()));
                }
                for (String name : List.of("full", "ready", "hold")) {
                    Path file = assets.resolve("animmodels/animations/" + folder + name + ".json");
                    try (var input = Files.newInputStream(file)) {
                        var clip = new JsonAssetLoader(input, set.ready().registryName()).loadAnimationClip(rig);
                        if (clip.getJointTransforms().size() != (name.equals("hold") ? 13 : 20)) throw new AssertionError("Bad stance/viewer clip");
                        staticClips++;
                    }
                }
            }
        }
        for (String name : List.of("full", "dash", "air")) {
            var hashes = new HashSet<String>();
            for (String mode : UnityScytheAnimations.MODE_KEYS) {
                hashes.add(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(
                        assets.resolve("animmodels/animations/player/poem_unity09/" + mode + "/" + name + ".json")))));
            }
            if (hashes.size() != 4) throw new AssertionError("Modes reuse the same " + name);
        }
        if (reports.size()!=48 || staticClips!=24) throw new AssertionError("Wrong four-mode clip count");
        var report = new LinkedHashMap<String,Object>();
        report.put("attack_clips_checked", reports.size()); report.put("stance_and_viewer_clips_checked", staticClips);
        report.put("four_distinct_mode_styles", true); report.put("four_distinct_combo_dash_air_sets", true);
        report.put("full_body_and_gravity_policy_checked", true); report.put("engine_samples_hz", 480);
        report.put("maximum_preview_engine_matrix_error", largestMatrixError); report.put("clips", reports);
        report.put("live_gameplay_tested", false);
        Files.writeString(output.resolve("unity_modes_runtime_validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("UNITY_MODES_RUNTIME_OK: 72 clips; four styles; 48 full-body attacks; actual EF skeleton and root travel match preview");
    }

    static void verifyChainWindow(AttackAnimation motion, LivingEntityPatch<?> patch,
                                  UnityScytheAnimations.Timing timing, float maximumTail) {
        // Query the real EF state spectrum, so JSON edits alone cannot pass.
        motion.postInit();
        for (var hit : timing.contacts()) {
            var state = new EntityState(motion.getStatesMap(patch, (hit.start() + hit.end()) * .5F));
            if (state.canBasicAttack() || !state.attacking()) throw new AssertionError("A contact can be cancelled: " + timing.name());
        }
        if (new EntityState(motion.getStatesMap(patch, timing.recovery() - .001F)).canBasicAttack()) {
            throw new AssertionError("Attack unlocks before follow-through: " + timing.name());
        }
        if (!new EntityState(motion.getStatesMap(patch, timing.recovery() + .001F)).canBasicAttack()) {
            throw new AssertionError("EF still locks the next attack after recovery: " + timing.name());
        }
        float lastContact = timing.contacts().get(timing.contacts().size() - 1).end();
        if (timing.recovery() - lastContact > maximumTail) throw new AssertionError("Long recovery returned: " + timing.name());
    }

    static double compareMatrices(Path file, HumanoidArmature rig, AttackAnimation motion) throws Exception {
        JsonArray rows = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("animation");
        var source = new LinkedHashMap<String,JsonObject>();
        for (var row : rows) source.put(row.getAsJsonObject().get("name").getAsString(), row.getAsJsonObject());
        var parents = new LinkedHashMap<String,String>();
        var model = JsonParser.parseString(Files.readString(Path.of("build/epicfight-mediapipe/biped.json"))).getAsJsonObject();
        parents(model.getAsJsonObject("armature").getAsJsonArray("hierarchy"), null, parents);
        int count=source.get("Root").getAsJsonArray("time").size(); double error=0;
        for (int key : new int[]{0, count/4, count/2, count*3/4, count-1}) {
            float time=source.get("Root").getAsJsonArray("time").get(key).getAsFloat();
            var pose=motion.getAnimationClip().getPoseInTime(time);
            var world=new LinkedHashMap<String,double[]>();
            for (var row : parents.entrySet()) {
                JsonArray array=source.get(row.getKey()).getAsJsonArray("transform").get(key).getAsJsonArray();
                double[] local=new double[16]; for (int i=0;i<16;i++) local[i]=array.get(i).getAsDouble();
                world.put(row.getKey(),row.getValue()==null?local:multiply(world.get(row.getValue()),local));
                double[] expected=world.get(row.getKey());
                var actual=rig.getBoundTransformFor(pose,rig.searchJointByName(row.getKey()));
                for (Vec3 probe : List.of(Vec3.ZERO,new Vec3(.1,0,0),new Vec3(0,.1,0),new Vec3(0,0,.1))) {
                    double x=expected[0]*probe.x+expected[1]*probe.y+expected[2]*probe.z+expected[3];
                    double y=expected[4]*probe.x+expected[5]*probe.y+expected[6]*probe.z+expected[7];
                    double z=expected[8]*probe.x+expected[9]*probe.y+expected[10]*probe.z+expected[11];
                    Vec3 point=OpenMatrix4f.transform(actual,probe);
                    error=Math.max(error,point.distanceTo(new Vec3(x,z,-y)));
                }
            }
        }
        return error;
    }

    private static void parents(JsonArray nodes,String parent,Map<String,String> result) {
        for (var entry:nodes) { var node=entry.getAsJsonObject();String name=node.get("name").getAsString();result.put(name,parent);
            if(node.has("children"))parents(node.getAsJsonArray("children"),name,result); }
    }
    private static double[] multiply(double[] a,double[] b) {
        double[] c=new double[16];for(int i=0;i<4;i++)for(int j=0;j<4;j++)for(int k=0;k<4;k++)c[i*4+j]+=a[i*4+k]*b[k*4+j];return c;
    }
}
