package com.whitecloud233.modid.herobrine_companion.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Executes the native sampler and segmented Hero cubes without optional animation mods or a GPU. */
public final class HeroPoemAnimationChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassLoader loader = HeroPoemAnimationChecks.class.getClassLoader();
        check(loader.getResource("yesman/epicfight/main/EpicFightMod.class") == null, "Epic Fight is absent");
        check(loader.getResource("software/bernie/geckolib/GeckoLib.class") == null, "GeckoLib is absent");
        hooks();
        PoemMotionLibrary lib = PoemMotionLibrary.get();
        Matrix4f[] base = lib.locomotion("idle").sample(.3f, true);
        playback(base);
        nativeMoves(base);
        trailVertices();
        com.whitecloud233.modid.herobrine_companion.combat.poem.PoemActionChecks.run();
        for (int mode = 0; mode < 4; mode++) {
            int duration = mode == 3 ? 7 : mode == 0 ? 10 : 14;
            for (int step = 0; step < 4; step++) {
                int serial = step + 1;
                check(HeroPoemAnimation.step(serial) == step && HeroPoemAnimation.step(serial + 4) == step,
                        "Attack serial starts at cut one and loops through four cuts");
                PoemMotionLibrary.Clip clip = lib.clip(mode, step);
                matrices(HeroPoemAnimation.sample(mode, serial, 0, duration, base, base), base, "Starts at base pose");
                float elapsed = duration * .45f;
                Matrix4f[] pose = HeroPoemAnimation.sample(mode, serial, elapsed, duration, base, base);
                matrices(pose, clip.sample(.45f * clip.timing.duration(), true), "Full-weight cut matches player matrices");
                matrices(HeroPoemAnimation.sample(mode, serial, duration, duration, base, base), clip.sample(clip.timing.duration(), true), "Holds cut for next combo link");
                matrices(HeroPoemAnimation.sample(mode, serial, duration + 100, duration, base, base), clip.sample(clip.timing.duration(), true), "Clamps stale ticks at the final cut pose");
                for (boolean left : new boolean[]{false, true}) {
                    Matrix4f[] skin = lib.deform(pose, left);
                    ModelPart root = HeroModel.createBodyLayer(false).bakeRoot();
                    mesh(root.getChild("right_arm"), PoemSkinMesh.Part.RIGHT_ARM, skin);
                    mesh(root.getChild("left_arm"), PoemSkinMesh.Part.LEFT_ARM, skin);
                    mesh(root.getChild("right_leg"), PoemSkinMesh.Part.RIGHT_LEG, skin);
                    mesh(root.getChild("left_leg"), PoemSkinMesh.Part.LEFT_LEG, skin);
                    mesh(root.getChild("right_sleeve"), PoemSkinMesh.Part.RIGHT_ARM, skin);
                    mesh(root.getChild("left_pants"), PoemSkinMesh.Part.LEFT_LEG, skin);
                }
            }
        }
        System.out.println("HeroPoemAnimationChecks: " + checks + " checks passed; 48 mode/move combinations, blade ribbons, both hands, segmented Hero mesh; Epic Fight and GeckoLib absent");
    }

    private static void nativeMoves(Matrix4f[] base) {
        var lib = PoemMotionLibrary.get();
        int socket = lib.joint("Tool_R");
        for (int mode = 0; mode < 4; mode++) {
            Matrix4f[] previous = null;
            for (int move = 0; move < PoemMotionLibrary.MOVE_COUNT; move++) {
                var clip = lib.clip(mode, move);
                int duration = (int) Math.ceil(clip.timing.duration() * 20 - .00001);
                HeroPoemAnimation.Playback playback = new HeroPoemAnimation.Playback();
                playback.sampleMove(true, mode, 1, move, 0, duration, 100, base);
                check(playback.trail(100).isEmpty(), "No blade trail before a cut begins");
                for (var contact : clip.timing.contacts()) {
                    float seconds = (contact.start() + contact.end()) * .5f;
                    double now = 100 + seconds * duration / clip.timing.duration();
                    Matrix4f[] pose = playback.sampleMove(true, mode, 1, move, 0, duration, now, base);
                    var trail = playback.trail(now);
                    check(!trail.isEmpty(), "Every Hero move emits blade ribbons during each authored contact");
                    var edges = trail.get(trail.size() - 1).edges();
                    var end = edges.get(edges.size() - 1);
                    check(end.inner().equals(pose[socket].transformPosition(
                                    com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail.inner()), .0001f)
                            && end.outer().equals(pose[socket].transformPosition(
                                    com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail.outer()), .0001f),
                            "Hero blade trail ends on the actually rendered, blended weapon");
                    for (var ribbon : trail) for (var edge : ribbon.edges()) {
                        check(edge.inner().isFinite() && edge.outer().isFinite() && edge.weight() == 1,
                                "Ground and air ribbons retain finite full-weight blade geometry");
                    }
                }
                Matrix4f[] representative = clip.sample(clip.timing.recovery() * .5f, true);
                if (previous != null) {
                    boolean different = false;
                    for (int joint = 0; joint < representative.length; joint++) {
                        different |= !representative[joint].equals(previous[joint], .001f);
                    }
                    check(different, "Consecutive native move IDs select visibly different authored poses");
                }
                previous = representative;
                playback.sampleMove(false, mode, 1, move, 0, duration, 200, base);
                check(playback.trail(200).isEmpty(), "Stopped attacks do not leave a permanent trail");
            }
        }
    }

    /** Exercise the real entity buffer: a missing overlay/normal throws at endVertex. */
    private static void trailVertices() {
        var root = net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.HumanoidModel.createMesh(
                        net.minecraft.client.model.geom.builders.CubeDeformation.NONE, 0), 64, 64).bakeRoot();
        var model = new net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity>(root);
        var ribbon = new com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail.Ribbon(List.of(
                new com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail.Edge(
                        new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), 1, .4f, 0),
                new com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail.Edge(
                        new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), 1, 1, 1)));
        for (boolean left : new boolean[] {false, true}) {
            var frame = new StandalonePoemAnimation.Frame(new Matrix4f[0], new Matrix4f[0], 1, left, List.of(ribbon));
            var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(1024);
            var format = com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY;
            buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, format);
            PoemTrailMesh.renderBody(frame, model, new PoseStack(), buffer, .2f);
            PoemTrailMesh.render(frame, model, new PoseStack(), buffer, .2f);
            var rendered = buffer.end();
            try {
                check(rendered.drawState().vertexCount() == 12, "Body and both glow layers emit complete entity quads");
                var data = rendered.vertexBuffer().order(java.nio.ByteOrder.nativeOrder());
                for (int vertex = 0; vertex < 12; vertex++) {
                    for (int axis = 0; axis < 3; axis++) {
                        check(Float.isFinite(data.getFloat(vertex * format.getVertexSize() + axis * 4)),
                                "Both hands produce finite entity-buffer positions");
                    }
                }
            } finally { rendered.release(); }
        }
    }

    private static void playback(Matrix4f[] base) {
        for (int mode = 0; mode < 4; mode++) {
            int duration = mode == 3 ? 7 : mode == 0 ? 10 : 14;
            HeroPoemAnimation.Playback playback = new HeroPoemAnimation.Playback();
            matrices(playback.sample(true, mode, 1, 0, duration, 100.25, base), base, "New cut starts from visible pose");
            // Metadata can stay unchanged across client tick boundaries. Playback must keep moving
            // at render rate instead of rewinding every time partialTick wraps from one to zero.
            for (int frame = 1; frame <= 16; frame++) {
                double now = 100.25 + frame * .25;
                Matrix4f[] pose = playback.sample(true, mode, 1, 0, duration, now, base);
                if (frame >= 8) matrices(pose,
                        HeroPoemAnimation.sample(mode, 1, (float) (now - 100), duration, base, null),
                        "Stalled metadata preserves continuous local playback");
                matrices(playback.sample(true, mode, 1, 0, duration, now, base), pose,
                        "Multiple renders at the same time do not advance the clock");
            }
            Matrix4f[] before = playback.sample(true, mode, 1, 6, duration, 104.5, base);
            matrices(before, HeroPoemAnimation.sample(mode, 1, 4.5f, duration, base, null),
                    "Batched metadata does not jump the current cut forward");
            before = playback.sample(true, mode, 1, 1, duration, 104.75, base);
            matrices(before,
                    HeroPoemAnimation.sample(mode, 1, 4.75f, duration, base, null),
                    "Late metadata does not rewind the current cut");

            int nextDuration = duration + (mode == 3 ? 1 : 2);
            matrices(playback.sample(true, mode, 2, 4, nextDuration, 104.75, base), before,
                    "Late next-serial update blends from the previous cut");
            Matrix4f[] next = playback.sample(true, mode, 2, 6, nextDuration, 106.75, base);
            matrices(next, HeroPoemAnimation.sample(mode, 2, 6.75f, nextDuration, base, null),
                    "New serial anchors to its own synchronized age");
            matrices(playback.sample(false, mode, 2, 0, nextDuration, 106.75, base), next,
                    "Interrupted attack retains its visible pose at hand-off");
            matrices(playback.sample(false, mode, 2, 0, nextDuration, 107.75, base),
                    PoemMotionLibrary.blend(next, base, .5f), "Interrupted attack eases back to locomotion");
            matrices(playback.sample(false, mode, 2, 0, nextDuration, 108.75, base), base,
                    "Recovery completes after two local ticks");

            playback.sample(true, mode, 3, 0, duration, 110, base);
            before = playback.sample(true, mode, 3, 3, duration, 113, base);
            matrices(playback.sample(true, (mode + 1) % 4, 3, 3, duration, 113, base), before,
                    "Weapon mode change blends without a pose jump");
        }
    }

    private static void hooks() throws Exception {
        String pkg = HeroPoemAnimationChecks.class.getPackageName().replace(".client.animation", "").replace('.', '/');
        check(calls(pkg + "/client/render/StandalonePoemRenderer", "push", pkg + "/client/animation/HeroPoemAnimation", "frame"),
                "Living renderer installs Hero's native frame");
        check(calls(pkg + "/client/model/HeroModel", "setupAnim", pkg + "/client/animation/HeroPoemAnimation", "shouldAnimate"),
                "Native skinning bypasses legacy Euler poses");
        check(calls(pkg + "/client/render/HeroHeldItemLayer", "renderArmWithItem", pkg + "/client/render/StandalonePoemRenderer", "renderHeld"),
                "Hero weapons use the same Tool_R socket as players");
        check(calls(pkg + "/client/animation/HeroPoemAnimation", "shouldAnimate", pkg + "/api/compat/HeroEpicFightCompat", "isRuntimeBridgeReady"),
                "Only an active add-on bridge takes ownership from native animation");
        check(calls(pkg + "/client/event/PoemScytheInputEvents", "send", pkg + "/client/animation/StandalonePoemAnimation", "gesture"),
                "Gesture input routes to standalone prediction without Epic Fight");
        check(calls(pkg + "/client/event/PoemScytheInputEvents", "reset", pkg + "/combat/poem/StandalonePoemController", "standalone"),
                "Input reset selects cleanup based on whether an EF engine consumer exists");
        check(calls(pkg + "/entity/ai/goal/HeroBattleStanceGoal", "tick", pkg + "/entity/ai/combat/HeroPoemCombat", "tick"),
                "Native combat is reachable from the running battle goal");
        check(calls(pkg + "/entity/ai/combat/HeroPoemCombat", "begin", pkg + "/entity/HeroEntity", "setBattlePoemMove")
                && calls(pkg + "/client/animation/HeroPoemAnimation", "frame", pkg + "/entity/HeroEntity", "getBattlePoemMove"),
                "Server-selected ground/air move is synchronized to the rendered pose");
        check(calls(pkg + "/client/animation/HeroPoemAnimation", "frame", pkg + "/client/animation/HeroPoemAnimation$Playback", "trail"),
                "Hero frames supply real blade trails to the shared weapon renderer");
        check(calls(pkg + "/entity/HeroEntity", "aiStep", pkg + "/entity/logic/HeroMovementState", "afterAiStep"),
                "Entity AI delegates post-goal physics to the movement owner");
        check(calls(pkg + "/entity/HeroEntity", "beginBattleAction", pkg + "/entity/logic/data/HeroCombatTimeline", "beginBattleAction"),
                "Combat timeline owns attack transitions and serial changes");
        check(calls(pkg + "/entity/HeroEntity", "playOfferGesture", pkg + "/entity/logic/HeroGestureController", "start")
                && calls(pkg + "/entity/HeroEntity", "holdForGiftGesture", pkg + "/entity/logic/HeroGestureController", "hold"),
                "Gesture lifecycle owns the display and movement lock");
        check(calls(pkg + "/entity/HeroEntity", "tick", pkg + "/entity/logic/HeroChallengePresentation", "tickBossBar"),
                "Challenge presentation owns boss-bar updates");
        for (var field : read(pkg + "/entity/HeroEntity").fields) {
            check(!List.of("groundNavigation", "flyingNavigation", "outOfWaterTimer", "tpFollowHoldUntilTick",
                    "bossEvent", "challengeAfterimages").contains(field.name),
                    "Subsystem state is owned by its component rather than duplicated on HeroEntity");
        }
        for (var method : read(pkg + "/entity/logic/HeroMovementState").methods) if (method.name.equals("afterAiStep")) {
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && (call.name.equals("setFloating") || call.name.equals("setNoGravity"))) {
                var argument = instruction.getPrevious();
                while (argument != null && argument.getOpcode() < 0) argument = argument.getPrevious();
                check(argument != null && argument.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1,
                        "Post-AI buoyancy cannot clear flight established by combat during the same tick");
            }
        }
        check(calls(pkg + "/network/PoemGesturePacket", "handle", pkg + "/combat/poem/StandalonePoemController", "requestGesture")
                || read(pkg + "/network/PoemGesturePacket").methods.stream().anyMatch(method -> {
                    for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                            && call.owner.equals(pkg + "/combat/poem/StandalonePoemController") && call.name.equals("requestGesture")) return true;
                    return false;
                }), "Server routes standalone gestures to authoritative collision");
        ClassNode renderer = read(pkg + "/client/render/StandalonePoemRenderer");
        for (String layer : List.of("HeroHeldItemLayer", "HeroEyesLayer", "HeroChargedLayer")) {
            boolean bypass = false;
            for (var method : renderer.methods) if (method.name.equals("renderLayer")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type && type.desc.equals(pkg + "/client/render/" + layer)) bypass = true;
                }
            }
            check(bypass, layer + " is not transformed twice");
        }
    }

    private static boolean calls(String type, String method, String owner, String name) throws Exception {
        for (var candidate : read(type).methods) if (candidate.name.equals(method)) {
            for (var instruction : candidate.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }

    private static ClassNode read(String type) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = HeroPoemAnimationChecks.class.getClassLoader().getResourceAsStream(type + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void matrices(Matrix4f[] actual, Matrix4f[] expected, String label) {
        check(actual.length == 20, "Complete rig retained");
        for (int i = 0; i < actual.length; i++) check(actual[i].equals(expected[i], .00002f), label + " joint " + i);
    }

    private static void mesh(ModelPart part, PoemSkinMesh.Part type, Matrix4f[] skin) {
        PoemSkinMesh mesh = PoemSkinMesh.of(part, type);
        check(mesh.faces.stream().anyMatch(face -> face.ancestry().size() == 2), "Lower segment geometry captured");
        List<Vector3f> vertices = new ArrayList<>();
        mesh.render(part, skin, 1, new PoseStack(), capture(vertices), 0, 0, -1);
        check(!vertices.isEmpty(), "Native segmented mesh emits vertices");
        for (Vector3f vertex : vertices) check(vertex.isFinite() && vertex.length() < 8, "Vertices stay finite and in model space");
        for (PoemSkinMesh.Face face : mesh.faces) for (PoemSkinMesh.Vertex vertex : face.vertices()) {
            Vector3f before = mesh.position(vertex, skin, PoemSkinMesh.current(part), 1);
            part.xRot = 2.4f; part.y = 35;
            Vector3f after = mesh.position(vertex, skin, PoemSkinMesh.current(part), 1);
            check(before.equals(after, .000001f), "Full native pose ignores legacy ModelPart angles");
        }
        ModelPart lower = part.getChild(type == PoemSkinMesh.Part.RIGHT_ARM ? (part.hasChild("right_arm_lower") ? "right_arm_lower" : "right_sleeve_lower")
                : type == PoemSkinMesh.Part.LEFT_ARM ? "left_arm_lower"
                : type == PoemSkinMesh.Part.RIGHT_LEG ? "right_leg_lower"
                : part.hasChild("left_leg_lower") ? "left_leg_lower" : "left_pants_lower");
        int full = vertices.size();
        lower.visible = false;
        vertices.clear();
        mesh.render(part, skin, 1, new PoseStack(), capture(vertices), 0, 0, -1);
        check(!vertices.isEmpty() && vertices.size() < full, "Hidden lower segments remain hidden");
    }

    private static VertexConsumer capture(List<Vector3f> vertices) {
        Vector3f point = new Vector3f();
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            switch (method.getName()) {
                case "vertex" -> point.set(((Number) args[0]).floatValue(), ((Number) args[1]).floatValue(), ((Number) args[2]).floatValue());
                case "endVertex" -> { vertices.add(new Vector3f(point)); return null; }
                case "defaultColor", "unsetDefaultColor" -> { return null; }
            }
            return proxy;
        });
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
