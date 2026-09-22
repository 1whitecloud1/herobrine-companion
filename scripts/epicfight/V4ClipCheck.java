import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.model.Armature;

/** Runs the installed Epic Fight loader against the actual exported resources. */
public class V4ClipCheck {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion/animmodels");
        Armature armature;
        try (var input = Files.newInputStream(assets.resolve("entity/hero_biped_nightfall.json"))) {
            armature = new JsonAssetLoader(input, ResourceLocation.parse("herobrine_companion:entity/hero_biped_nightfall"))
                    .loadArmature(Armature::new);
        }
        String[] suffixes = {"", "_1", "_2", "_3", "_4"};
        float[] durations = {4.2F, 1.1F, .95F, 1F, 1.15F};
        var reports = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < suffixes.length; i++) {
            String name = "hero_scythe_combo_v4" + suffixes[i];
            AnimationClip clip;
            try (var input = Files.newInputStream(assets.resolve("animations/hero/" + name + ".json"))) {
                clip = new JsonAssetLoader(input, ResourceLocation.parse("herobrine_companion:hero/" + name))
                        .loadAnimationClip(armature);
            }
            if (Math.abs(clip.getClipTime() - durations[i]) > 0.00001F || clip.getJointTransforms().size() != 20) {
                throw new AssertionError("Invalid clip " + name);
            }
            // Sample at 240 Hz, including points between exported keys and both endpoints.
            int samples = Math.round(durations[i] * 240);
            for (int tick = 0; tick <= samples; tick++) {
                var pose = clip.getPoseInTime(tick * durations[i] / samples);
                for (JointTransform transform : pose.getJointTransformData().values()) {
                    var p = transform.translation();
                    var q = transform.rotation();
                    var s = transform.scale();
                    if (!Float.isFinite(p.x + p.y + p.z + q.x + q.y + q.z + q.w + s.x + s.y + s.z)
                            || Math.abs(q.lengthSquared() - 1F) > .002F) {
                        throw new AssertionError("Non-finite/non-unit pose: " + name + " tick " + tick);
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clip", name);
            result.put("duration", clip.getClipTime());
            result.put("joints", clip.getJointTransforms().size());
            result.put("interpolated_samples", samples + 1);
            reports.add(result);
            System.out.println("EPIC_FIGHT_LOADER_OK " + result);
        }
        Files.writeString(Path.of("build/epicfight-v4/engine_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(reports) + "\n");
    }
}
