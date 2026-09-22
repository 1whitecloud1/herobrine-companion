package com.whitecloud233.modid.herobrine_companion.combat.poem;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemTrailMesh;
import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Actual clips/rendered vertices and an independent JOML collision oracle, without optional animation mods. */
public final class PoemCollisionCheck {
    private static final String PACKAGE = "com/whitecloud233/modid/herobrine_companion/";
    private static int checks, trajectoryPoints, oracleCases, cuts, renderVertices;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = PoemCollisionCheck.class.getClassLoader();
        check(loader.getResource("yesman/epicfight/main/EpicFightMod.class") == null, "Epic Fight absent");
        check(loader.getResource("software/bernie/geckolib/GeckoLib.class") == null, "GeckoLib absent");
        obbs();
        PoemMotionLibrary lib = PoemMotionLibrary.get();
        lib.preload();
        trajectories(lib);
        renderer(lib);
        boundaries(lib);
        hooks();
        Path report = Path.of("build/poem_collision/validation.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", "passed", "checks", checks, "ordinary_clips", 16, "joml_oracle_cases", oracleCases,
                "trajectory_points", trajectoryPoints, "distinct_cuts", cuts, "rendered_blade_vertices", renderVertices,
                "epic_fight_on_runtime_classpath", false, "geckolib_on_runtime_classpath", false, "live_gameplay_tested", false)));
        System.out.println("POEM_COLLISION_OK: " + checks + " checks; " + oracleCases + " OBB oracle cases; "
                + trajectoryPoints + " moving/turning blade points; " + cuts + " deduplicated cuts; "
                + renderVertices + " actual rendered blade vertices");
    }

    private static AABB small(Vec3 point) { return new AABB(point, point).inflate(.002); }
    private static Vec3 vec(Vector3d v) { return new Vec3(v.x, v.y, v.z); }
    private static Vector3d vector(Vec3 v) { return new Vector3d(v.x, v.y, v.z); }

    private static void obbs() {
        Vec3 a = new Vec3(-2, 0, -2), b = new Vec3(2, 0, 2);
        PoemOBB diagonal = PoemOBB.sweep(a, b, a, b, .1);
        AABB emptyCorner = small(new Vec3(1.5, 0, -1.5));
        check(diagonal.bounds().intersects(emptyCorner) && !diagonal.intersects(emptyCorner),
                "An entity inside the enclosing AABB but away from the rotated blade must miss");
        Vec3 i0 = new Vec3(-2, 0, -1), o0 = new Vec3(2, 0, -1);
        Vec3 i1 = new Vec3(-2, 0, 1), o1 = new Vec3(2, 0, 1);
        check(!PoemOBB.sweep(i0, o0, i0, o0, .1).intersects(small(Vec3.ZERO))
                        && !PoemOBB.sweep(i1, o1, i1, o1, .1).intersects(small(Vec3.ZERO))
                        && PoemOBB.sweep(i0, o0, i1, o1, .1).intersects(small(Vec3.ZERO)),
                "Sweeping must hit a thin target missed by both endpoint poses");
        check(PoemOBB.sweep(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, .1).intersects(small(Vec3.ZERO)),
                "A stationary or degenerate blade still produces a finite box");

        Random random = new Random(0x504f454dL);
        int hits = 0, misses = 0;
        for (int i = 0; i < 6000; i++) {
            Quaterniond rotation = new Quaterniond().rotationXYZ(random.nextDouble() * 6, random.nextDouble() * 6, random.nextDouble() * 6);
            Vector3d u = rotation.transform(new Vector3d(1, 0, 0));
            Vector3d v = rotation.transform(new Vector3d(0, 1, 0));
            Vector3d w = rotation.transform(new Vector3d(0, 0, 1));
            Vec3 center = new Vec3(i % 2 == 0 ? 29_000_000 : 0, 64, -123.25);
            double length = .3 + random.nextDouble() * 3, width = .01 + random.nextDouble(), pad = .1;
            Vec3 along = vec(u).scale(length), across = vec(v).scale(width);
            PoemOBB box = PoemOBB.sweep(center.subtract(along).subtract(across), center.add(along).subtract(across),
                    center.subtract(along).add(across), center.add(along).add(across), pad);
            Vec3 point = center.add(random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3);
            Vec3 half = new Vec3(.01 + random.nextDouble() * .6, .01 + random.nextDouble(), .01 + random.nextDouble() * .6);
            AABB target = new AABB(point.subtract(half), point.add(half));
            boolean expected = Intersectiond.testObOb(vector(center), u, v, w, new Vector3d(length + pad, width + pad, pad),
                    vector(point), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1), vector(half));
            check(box.intersects(target) == expected, "OBB disagrees with JOML on case " + i);
            if (expected) hits++; else misses++;
            oracleCases++;
        }
        check(hits > 100 && misses > 100, "Oracle covers intersections and separations");
    }

    private static PoemBladeSweep.Actor actor(double tick, boolean left, float yaw) {
        return new PoemBladeSweep.Actor(new Vec3(29_000_000 + .07 * tick, 64 + .013 * tick, -123.25 + .11 * tick),
                yaw + (float) (tick * 7), left, -.125);
    }

    private static void trajectories(PoemMotionLibrary lib) {
        int tool = lib.joint("Tool_R");
        UUID first = new UUID(1, 2), second = new UUID(3, 4);
        for (int mode = 0; mode < 4; mode++) for (int step = 0; step < 4; step++) {
            var clip = lib.clip(mode, step);
            for (boolean left : new boolean[]{false, true}) for (float yaw : new float[]{0, 179}) {
                PoemBladeSweep sweep = new PoemBladeSweep(mode, step, 100, actor(0, left, yaw));
                int[] counts = new int[clip.timing.contacts().size()];
                for (int tick = 1; tick <= Math.ceil(clip.timing.duration() * 20) + 8; tick++) {
                    var slices = sweep.poll(100 + tick, actor(tick, left, yaw));
                    check(sweep.poll(100 + tick, actor(tick, left, yaw)).isEmpty(), "A repeated poll must not attack twice");
                    for (var slice : slices) {
                        var contact = clip.timing.contacts().get(slice.contact());
                        double from = Math.max((tick - 1) / 20.0, contact.start());
                        double to = Math.max(from, Math.min(tick / 20.0, contact.end()));
                        for (int t = 0; t <= 12; t++) {
                            double time = from + (to - from) * t / 12;
                            Matrix4f socket = clip.sampleJoint(tool, (float) time, true);
                            Vec3 inner = actor(time * 20, left, yaw).toWorld(socket.transformPosition(PoemBladeTrail.inner()));
                            Vec3 outer = actor(time * 20, left, yaw).toWorld(socket.transformPosition(PoemBladeTrail.outer()));
                            for (double u : new double[]{0, .25, .5, .75, 1}) {
                                check(slice.intersects(small(inner.lerp(outer, u))),
                                        "Blade point missed: " + mode + "/" + step + " tick " + tick + " at " + time);
                                trajectoryPoints++;
                            }
                        }
                        AABB enclosingTarget = slice.bounds().inflate(.5);
                        if (sweep.claim(slice, first, enclosingTarget)) {
                            counts[slice.contact()]++;
                            check(sweep.claim(slice, second, enclosingTarget), "A cut can hit a second target");
                        }
                        check(!sweep.claim(slice, first, enclosingTarget) && !sweep.claim(slice, second, enclosingTarget),
                                "Repeated OBB samples cannot multiply damage within a cut");
                    }
                }
                for (int count : counts) { check(count == 1, "Every separate contact rearms exactly one hit"); cuts++; }
            }
        }
    }

    private static void renderer(PoemMotionLibrary lib) throws Exception {
        PlayerModel<?> model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        int tool = lib.joint("Tool_R");
        Class<?> playback = Class.forName(PACKAGE.replace('/', '.') + "client.animation.StandalonePoemAnimation$Playback");
        var constructor = playback.getDeclaredConstructor(int.class, int.class, int.class, int.class, double.class, Matrix4f[].class, float.class);
        constructor.setAccessible(true);
        var weight = playback.getDeclaredMethod("weight", double.class); weight.setAccessible(true);
        var sampleTool = playback.getDeclaredMethod("tool", double.class); sampleTool.setAccessible(true);
        for (int mode = 0; mode < 4; mode++) for (int step = 0; step < 4; step++) {
            var clip = lib.clip(mode, step);
            var contact = clip.timing.contacts().get(0);
            for (boolean continuing : new boolean[]{false, true}) {
                Object playing = constructor.newInstance(1, 0, mode, step, 100D,
                        continuing ? lib.clip((mode + 1) % 4, 0).sample(.2f, true) : null, .3f);
                double time = 100D + contact.start() * 20D;
                check(Math.abs((float) weight.invoke(playing, time) - 1) < .00001,
                        "The rendered blade is fully in its attack pose when damage starts");
                check(((Matrix4f) sampleTool.invoke(playing, time)).equals(clip.sampleJoint(tool, contact.start(), true), .00002f),
                        "Rendering and collision sample the same socket at contact start");
            }
            float age = (contact.start() + contact.end()) * .5f;
            var ribbons = PoemBladeTrail.sample(clip.timing, age, t -> clip.sampleJoint(tool, (float) t, true), t -> 1);
            Matrix4f[] world = clip.sample(age, true);
            for (boolean left : new boolean[]{false, true}) for (float yaw : new float[]{0, 73, 179}) {
                var pose = new PoemBladeSweep.Actor(new Vec3(29_000_000, 64, -123.25), yaw, left, -.125);
                PoseStack stack = new PoseStack();
                stack.translate(0, -.125, 0);
                stack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
                stack.scale(-.9375f, -.9375f, .9375f);
                stack.translate(0, -1.501, 0);
                Recorder recorder = new Recorder();
                PoemTrailMesh.renderBody(new Frame(world, lib.deform(world, left), 1, left, ribbons), model, stack, recorder);
                int vertex = 0;
                for (var ribbon : ribbons) for (int i = 0; i < ribbon.edges().size() - 1; i++) {
                    var edge = ribbon.edges().get(i);
                    // The visible body pass widens by 1.14; remove just that cosmetic halo.
                    Vec3 a = recorder.points.get(vertex), b = recorder.points.get(vertex + 1);
                    Vec3 middle = a.lerp(b, .5);
                    Vec3 inner = middle.add(a.subtract(middle).scale(1 / 1.14)).add(pose.position());
                    Vec3 outer = middle.add(b.subtract(middle).scale(1 / 1.14)).add(pose.position());
                    check(inner.distanceTo(pose.toWorld(edge.inner())) < .00001, "Rendered inner blade differs from world hit coordinates");
                    check(outer.distanceTo(pose.toWorld(edge.outer())) < .00001, "Rendered outer blade differs from world hit coordinates");
                    renderVertices += 2;
                    vertex += 4;
                }
                check(vertex == recorder.points.size(), "Every actual trail quad was inspected");
            }
        }
    }

    private static void boundaries(PoemMotionLibrary lib) {
        var pose = new PoemBladeSweep.Actor(Vec3.ZERO, 179, false, 0);
        for (int mode = 0; mode < 4; mode++) for (int step = 0; step < 4; step++) {
            var timing = lib.timing(mode, step);
            var sweep = new PoemBladeSweep(mode, step, 0, pose);
            check(sweep.poll(timing.contacts().get(0).start() * 20 - .001, pose).isEmpty(), "Wind-up never hits");
            for (int c = 0; c < timing.contacts().size(); c++) {
                var contact = timing.contacts().get(c);
                var atEnd = sweep.poll(contact.end() * 20D, pose);
                check(atEnd.size() == 1 && atEnd.get(0).contact() == c, "Polling across a contact still sweeps its final blade segment");
                double endGap = c + 1 < timing.contacts().size() ? timing.contacts().get(c + 1).start() : timing.duration();
                if (endGap > contact.end()) {
                    check(sweep.poll((contact.end() + endGap) * 10, pose).isEmpty(), "Separate contacts/recovery must not be bridged");
                }
            }
            check(sweep.poll((timing.duration() + PoemBladeTrail.LIFETIME) * 20D, pose).isEmpty(), "Afterimages never remain damaging");
        }
        var wrap = pose.interpolate(new PoemBladeSweep.Actor(Vec3.ZERO, -179, false, 0), .5);
        check(Math.abs(Math.abs(wrap.yaw()) - 180) < .001, "Yaw wraps over two degrees rather than a full spin");
        var teleport = new PoemBladeSweep(0, 0, 0, pose);
        check(teleport.poll(5, new PoemBladeSweep.Actor(new Vec3(100, 0, 0), 0, false, 0)).isEmpty()
                        && teleport.cancelled() && teleport.poll(6, pose).isEmpty(), "Teleport cancels without a phantom ranged sweep");
        var mirror = new PoemBladeSweep(0, 0, 0, pose);
        check(mirror.poll(5, new PoemBladeSweep.Actor(Vec3.ZERO, 179, true, 0)).isEmpty() && mirror.cancelled(),
                "Changing hands cannot create a sweep between mirrored blades");
        var invalid = new PoemBladeSweep(0, 0, 0, pose);
        check(invalid.poll(Double.NaN, pose).isEmpty() && invalid.cancelled(), "Invalid clock cannot damage");
        var exactTick = new PoemBladeSweep(2, 0, 0, pose);
        check(!exactTick.poll(8, pose).isEmpty() && exactTick.poll(9, pose).isEmpty(),
                "Float contact endpoints at 0.4s cannot leak damage into the next tick");
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = PoemCollisionCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Compiled class exists: " + name);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }

    private static int calls(ClassNode node, String method, String callName) {
        int count = 0;
        for (var m : node.methods) if (method == null || m.name.equals(method)) for (var i : m.instructions) {
            if (i instanceof MethodInsnNode call && call.name.equals(callName)) count++;
        }
        return count;
    }

    private static void hooks() throws Exception {
        for (String name : List.of("PoemOBB", "PoemBladeSweep", "PoemMeleeHit", "StandalonePoemController")) {
            String path = PACKAGE + "combat/poem/" + name;
            Class.forName(path.replace('/', '.'), false, PoemCollisionCheck.class.getClassLoader()).getDeclaredMethods();
            for (var method : read(path).methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                check(!call.owner.startsWith("net/minecraft/client/") && !call.owner.startsWith("yesman/")
                                && !call.owner.startsWith("software/bernie/"), "Server collision links a client or optional class");
            }
        }
        var player = read("net/minecraft/world/entity/player/Player");
        check(player.methods.stream().anyMatch(m -> m.name.equals("getAttackStrengthScale") && m.desc.equals("(F)F")), "Charge injection target exists");
        check(player.methods.stream().anyMatch(m -> m.name.equals("resetAttackStrengthTicker") && m.desc.equals("()V")), "Charge reset injection target exists");
        check(calls(read(PACKAGE + "combat/poem/PoemMeleeHit"), "attack", "attack") == 1,
                "Swept hits must execute Player.attack for enchantments, critical hits and Forge hooks");
        var controller = read(PACKAGE + "combat/poem/StandalonePoemController");
        check(calls(controller, "onAttackEntity", "setCanceled") == 1 && calls(controller, "onAttackEntity", "allows") == 1,
                "Raw crosshair hits are rejected; only the scoped swept target is allowed");
        check(calls(controller, "collide", "hasLineOfSight") == 1 && calls(controller, "collide", "claim") == 1,
                "Occlusion and per-cut deduplication guard actual damage");
        var input = read(PACKAGE + "client/event/StandalonePoemEvents");
        check(calls(input, "onAttack", "setCanceled") == 1 && calls(input, "onAttack", "setSwingHand") == 1,
                "Combo input does not also send an immediate vanilla hit");
        check(calls(read(PACKAGE + "item/PoemOfTheEndItem"), "canPerformAction", "suppressVanillaSweep") == 1,
                "Vanilla's unrelated AABB sweep cannot damage outside the blade trajectory");
        var renderer = read("net/minecraft/client/renderer/entity/LivingEntityRenderer");
        check(calls(renderer, "render", "setupRotations") == 1, "Exact vanilla render rotation hook exists");
        var renderMixin = read(PACKAGE + "mixin/client/PoemLivingRendererMixin");
        check(calls(renderMixin, "poem$attackFacing", "attackYaw") == 1,
                "Visual body orientation uses the server's attack direction");
        var facing = renderMixin.methods.stream().filter(m -> m.name.equals("poem$attackFacing")).findFirst().orElseThrow();
        check(facing.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)F"),
                "Facing hook uses a typed argument modifier, without a synthetic Args class");
        check(facing.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")),
                "Forge startup regression: the facing hook must not use ModifyArgs");
    }

    private static final class Recorder implements VertexConsumer {
        final List<Vec3> points = new ArrayList<>();
        public VertexConsumer vertex(double x, double y, double z) { points.add(new Vec3(x, y, z)); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    }
}
