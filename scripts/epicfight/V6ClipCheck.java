import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.model.Armature;

/** Uses the project's actual Epic Fight dependency, including interpolation between keys. */
public class V6ClipCheck {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion");
        Path folder = assets.resolve("animmodels/animations/player/poem_v6");
        Armature armature;
        try (var input = Files.newInputStream(Path.of("build/epicfight-v6/biped.json"))) {
            armature = new JsonAssetLoader(input, ResourceLocation.parse("epicfight:entity/biped")).loadArmature(Armature::new);
        }
        var timing = JsonParser.parseString(Files.readString(assets.resolve("epicfight/poem_v6_timing.json"))).getAsJsonObject();
        Map<String, Float> durations = new LinkedHashMap<>();
        durations.put("full", 56.056F);
        for (String group : new String[]{"segments", "specials"}) {
            for (var entry : timing.getAsJsonArray(group)) {
                var t = entry.getAsJsonObject();
                durations.put(t.get("name").getAsString(), t.get("duration").getAsFloat());
            }
        }
        durations.put("ready", 1F);
        durations.put("hold", 1F);
        var reports = new ArrayList<Map<String, Object>>();
        for (var entry : durations.entrySet()) {
            String name = entry.getKey();
            AnimationClip clip;
            try (var input = Files.newInputStream(folder.resolve(name + ".json"))) {
                clip = new JsonAssetLoader(input, ResourceLocation.parse("herobrine_companion:player/poem_v6/" + name))
                        .loadAnimationClip(armature);
            }
            if (Math.abs(clip.getClipTime() - entry.getValue()) > .00001F
                    || clip.getJointTransforms().size() != (name.equals("hold") ? 13 : 20)) {
                throw new AssertionError("Invalid clip " + name + ": joints=" + clip.getJointTransforms().size());
            }
            int samples = Math.round(entry.getValue() * 240);
            for (int tick = 0; tick <= samples; tick++) {
                var pose = clip.getPoseInTime(tick * entry.getValue() / samples);
                for (var transform : pose.getJointTransformData().values()) {
                    var p = transform.translation(); var q = transform.rotation(); var s = transform.scale();
                    if (!Float.isFinite(p.x + p.y + p.z + q.x + q.y + q.z + q.w + s.x + s.y + s.z)
                            || Math.abs(q.lengthSquared() - 1F) > .002F || s.x <= 0 || s.y <= 0 || s.z <= 0) {
                        throw new AssertionError("Invalid interpolated pose " + name + " at " + tick);
                    }
                }
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("clip", name); result.put("duration", clip.getClipTime());
            result.put("joints", clip.getJointTransforms().size()); result.put("interpolated_samples", samples + 1);
            reports.add(result);
            System.out.println("EPIC_FIGHT_V6_LOADER_OK " + result);
        }
        if (reports.size() != 23) throw new AssertionError("Missing V6 clips");
        Files.writeString(Path.of("build/epicfight-v6/engine_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(reports) + "\n");
    }
}
