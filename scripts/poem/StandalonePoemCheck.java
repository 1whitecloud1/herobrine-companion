package com.whitecloud233.modid.herobrine_companion.combat.poem;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemSkinMesh;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemSkinMesh.Part;
import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemTrailMesh;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemItemMesh;
import com.whitecloud233.modid.herobrine_companion.client.render.PoemRenderBackend;
import com.whitecloud233.modid.herobrine_companion.client.render.StandalonePoemRenderer;
import com.whitecloud233.modid.herobrine_companion.network.PoemAnimationPacket;
import com.whitecloud233.modid.herobrine_companion.network.PoemAnimationRequestPacket;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

/** Runs the real sampler and vanilla cube renderer with Epic Fight and GeckoLib absent. */
public final class StandalonePoemCheck {
    private static int checks;
    private static double largestSourceError;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = StandalonePoemCheck.class.getClassLoader();
        check(loader.getResource("yesman/epicfight/main/EpicFightMod.class") == null, "Epic Fight must be absent from this test runtime");
        check(loader.getResource("software/bernie/geckolib/GeckoLib.class") == null, "GeckoLib must be absent from this test runtime");
        check(loader.getResource("assets/herobrine_companion/animations/poem_standalone/rig.json") == null,
                "Custom rig must not be scanned as a GeckoLib animation");
        check(loader.getResource("assets/herobrine_companion/animations/poem_standalone/weapon.json") == null,
                "Custom mesh must not be scanned as a GeckoLib animation");
        var weapon = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "weapon.json", com.google.gson.JsonObject.class);
        check(!weapon.getAsJsonArray("vertices").isEmpty() && !weapon.getAsJsonArray("faces").isEmpty(),
                "Relocated weapon mesh must still be readable by the standalone renderer");
        PoemMotionLibrary lib = PoemMotionLibrary.get(); lib.preload();
        for (int mode = 0; mode < 4; mode++) for (int step = 0; step < 4; step++) sample(lib, mode, step);
        legs(lib);
        trailsAndSteps(lib);
        nativeItem(lib);
        emissiveMaterials();
        clocks();
        packets();
        hooks();
        meshes(lib);
        Path report = Path.of("build/poem_standalone/validation.json"); Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", "passed", "checks", checks, "ordinary_clips", 16, "source_matrix_max_error", largestSourceError,
                "epic_fight_on_runtime_classpath", false, "geckolib_on_runtime_classpath", false,
                "vanilla_cube_skinning_executed", true, "leg_stance_regression_passed", true, "live_gameplay_tested", false)));
        System.out.println("STANDALONE_POEM_OK: " + checks + " checks; 16 actual clips; vanilla wide/slim/armor cubes; Epic Fight and GeckoLib absent; max source matrix error " + largestSourceError);
    }

    private static void emissiveMaterials() throws Exception {
        String pkg = StandalonePoemCheck.class.getPackageName().replace(".combat.poem", "").replace('.', '/');
        var material = read(pkg + "/client/render/PoemWeaponRenderTypes");
        boolean unlit = false;
        for (var method : material.methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("RENDERTYPE_EYES_SHADER")) unlit = true;
        }
        check(unlit, "Weapon emission must use the shader without normal or lightmap lighting");
        for (String relative : List.of("StandalonePoemRenderer", "PoemOfTheEndMeshRenderer")) {
            var node = read(pkg + "/client/render/" + relative);
            int base = 0, glow = 0;
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(pkg + "/client/render/PoemWeaponRenderTypes")) {
                    if (call.name.equals("base")) base++;
                    if (call.name.equals("glow")) glow++;
                }
            }
            check(base == 1 && glow == 1, "Both native renderers must actually connect the base and emissive materials");
        }
        int pairs = 0;
        for (int path = 0; path < 3; path++) {
            Recorder base = new Recorder(), glow = new Recorder();
            if (path == 0) {
                PoseStack pose = new PoseStack(); pose.translate(.5, .51, .5); pose.mulPose(Axis.YP.rotationDegrees(67));
                PoemItemMesh.render(pose, base, 0, 0);
                PoemItemMesh.render(pose, glow, LightTexture.FULL_BRIGHT, 0);
            } else {
                Matrix4f pose = new Matrix4f().translation(.5f, 1.3f, -.6f).rotateXYZ(.8f, .4f, -.7f);
                if (path == 2) pose.scale(-1, 1, 1);
                StandalonePoemRenderer.emitWeapon(pose, path == 2, base, 0);
                StandalonePoemRenderer.emitWeapon(pose, path == 2, glow, LightTexture.FULL_BRIGHT);
            }
            check(base.points.size() == 6096 && glow.points.size() == base.points.size(), "Each weapon material covers every original mesh vertex");
            for (int i = 0; i < base.points.size(); i++) {
                check(java.util.Arrays.equals(base.points.get(i), glow.points.get(i)), "Glowing blade and gem vertices must align exactly with the base mesh");
                check(base.lights.get(i) == 0 && glow.lights.get(i) == LightTexture.FULL_BRIGHT,
                        "A dark world darkens the ordinary material while the blade and gems stay full-bright");
                pairs++;
            }
        }
        Files.writeString(Path.of("build/poem_trail_step/emissive_material_validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "material_paths", 3, "matching_vertex_pairs", pairs, "unlit_shader_selected", true,
                "both_native_renderers_bind_glow", true, "dark_base_and_full_bright_glow_checked", true,
                "live_gameplay_tested", false)));
        System.out.println("POEM_EMISSIVE_MATERIAL_OK: native item and both animated hands; " + pairs + " aligned vertex pairs; unlit glow material");
    }

    private static void nativeItem(PoemMotionLibrary lib) throws Exception {
        check(PoemRenderBackend.choose(false, false) == PoemRenderBackend.FLAT, "Keep ordinary fallback without either animation mod");
        check(PoemRenderBackend.choose(true, false) == PoemRenderBackend.MESH, "Epic Fight without GeckoLib must use the native 3D mesh");
        check(PoemRenderBackend.choose(false, true) == PoemRenderBackend.GECKOLIB, "GeckoLib-only rendering stays intact");
        check(PoemRenderBackend.choose(true, true) == PoemRenderBackend.GECKOLIB, "Existing GeckoLib rendering stays preferred");
        var data = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "weapon.json", com.google.gson.JsonObject.class);
        float[][] rows = lib.rig.weapon_to_tool(); float[] flat = new float[16];
        for (int i = 0; i < 4; i++) System.arraycopy(rows[i], 0, flat, i * 4, 4);
        Matrix4f toTool = PoemMotionLibrary.matrix(flat);
        PoseStack hand = new PoseStack();
        hand.translate(0, 0, -.13); hand.mulPose(Axis.XP.rotationDegrees(-90));
        PoemItemMesh.display(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(false, hand);
        hand.translate(0, .01, 0);
        Recorder held = new Recorder(); PoemItemMesh.render(hand, held, 15728880, 0);
        check(held.points.size() == data.getAsJsonArray("faces").size() * 4, "Native fallback renders the entire detailed 3D mesh");
        float maxError = 0; int index = 0;
        for (var face : data.getAsJsonArray("faces")) for (var rawIndex : face.getAsJsonArray()) {
            int vertex = rawIndex.getAsInt(); var raw = data.getAsJsonArray("vertices").get(vertex).getAsJsonArray();
            Vector3f expected = toTool.transformPosition(new Vector3f(raw.get(0).getAsFloat(), raw.get(1).getAsFloat(), raw.get(2).getAsFloat()));
            float[] point = held.points.get(index++);
            float error = expected.distance(new Vector3f(point[0], point[1], point[2])); maxError = Math.max(maxError, error);
            check(error < .00002f, "Epic Fight native item grip, scale and blade orientation must match the calibrated 3D weapon");
            var uv = data.getAsJsonArray("uv").get(vertex).getAsJsonArray();
            check(Math.abs(point[3] - uv.get(0).getAsFloat()) < .000001 && Math.abs(point[4] - (1 - uv.get(1).getAsFloat())) < .000001, "Native fallback preserves mesh UVs");
            check(Math.abs(new Vector3f(point[5], point[6], point[7]).length() - 1) < .0001, "Native item normals remain normalized");
        }
        List<Map<String, Object>> views = new ArrayList<>();
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.NONE) continue;
            PoseStack pose = new PoseStack(); PoemItemMesh.display(context).apply(false, pose); pose.translate(0, .01, 0);
            Recorder rendered = new Recorder(); PoemItemMesh.render(pose, rendered, 15728880, 0);
            check(rendered.points.size() == held.points.size(), "Every 3D item display context renders the same full mesh");
            for (float[] p : rendered.points) {
                check(Float.isFinite(p[0]) && Float.isFinite(p[1]) && Float.isFinite(p[2]) && Math.abs(p[0]) < 8 && Math.abs(p[1]) < 8 && Math.abs(p[2]) < 8,
                        "Display transforms use block units and stay near the item origin");
            }
            views.add(Map.of("context", context.getSerializedName(), "vertices", rendered.points));
        }
        var base = ImageIO.read(StandalonePoemCheck.class.getResourceAsStream("/assets/herobrine_companion/textures/item/poem_of_the_end_native.png"));
        var glow = ImageIO.read(StandalonePoemCheck.class.getResourceAsStream("/assets/herobrine_companion/textures/item/poem_of_the_end_native_glow.png"));
        var original = ImageIO.read(StandalonePoemCheck.class.getResourceAsStream("/assets/herobrine_companion/textures/item/poem_of_the_end_geo.png"));
        check(base.getWidth() == original.getWidth() && glow.getHeight() == original.getHeight(), "Native item textures preserve original atlas dimensions");
        int glowing = 0;
        for (int y = 0; y < original.getHeight(); y++) for (int x = 0; x < original.getWidth(); x++) {
            int pixel = original.getRGB(x, y), alpha = pixel >>> 24;
            if (alpha > 0 && alpha < 255) {
                check((base.getRGB(x, y) >>> 24) == 0 && (glow.getRGB(x, y) >>> 24) == 255
                        && (glow.getRGB(x, y) & 0xffffff) == (pixel & 0xffffff), "Glowing pixels have their original colors on one emissive layer");
                glowing++;
            } else check(base.getRGB(x, y) == pixel && (glow.getRGB(x, y) >>> 24) == 0, "Ordinary pixels keep original lighting");
        }
        check(glowing > 0, "Blade edging and gems still glow without GeckoLib");
        Path out = Path.of("build/poem_trail_step");
        Files.writeString(out.resolve("native_item_preview.json"), new GsonBuilder().create().toJson(views));
        Files.writeString(out.resolve("native_item_validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "vertices", held.points.size(), "display_contexts", views.size(), "maximum_tool_space_error", maxError,
                "glowing_pixels", glowing, "epic_fight_without_geckolib_selects_3d", true, "geckolib_on_runtime_classpath", false,
                "live_gameplay_tested", false)));
        System.out.println("POEM_NATIVE_ITEM_OK: " + held.points.size() + " vertices; " + views.size() + " display contexts; grip error " + maxError + "; no GeckoLib");
    }

    private static void trailsAndSteps(PoemMotionLibrary lib) throws Exception {
        int poses = 0, impulses = 0, maximumEdges = 0;
        List<Map<String, Object>> preview = new ArrayList<>();
        int toolJoint = lib.joint("Tool_R");
        PlayerModel<?> model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        var weapon = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "weapon.json", com.google.gson.JsonObject.class);
        float[][] rows = lib.rig.weapon_to_tool(); float[] flat = new float[16];
        for (int i = 0; i < 4; i++) System.arraycopy(rows[i], 0, flat, i * 4, 4);
        Matrix4f weaponToTool = PoemMotionLibrary.matrix(flat);
        for (Vector3f endpoint : List.of(PoemBladeTrail.inner(), PoemBladeTrail.outer())) {
            float nearest = Float.POSITIVE_INFINITY;
            for (var raw : weapon.getAsJsonArray("vertices")) {
                var a = raw.getAsJsonArray();
                nearest = Math.min(nearest, weaponToTool.transformPosition(new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat())).distance(endpoint));
            }
            check(nearest < .06f, "Trail endpoint must touch the actual scythe blade mesh");
        }
        for (int mode = 0; mode < 4; mode++) for (int step = 0; step < 4; step++) {
            var clip = lib.clip(mode, step); var timing = clip.timing;
            PoemComboClock clock = new PoemComboClock(); clock.synchronize(mode, step, 0);
            PoemAttackStep movement = new PoemAttackStep(); int fired = 0;
            for (int tick = 0; tick <= Math.ceil((timing.duration() + .5f) * 20); tick++) {
                if (movement.poll(clock, tick)) fired++;
                check(!movement.poll(clock, tick), "Repeated ticks cannot stack an attack step");
            }
            check(fired == 1, "Each of the sixteen stages gets exactly one step"); impulses += fired;
            check(!new PoemAttackStep().poll(clock, (timing.duration() + 1) * 20), "Late playback cannot cause a recovery shove");
            for (float time = 0; time <= timing.duration() + PoemBladeTrail.LIFETIME + .05f; time += 1f / 120) {
                float age = time;
                var ribbons = PoemBladeTrail.sample(timing, age, t -> clip.sampleJoint(toolJoint, (float) t, true), t -> 1);
                long windows = timing.contacts().stream().filter(c -> Math.min(c.end(), age) - Math.max(c.start(), age - PoemBladeTrail.LIFETIME) >= .00001f).count();
                check(ribbons.size() == windows, "Each visible contact has its own ribbon, including fading tails");
                check(clip.sampleJoint(toolJoint, age, true).equals(clip.sample(age, true)[toolJoint], .00002f), "Fast socket sampling matches the complete animated skeleton");
                for (var ribbon : ribbons) {
                    maximumEdges = Math.max(maximumEdges, ribbon.edges().size());
                    check(ribbon.edges().size() <= 29, "Trail tessellation is bounded at high frame rates");
                    float previousAlpha = -1;
                    for (var edge : ribbon.edges()) {
                        check(edge.inner().isFinite() && edge.outer().isFinite(), "Trail coordinates are finite");
                        check(edge.inner().distance(edge.outer()) > 1, "Blade ribbon must not collapse onto the handle");
                        check(edge.alpha() >= previousAlpha && edge.alpha() >= 0 && edge.alpha() <= 1, "Old trail edges fade toward zero");
                        previousAlpha = edge.alpha();
                    }
                }
                poses++;
            }
            float age = Math.min(timing.contacts().get(0).end(), timing.contacts().get(0).start() + .11f);
            var ribbons = PoemBladeTrail.sample(timing, age, t -> clip.sampleJoint(toolJoint, (float) t, true), t -> 1);
            Matrix4f[] world = clip.sample(age, true);
            Recorder rightVertices = new Recorder(true), leftVertices = new Recorder(true);
            PoemTrailMesh.render(new Frame(world, lib.deform(world, false), 1, false, ribbons), model, new PoseStack(), rightVertices);
            PoemTrailMesh.render(new Frame(world, lib.deform(world, true), 1, true, ribbons), model, new PoseStack(), leftVertices);
            Recorder rightBody = new Recorder(true), leftBody = new Recorder(true);
            PoemTrailMesh.renderBody(new Frame(world, lib.deform(world, false), 1, false, ribbons), model, new PoseStack(), rightBody);
            PoemTrailMesh.renderBody(new Frame(world, lib.deform(world, true), 1, true, ribbons), model, new PoseStack(), leftBody);
            int expectedVertices = ribbons.stream().mapToInt(r -> (r.edges().size() - 1) * 8).sum();
            check(rightVertices.points.size() == expectedVertices && leftVertices.points.size() == expectedVertices, "Real trail renderer emits both glow passes in both hands");
            check(rightBody.points.size() == expectedVertices / 2 && leftBody.points.size() == expectedVertices / 2,
                    "A visible alpha-blended body accompanies every additive trail segment");
            for (int i = 0; i < rightBody.points.size(); i++) {
                float[] r = rightBody.points.get(i), l = leftBody.points.get(i);
                check(Math.abs(r[0] + l[0]) < .00001 && Math.abs(r[1] - l[1]) < .00001 && Math.abs(r[2] - l[2]) < .00001,
                        "Visible trail body follows the same left-hand mirror as the glow");
                check(r[6] == 240 && r[7] == 240 && r[5] >= 0 && r[5] <= 255, "Trail body stays full-bright with valid alpha");
            }
            for (int i = 0; i < expectedVertices; i++) {
                float[] r = rightVertices.points.get(i), l = leftVertices.points.get(i);
                check(Math.abs(r[0] + l[0]) < .00001 && Math.abs(r[1] - l[1]) < .00001 && Math.abs(r[2] - l[2]) < .00001, "Rendered left-handed trail mirrors the blade");
                check(r[6] == 240 && r[7] == 240, "Actual trail vertices use full block and sky light");
                check(r[3] >= 0 && r[3] <= 1 && r[4] >= 0 && r[4] <= 1 && r[5] >= 0 && r[5] <= 255, "Rendered trail UV and alpha are valid");
            }
            List<List<float[]>> strips = new ArrayList<>();
            for (var ribbon : ribbons) strips.add(ribbon.edges().stream().map(e -> new float[]{e.inner().x, e.inner().y, e.inner().z, e.outer().x, e.outer().y, e.outer().z, e.alpha()}).toList());
            preview.add(Map.of("mode", mode, "step", step, "time", age, "ribbons", strips,
                    "body_vertices", rightBody.points, "glow_vertices", rightVertices.points,
                    "tool", clip.sampleJoint(toolJoint, age, true).get(new float[16])));
        }
        for (int step = 0; step < 4; step++) {
            Vec3 impulse = PoemAttackStep.velocity(Vec3.ZERO, 0, step);
            check(impulse.x == 0 && impulse.z > 0 && impulse.z <= .21, "Standing attack gives a small forward step");
            check(PoemAttackStep.velocity(Vec3.ZERO, 90, step).x < 0, "Step follows player yaw");
            double drift = 0, velocity = impulse.z;
            for (int tick = 0; tick < 30; tick++) { drift += velocity; velocity *= .6 * .91; }
            check(drift > .3 && drift < .46, "Standing step stays below half a block on ordinary ground");
            for (double x : new double[]{-.8, -.25, 0, .25, .8}) for (double z : new double[]{-.8, -.25, 0, .25, .8}) {
                Vec3 before = new Vec3(x, -.13, z), after = PoemAttackStep.velocity(before, 43, step);
                check(after.y == before.y, "Attack steps preserve vertical velocity");
                if (before.horizontalDistance() >= PoemAttackStep.MAX_HORIZONTAL_SPEED) check(after.equals(before), "Fast existing movement is never slowed or boosted");
                else check(after.horizontalDistance() <= PoemAttackStep.MAX_HORIZONTAL_SPEED + .000001, "Movement has a hard horizontal speed limit");
            }
        }
        PoemComboClock combo = new PoemComboClock(); PoemAttackStep stepping = new PoemAttackStep();
        check(combo.request(0, 100), "Begin movement combo");
        check(stepping.poll(combo, 104), "First movement stage");
        for (int i = 0; i < 50; i++) { combo.request(0, 104); check(!stepping.poll(combo, 104), "Spam cannot repeat the same impulse"); }
        check(combo.tick(109), "Buffered next stage starts");
        check(stepping.poll(combo, 114), "Next accepted stage rearms the step");
        var texture = ImageIO.read(StandalonePoemCheck.class.getResourceAsStream("/assets/herobrine_companion/textures/trail/poem_standalone_trail.png"));
        check(texture != null && texture.getColorModel().hasAlpha(), "Standalone trail PNG decodes with transparency");
        check((texture.getRGB(texture.getWidth() - 1, 0) >>> 24) < 5, "Trail texture edge is transparent");
        check((texture.getRGB(texture.getWidth() - 1, texture.getHeight() / 4) >>> 24) > 190,
                "Fresh cyan trail shoulders remain opaque enough to show on a bright background");
        Path out = Path.of("build/poem_trail_step"); Files.createDirectories(out);
        Files.writeString(out.resolve("trail_preview.json"), new GsonBuilder().create().toJson(preview));
        Files.writeString(out.resolve("validation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "sampled_poses", poses, "stages_with_one_impulse", impulses, "maximum_ribbon_edges", maximumEdges,
                "blade_mesh_endpoints_checked", true, "step_speed_limit", PoemAttackStep.MAX_HORIZONTAL_SPEED,
                "actual_trail_renderer_and_left_hand_checked", true,
                "epic_fight_on_runtime_classpath", false, "live_gameplay_tested", false,
                "trail_lifetime_seconds", PoemBladeTrail.LIFETIME)));
        System.out.println("POEM_TRAIL_STEP_OK: " + poses + " ribbon poses; " + impulses + " single-step stages; blade anchors, fading, speed limit and PNG checked");
    }

    private static void legs(PoemMotionLibrary lib) throws Exception {
        PlayerModel<?> model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        float largestOpening = 0, minimumFlex = Float.POSITIVE_INFINITY;
        int poses = 0;
        for (int mode = 0; mode < 4; mode++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Matrix4f[] skin = lib.deform(lib.clip(mode, 0).sample(0, true), mirror);
                Vector3f right = soleFacing(model.rightLeg, Part.RIGHT_LEG, skin);
                Vector3f left = soleFacing(model.leftLeg, Part.LEFT_LEG, skin);
                float opening = right.angle(left);
                largestOpening = Math.max(largestOpening, opening);
                // The shared near-extended entry stance must not become the
                // 104-degree toe-out / crossed-knee pose from Unity11.
                check(opening < Math.toRadians(30), "Extended entry legs splay out: mode " + mode + ", mirror " + mirror);
                Vector3f forward = skin[lib.joint("Root")].transformDirection(new Vector3f(0, 1, 0));
                forward.z = 0; forward.normalize();
                check(right.dot(forward) > .90f && left.dot(forward) > .90f, "Entry soles face the pelvis forward direction");
            }
            for (int step = 0; step < 4; step++) {
                var clip = lib.clip(mode, step);
                for (float time = 0; time <= clip.timing.duration(); time += 1f / 120) {
                    Matrix4f[] world = clip.sample(time, true);
                    for (String side : List.of("R", "L")) {
                        Matrix4f thigh = world[lib.joint("Thigh_" + side)], shin = world[lib.joint("Leg_" + side)];
                        Vector3f upper = thigh.transformDirection(new Vector3f(0, 1, 0)).normalize();
                        Vector3f lower = shin.transformDirection(new Vector3f(0, 1, 0)).normalize();
                        Vector3f hinge = thigh.transformDirection(new Vector3f(1, 0, 0)).normalize();
                        float flex = -new Vector3f(upper).cross(lower).dot(hinge);
                        minimumFlex = Math.min(minimumFlex, flex);
                        check(flex >= -.001f, "Knee bends backward after thigh/shin frame flip: " + mode + "/" + step + "/" + side + " at " + time);
                        check(hinge.dot(shin.transformDirection(new Vector3f(1, 0, 0)).normalize()) > .999f,
                                "Thigh and shin twist in opposite directions");
                    }
                    poses++;
                }
            }
        }
        Path report = Path.of("build/poem_standalone/leg_stance_validation.json"); Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", "passed", "sampled_poses", poses, "maximum_entry_sole_opening_degrees", Math.toDegrees(largestOpening),
                "minimum_forward_knee_flex", minimumFlex, "actual_vanilla_sole_vertices", true, "left_handed_checked", true)));
        System.out.println("POEM_LEG_STANCE_OK: " + poses + " poses; actual mirrored vanilla soles; maximum entry opening " + Math.toDegrees(largestOpening));
    }

    private static Vector3f soleFacing(ModelPart part, Part type, Matrix4f[] skin) {
        PoemSkinMesh mesh = PoemSkinMesh.of(part, type);
        float bottom = Float.NEGATIVE_INFINITY, front = Float.POSITIVE_INFINITY, back = Float.NEGATIVE_INFINITY;
        for (var face : mesh.faces) for (var vertex : face.vertices()) {
            bottom = Math.max(bottom, vertex.point().y); front = Math.min(front, vertex.point().z); back = Math.max(back, vertex.point().z);
        }
        Vector3f a = new Vector3f(), b = new Vector3f(); int na = 0, nb = 0;
        for (var face : mesh.faces) for (var vertex : face.vertices()) {
            if (Math.abs(vertex.point().y - bottom) > .00001f) continue;
            Vector3f p = mesh.position(vertex, skin, PoemSkinMesh.current(part), 1);
            if (Math.abs(vertex.point().z - front) < .00001f) { a.add(p); na++; }
            if (Math.abs(vertex.point().z - back) < .00001f) { b.add(p); nb++; }
        }
        check(na > 0 && nb > 0, "Captured vanilla cube sole has front and back vertices");
        Vector3f facing = a.div(na).sub(b.div(nb));
        PoemSkinMesh.MODEL_TO_DCC.transformDirection(facing); facing.z = 0;
        return facing.normalize();
    }

    private static void sample(PoemMotionLibrary lib, int mode, int step) throws Exception {
        var clip = lib.clip(mode, step);
        Path path = Path.of("src/main/resources/assets/herobrine_companion/animmodels/animations/player/poem_unity09/" + PoemMotionLibrary.MODES.get(mode) + "/" + clip.timing.name() + ".json");
        var channels = JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("animation");
        int count = channels.get(0).getAsJsonObject().getAsJsonArray("time").size();
        for (int sample : new int[]{0, count / 3, count / 2, count - 1}) {
            float t = channels.get(0).getAsJsonObject().getAsJsonArray("time").get(sample).getAsFloat();
            Matrix4f[] expected = new Matrix4f[20];
            for (var channelElement : channels) {
                var channel = channelElement.getAsJsonObject();
                int joint = lib.joint(channel.get("name").getAsString());
                var row = channel.getAsJsonArray("transform").get(sample).getAsJsonArray();
                float[] values = new float[16]; for (int i = 0; i < 16; i++) values[i] = row.get(i).getAsFloat();
                expected[joint] = PoemMotionLibrary.matrix(values);
            }
            for (int i = 0; i < 20; i++) {
                int parent = lib.rig.joints().get(i).parent();
                if (parent >= 0) expected[i] = new Matrix4f(expected[parent]).mul(expected[i]);
            }
            Matrix4f[] actual = clip.sample(t, false), inPlace = clip.sample(t, true);
            for (int i = 0; i < 20; i++) {
                float[] a = actual[i].get(new float[16]), e = expected[i].get(new float[16]);
                for (int k = 0; k < 16; k++) {
                    largestSourceError = Math.max(largestSourceError, Math.abs(a[k] - e[k]));
                    check(Math.abs(a[k] - e[k]) < .00003f, "Source joint changed: " + mode + "/" + step + "/" + i);
                }
                check(Math.abs(actual[i].m30() - inPlace[i].m30() - actual[0].m30()) < .00003f, "In-place X reconstruction");
                check(Math.abs(actual[i].m31() - inPlace[i].m31() - actual[0].m31()) < .00003f, "In-place Y reconstruction");
                check(Math.abs(actual[i].m32() - inPlace[i].m32()) < .00003f, "Vertical motion must be retained");
            }
        }
        for (float t = .001f; t < clip.timing.duration(); t += .019f) {
            for (Matrix4f pose : clip.sample(t, true)) {
                // The authored hand/socket channels contain intentional uniform reach/weapon scaling.
                check(pose.isFinite() && pose.determinant3x3() > .01f && pose.determinant3x3() < 8,
                        "Invalid interpolated joint " + mode + "/" + step + " t=" + t + " det=" + pose.determinant3x3());
            }
        }
    }

    private static void clocks() {
        for (int mode = 0; mode < 4; mode++) {
            PoemComboClock clock = new PoemComboClock();
            check(!clock.request(-1, 0) && !clock.request(4, 0), "Reject invalid modes");
            double time = 100;
            check(clock.request(mode, time) && clock.step() == 0, "First basic");
            check(!clock.request(mode, time), "At most one input each tick");
            for (int i = 1; i <= 8; i++) {
                double recovery = time + clock.timing().recovery() * 20;
                check(!clock.request(mode, recovery - 1), "One early click buffers, not cuts contact");
                check(clock.tick(Math.ceil(recovery)) && clock.step() == i % 4, "Chain or restart at recovery");
                time = Math.ceil(recovery);
                check(!clock.tick(time), "Buffered click consumed exactly once");
            }
            time += clock.timing().duration() * 20 + PoemComboClock.RESET_TICKS + 1;
            check(clock.request(mode, time) && clock.step() == 0, "Idle resets to first stroke");
            clock.reset(); check(!clock.active(time), "Cancellation clears playback");
            check(clock.request((mode + 1) % 4, time + 2) && clock.step() == 0, "Mode switch starts new set");
            check(!clock.request((mode + 1) % 4, time + 3), "Early press does not restart first stroke");
            check(!clock.tick(time + 200), "Old buffered press expires");
        }
    }

    private static void packets() {
        UUID id = UUID.fromString("91331987-f34a-4766-8f36-5ee1bade8493");
        for (int mode = 0; mode < 4; mode++) for (int step = -1; step < 4; step++) {
            var packet = new PoemAnimationPacket(42, id, 3, mode, step, 1000, 1007);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                check(packet.equals(new PoemAnimationPacket(buffer)), "Animation packet round trip, including stop");
                check(buffer.readableBytes() == 0, "No trailing animation payload");
            } finally { buffer.release(); }
        }
        for (int slot : new int[]{-1, 0, 8, 9, 127}) {
            var packet = new PoemAnimationRequestPacket(slot, 3);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                check(packet.equals(new PoemAnimationRequestPacket(buffer)), "Request/cancel/boundary payload round trip");
            } finally { buffer.release(); }
        }
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = StandalonePoemCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(input != null, "Missing class " + name); new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void hooks() throws Exception {
        String pkg = StandalonePoemCheck.class.getPackageName().replace(".combat.poem", "");
        for (String relative : List.of("client.animation.StandalonePoemAnimation", "client.animation.PoemSkinMesh",
                "client.animation.PoemTrailMesh",
                "client.animation.PoemItemMesh", "client.render.PoemOfTheEndMeshRenderer", "client.render.PoemRenderBackend", "client.render.PoemWeaponRenderTypes",
                "client.render.StandalonePoemTrailRenderer", "combat.poem.PoemBladeTrail", "combat.poem.PoemAttackStep",
                "client.render.StandalonePoemRenderer", "combat.poem.StandalonePoemController", "network.PoemAnimationPacket",
                "network.PoemAnimationRequestPacket", "client.event.StandalonePoemEvents")) {
            Class.forName(pkg + "." + relative, false, StandalonePoemCheck.class.getClassLoader()).getDeclaredMethods();
            var node = read((pkg + "." + relative).replace('.', '/'));
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("yesman/") && !call.owner.startsWith("software/bernie/"), "Optional mod linked from standalone path");
            }
        }
        var renderer = read("net/minecraft/client/renderer/entity/LivingEntityRenderer");
        check(renderer.fields.stream().anyMatch(f -> f.name.equals("model") && f.desc.equals("Lnet/minecraft/client/model/EntityModel;")), "Renderer shadow descriptor");
        String render = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
        var method = renderer.methods.stream().filter(m -> m.name.equals("render") && m.desc.equals(render)).findFirst().orElseThrow();
        boolean found = false;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/entity/layers/RenderLayer") && call.name.equals("render")) {
            check(call.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"), "Layer redirect descriptor"); found = true;
        }
        check(found, "Actual vanilla layer call exists");
        var ageable = read("net/minecraft/client/model/AgeableListModel");
        String renderModel = pkg.contains(".modid.") ? "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V" : "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";
        check(ageable.methods.stream().anyMatch(m -> m.name.equals("renderToBuffer") && m.desc.equals(renderModel)), "Actual humanoid render hook exists");
        var held = read("net/minecraft/client/renderer/entity/layers/ItemInHandLayer");
        check(held.methods.stream().anyMatch(m -> m.name.equals("renderArmWithItem") && m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")), "Actual held item hook exists");
    }

    private static void meshes(PoemMotionLibrary lib) throws Exception {
        Matrix4f[] bind = new Matrix4f[20]; for (int i = 0; i < bind.length; i++) bind[i] = lib.bind(i);
        Matrix4f[] identity = lib.deform(bind, false);
        List<Map<String, Object>> exported = new ArrayList<>();
        for (boolean slim : new boolean[]{false, true}) {
            PlayerModel<?> model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            Map<Part, ModelPart> parts = new LinkedHashMap<>();
            parts.put(Part.HEAD, model.head); parts.put(Part.BODY, model.body);
            parts.put(Part.RIGHT_ARM, model.rightArm); parts.put(Part.LEFT_ARM, model.leftArm);
            parts.put(Part.RIGHT_LEG, model.rightLeg); parts.put(Part.LEFT_LEG, model.leftLeg);
            Map<String, Object> export = new LinkedHashMap<>(); export.put("slim", slim);
            List<Map<String, Object>> meshes = new ArrayList<>();
            for (var entry : parts.entrySet()) {
                ModelPart part = entry.getValue(); PoemSkinMesh mesh = PoemSkinMesh.of(part, entry.getKey());
                check(mesh.faces.size() >= (entry.getKey() == Part.HEAD ? 6 : 14), "Limbs/body must be subdivided at their hinge");
                float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
                for (var face : mesh.faces) for (var vertex : face.vertices()) {
                    Vector3f expected = vertex.point(), actual = mesh.position(vertex, identity, PoemSkinMesh.current(part), 1);
                    check(expected.distance(actual) < .00001, "Bind skin changed original vanilla cube");
                    check(Float.isFinite(vertex.u()) && Float.isFinite(vertex.v()), "UV preserved");
                    minX = Math.min(minX, expected.x); maxX = Math.max(maxX, expected.x);
                }
                if (entry.getKey() == Part.RIGHT_ARM) check(Math.abs(maxX - minX - (slim ? 3f : 4f) / 16) < .0001f, "Correct slim arm width");
                Recorder vertices = new Recorder();
                Matrix4f[] pose = lib.deform(lib.clip(2, 2).sample(.8f, true), false);
                mesh.render(part, pose, 1, new PoseStack(), vertices, 15728880, 0, -1);
                check(vertices.points.size() >= 24, "Real cube vertex renderer executed");
                for (float[] point : vertices.points) for (float value : point) check(Float.isFinite(value), "Rendered vertex/normal finite");
                part.visible = false; vertices.points.clear();
                mesh.render(part, pose, 1, new PoseStack(), vertices, 15728880, 0, -1);
                check(vertices.points.isEmpty(), "Hidden model parts must remain hidden"); part.visible = true;
                List<float[]> rest = new ArrayList<>(), uvs = new ArrayList<>();
                for (var face : mesh.faces) for (var vertex : face.vertices()) {
                    rest.add(xyz(vertex.point())); uvs.add(new float[]{vertex.u(), vertex.v()});
                }
                List<Map<String, Object>> poses = new ArrayList<>();
                for (int mode = 0; mode < 4; mode++) {
                    List<float[][]> frames = new ArrayList<>();
                    List<Map<String, Object>> schedule = new ArrayList<>();
                    for (int step = 0; step < 4; step++) {
                        var clip = lib.clip(mode, step);
                        for (float t = 0; t < clip.timing.recovery(); t += 1f / 30) {
                            Matrix4f[] world = clip.sample(t, true);
                            Matrix4f[] skin = lib.deform(world, false);
                            List<float[]> frame = new ArrayList<>();
                            for (var face : mesh.faces) for (var vertex : face.vertices()) frame.add(xyz(mesh.position(vertex, skin, PoemSkinMesh.current(part), 1)));
                            frames.add(frame.toArray(float[][]::new));
                            schedule.add(Map.of("step", step, "time", t, "tool", world[lib.joint("Tool_R")].get(new float[16])));
                        }
                    }
                    poses.add(Map.of("mode", mode, "frames", frames, "schedule", schedule));
                }
                meshes.add(Map.of("part", entry.getKey().name(), "rest", rest, "uv", uvs, "poses", poses));
            }
            export.put("meshes", meshes); exported.add(export);
        }
        HumanoidModel<?> armor = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1), 0), 64, 32).bakeRoot());
        var armorArm = PoemSkinMesh.of(armor.rightArm, Part.RIGHT_ARM);
        var armorLeg = PoemSkinMesh.of(armor.rightLeg, Part.RIGHT_LEG);
        check(armorArm.faces.size() >= 14 && armorLeg.faces.size() >= 14, "Real armor cubes bend too");
        Matrix4f[] right = lib.deform(lib.clip(0, 0).sample(.25f, true), false), left = lib.deform(lib.clip(0, 0).sample(.25f, true), true);
        Matrix4f mirror = new Matrix4f().scaling(-1, 1, 1);
        Matrix4f expectedLeft = new Matrix4f(mirror).mul(right[lib.joint("Hand_R")]).mul(mirror);
        check(expectedLeft.equals(left[lib.joint("Hand_L")], .00001f), "Left-handed source swaps arm bones and mirrors turns");
        Files.createDirectories(Path.of("build/poem_standalone"));
        Files.writeString(Path.of("build/poem_standalone/vanilla_mesh_preview.json"), new GsonBuilder().create().toJson(exported));
    }

    private static float[] xyz(Vector3f v) { return new float[]{v.x, v.y, v.z}; }

    private static final class Recorder implements VertexConsumer {
        public void endVertex() { }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
        final List<float[]> points = new ArrayList<>();
        final List<Integer> lights = new ArrayList<>();
        final boolean trail;
        int alpha, packedLight;
        Recorder() { this(false); }
        Recorder(boolean trail) { this.trail = trail; }
        float x, y, z, u, v;
        public VertexConsumer vertex(double x, double y, double z) { this.x = (float) x; this.y = (float) y; this.z = (float) z; return this; }
        public VertexConsumer color(int r, int g, int b, int a) { alpha = a; return this; }
        public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { packedLight = u | (v << 16); if (trail) points.add(new float[]{x, y, z, this.u, this.v, alpha, u, v}); return this; }
        public VertexConsumer normal(float nx, float ny, float nz) { points.add(new float[]{x, y, z, u, v, nx, ny, nz}); lights.add(packedLight); return this; }
    }
}
