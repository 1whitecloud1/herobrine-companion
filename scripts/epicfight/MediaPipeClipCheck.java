import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.model.Armature;

/** Loads both actual armatures and samples the exported clips with Epic Fight itself. */
public class MediaPipeClipCheck {
    private static final class LoadedMotion extends DynamicAnimation {
        LoadedMotion(AnimationClip clip) { super(0F, false); this.animationClip = clip; }
        @Override public <A extends DynamicAnimation> AnimationManager.AnimationAccessor<? extends DynamicAnimation> getAccessor() { return null; }
        @Override public AssetAccessor<? extends StaticAnimation> getRealAnimation() { return null; }
    }

    public static void main(String[] args) throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion");
        var timing = JsonParser.parseString(Files.readString(assets.resolve("epicfight/poem_mediapipe_timing.json"))).getAsJsonObject();
        var reports = new ArrayList<Map<String, Object>>();
        var movement = new ArrayList<Map<String, Object>>();
        int comboCount = timing.getAsJsonArray("segments").size();
        Map<String, Float> expectedTravel = new LinkedHashMap<>();
        Map<String, Float> expectedLateral = new LinkedHashMap<>();
        var turningClips = new java.util.HashSet<String>();
        float totalTravel = 0F;
        float totalLateral = 0F;
        for (var element : timing.getAsJsonArray("segments")) {
            var segment = element.getAsJsonObject();
            float travel = segment.get("travel_blocks").getAsFloat();
            expectedTravel.put(segment.get("name").getAsString(), travel);
            float lateral = segment.has("lateral_blocks") ? segment.get("lateral_blocks").getAsFloat() : 0F;
            expectedLateral.put(segment.get("name").getAsString(), lateral);
            totalLateral += lateral;
            if (segment.has("full_body_turn") && segment.get("full_body_turn").getAsBoolean()) {
                turningClips.add(segment.get("name").getAsString());
            }
            totalTravel += travel;
        }
        expectedTravel.put("full", totalTravel);
        expectedLateral.put("full", totalLateral);
        for (var element : timing.getAsJsonArray("specials")) {
            var special = element.getAsJsonObject();
            if (special.has("full_body_turn") && special.get("full_body_turn").getAsBoolean()) {
                turningClips.add(special.get("name").getAsString());
            }
            expectedTravel.put(special.get("name").getAsString(), special.get("travel_blocks").getAsFloat());
            expectedLateral.put(special.get("name").getAsString(), special.has("lateral_blocks") ? special.get("lateral_blocks").getAsFloat() : 0F);
        }
        for (boolean hero : new boolean[]{false, true}) {
            String prefix = hero ? "hero/poem_mediapipe/" : "player/poem_mediapipe/";
            Path armaturePath = hero ? assets.resolve("animmodels/entity/hero_biped_nightfall.json")
                    : Path.of("build/epicfight-mediapipe/biped.json");
            Armature armature;
            try (var input = Files.newInputStream(armaturePath)) {
                armature = new JsonAssetLoader(input, ResourceLocation.parse("herobrine_companion:test_biped"))
                        .loadArmature(Armature::new);
            }
            Map<String, Float> durations = new LinkedHashMap<>();
            durations.put("full", timing.get("duration").getAsFloat());
            for (String group : hero ? new String[]{"segments"} : new String[]{"segments", "specials"}) {
                for (var entry : timing.getAsJsonArray(group)) {
                    var t = entry.getAsJsonObject();
                    durations.put(t.get("name").getAsString(), t.get("duration").getAsFloat());
                }
            }
            durations.put("ready", 1F);
            durations.put("hold", 1F);
            for (var entry : durations.entrySet()) {
                String name = entry.getKey();
                AnimationClip clip;
                try (var input = Files.newInputStream(assets.resolve("animmodels/animations/" + prefix + name + ".json"))) {
                    clip = new JsonAssetLoader(input, ResourceLocation.parse("herobrine_companion:" + prefix + name))
                            .loadAnimationClip(armature);
                }
                if (Math.abs(clip.getClipTime() - entry.getValue()) > .00001F
                        || clip.getJointTransforms().size() != (name.equals("hold") ? 13 : 20)) {
                    throw new AssertionError("Invalid clip: " + prefix + name);
                }
                int samples = Math.round(entry.getValue() * 960);
                org.joml.Quaternionf previousRoot = null;
                double rootRotationDegrees = 0D;
                for (int tick = 0; tick <= samples; tick++) {
                    var pose = clip.getPoseInTime(tick * entry.getValue() / samples);
                    var rootTransform = pose.getJointTransformData().get("Root");
                    if (rootTransform != null) {
                        var q = rootTransform.rotation();
                        if (previousRoot != null) {
                            double dot = previousRoot.dot(q) / Math.sqrt(previousRoot.lengthSquared() * q.lengthSquared());
                            rootRotationDegrees += Math.toDegrees(2D * Math.acos(Math.min(1D, Math.abs(dot))));
                        }
                        previousRoot = new org.joml.Quaternionf(q);
                    }
                    for (var transform : pose.getJointTransformData().values()) {
                        var p = transform.translation(); var q = transform.rotation(); var s = transform.scale();
                        if (!Float.isFinite(p.x + p.y + p.z + q.x + q.y + q.z + q.w + s.x + s.y + s.z)
                                || Math.abs(q.lengthSquared() - 1F) > .002F || s.x <= 0 || s.y <= 0 || s.z <= 0) {
                            throw new AssertionError("Invalid runtime interpolation: " + prefix + name + " @ " + tick);
                        }
                    }
                }
                var result = new LinkedHashMap<String, Object>();
                result.put("clip", prefix + name); result.put("duration", clip.getClipTime());
                result.put("joints", clip.getJointTransforms().size()); result.put("samples", samples + 1);
                result.put("root_rotation_path_degrees", rootRotationDegrees);
                if (turningClips.contains(name) && rootRotationDegrees < 285D) {
                    throw new AssertionError("Engine lost the authored body turn: " + prefix + name);
                }
                reports.add(result);
                if (expectedTravel.containsKey(name)) {
                    // Exercise the same Root fallback and RAW_COORD copy used by
                    // ActionAnimation, then integrate forward travel at game ticks.
                    var motion = new LoadedMotion(clip);
                    var coords = new TransformSheet();
                    MoveCoordFunctions.RAW_COORD.set(motion, null, coords);
                    if (motion.getCoord() != clip.getJointTransforms().get("Root")) {
                        throw new AssertionError("Root movement sheet missing: " + prefix + name);
                    }
                    var origin = coords.getInterpolatedTranslation(0F);
                    var previous = origin;
                    double integrated = 0D, maxTickTravel = 0D, maxSideExcursion = 0D;
                    int ticks = (int) Math.ceil(entry.getValue() * 20);
                    for (int tick = 1; tick <= ticks; tick++) {
                        var point = coords.getInterpolatedTranslation(Math.min(tick / 20F, entry.getValue()));
                        double forward = previous.z - point.z;
                        // Turning steps deliberately travel sideways around
                        // their planted foot. Audit finite, bounded movement
                        // and the authored landing instead of forbidding it.
                        double sideways = point.x - previous.x;
                        maxSideExcursion = Math.max(maxSideExcursion, Math.abs(point.x - origin.x));
                        integrated += forward;
                        maxTickTravel = Math.max(maxTickTravel, Math.hypot(forward, sideways));
                        previous = point;
                    }
                    if (Math.abs(integrated - expectedTravel.get(name)) > .00002D || maxTickTravel > .30D
                            || Math.abs(previous.x - origin.x - expectedLateral.get(name)) > .00002D
                            || maxSideExcursion > .90D) {
                        throw new AssertionError("Wrong Root travel: " + prefix + name + " = " + integrated);
                    }
                    movement.add(Map.of("clip", prefix + name, "forward_blocks", integrated,
                            "lateral_blocks", previous.x - origin.x, "maximum_side_excursion_blocks", maxSideExcursion,
                            "max_20hz_step_blocks", maxTickTravel, "raw_coord_checked", true));
                }
            }
        }
        if (reports.size() != comboCount * 2 + 8) throw new AssertionError("Wrong player/Hero clip count");
        Files.writeString(Path.of("build/epicfight-mediapipe/engine_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(reports) + "\n");
        Files.writeString(Path.of("build/epicfight-mediapipe/movement_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(movement) + "\n");
        System.out.println("MEDIAPIPE_ENGINE_OK: " + reports.size() + " clips, both armatures, 960 Hz interpolation; "
                + movement.size() + " RAW_COORD movement tracks checked");
    }
}
