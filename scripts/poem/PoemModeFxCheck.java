package com.whitecloud233.modid.herobrine_companion.client.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningPacket;
import com.whitecloud233.modid.herobrine_companion.fight.particles.PaleLightningPillarParticle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.JumpInsnNode;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Real projection math plus compiled call paths, with both optional animation libraries removed. */
public final class PoemModeFxCheck {
    private static final String PACKAGE = PoemModeFxCheck.class.getPackageName().replace(".client.render", "").replace('.', '/');
    private static int checks;
    private static int projectionCases;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void close(float actual, float expected, float tolerance, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": " + actual + " vs " + expected);
    }

    public static void main(String[] args) throws Exception {
        var loader = PoemModeFxCheck.class.getClassLoader();
        check(loader.getResource("yesman/epicfight/main/EpicFightMod.class") == null, "Epic Fight absent from test runtime");
        check(loader.getResource("software/bernie/geckolib/GeckoLib.class") == null, "GeckoLib absent from test runtime");
        projection();
        lifetime();
        wire();
        lightningTiming();
        combatHooks();
        hooks();
        shaderResources();
        Path report = Path.of("build/poem_mode_fx/java_validation.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", "passed", "checks", checks, "projection_cases", projectionCases,
                "lightning_profiles_round_trip", true, "visual_only_rifts_use_new_shader", true,
                "melee_and_input_guards_checked", true, "immediate_first_frame_checked", true,
                "epic_fight_on_runtime_classpath", false, "geckolib_on_runtime_classpath", false,
                "live_gameplay_tested", false)));
        System.out.println("POEM_MODE_FX_OK: " + checks + " checks; " + projectionCases
                + " projection cases; both lightning profiles/first-frame timing; melee/input guards; renderer/post/depth wiring");
    }

    private static void projection() {
        int[][] sizes = {{1920, 1080}, {1280, 720}, {800, 800}, {3440, 1440}, {720, 1280}};
        for (int[] size : sizes) for (float fov : new float[]{30, 70, 110}) {
            float aspect = (float) size[0] / size[1];
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, .05F, 256);
            Matrix4f projectionBefore = new Matrix4f(projection);
            for (float distance : new float[]{2, 5, 16, 64}) for (float degrees : new float[]{-180, -90, -37, 0, 33, 90, 179}) {
                float angle = (float) Math.toRadians(degrees);
                Matrix4f viewPose = new Matrix4f().translation(.15F, .10F, -distance).rotateZ(angle);
                Matrix4f before = new Matrix4f(viewPose);
                var r = VoidRiftProjection.project(projection, viewPose, size[0], size[1], 8, 31, .9F);
                check(r != null, "Front-facing visible rift must project");
                float f = (float) (1 / Math.tan(Math.toRadians(fov) * .5));
                close(r.u(), .5F + .15F * f / aspect / distance * .5F, .000002F, "Horizontal perspective");
                close(r.v(), .5F + .10F * f / distance * .5F, .000002F, "Bottom-up vertical perspective");
                close(r.radius(), Math.min(.42F, VoidRiftProjection.HALF_LENGTH * f / distance * .5F), .000002F,
                        "Lens remains round at every aspect ratio and shrinks with distance");
                close((float) Math.sin(r.angle() - angle), 0, .00004F, "Billboard rotation preserved");
                close(r.depth(), distance, .00001F, "Linear view depth");
                close(r.strength(), 1, .000001F, "Full-strength middle of lifetime");
                check(projection.equals(projectionBefore) && viewPose.equals(before), "Projection must not mutate shared render matrices");
                projectionCases++;
            }
        }
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9, .05F, 256);
        for (float yaw : new float[]{-.8F, 0, 1.8F}) for (float pitch : new float[]{-.7F, 0, .65F}) {
            Matrix4f camera = new Matrix4f().rotateXYZ(pitch, yaw, .12F);
            Matrix4f inverseCamera = camera.invert(new Matrix4f());
            Vector3f center = inverseCamera.transformPosition(new Vector3f(.8F, .2F, -7));
            Matrix4f model = new Matrix4f().translation(center).mul(inverseCamera).rotateZ(.5F);
            Matrix4f neoView = new Matrix4f(camera).mul(model);
            Matrix4f forgeView = new Matrix4f().mul(new Matrix4f(camera).mul(model));
            Matrix4f bobbed = new Matrix4f(projection).translate(.03F, -.02F, 0).rotateX(.017F).rotateZ(-.012F);
            var neo = VoidRiftProjection.project(bobbed, neoView, 1920, 1080, 5, 31, 0);
            var forge = VoidRiftProjection.project(bobbed, forgeView, 1920, 1080, 5, 24, 0);
            check(neo != null && neo.equals(forge), "Both view-matrix conventions agree with camera rotation/bobbing");
            Vector4f clip = bobbed.transform(neoView.transform(new Vector4f(0, 0, 0, 1)));
            float depth = clip.z / clip.w * .5F + .5F;
            Vector4f restored = bobbed.invert(new Matrix4f()).transform(new Vector4f(neo.u() * 2 - 1, neo.v() * 2 - 1, depth * 2 - 1, 1));
            close(-restored.z / restored.w, neo.depth(), .0002F, "Shader unprojection recovers billboard depth with camera bob");
            projectionCases++;
        }
        check(project(projection, 0, 0, 2) == null, "Rift behind the camera is culled");
        check(project(projection, 0, 0, -260) == null, "Rift past the far plane is culled");
        check(project(projection, 0, 0, -.04F) == null, "Rift at the camera/near plane is culled");
        check(project(projection, 200, 0, -5) == null, "Wholly offscreen rift is culled");
        check(project(projection, Float.NaN, 0, -5) == null, "Invalid transforms cannot reach uniforms");
        var near = project(projection, 0, 0, -.12F);
        check(near != null && near.radius() <= .42F && near.strength() < .5F, "Near-camera lens is bounded and faded");
        float halfScreenAtFive = 5 / projection.m00();
        var edge = project(projection, -halfScreenAtFive - .1F, 0, -5);
        check(edge != null && edge.u() < 0, "Partly offscreen lens still renders its visible edge");
    }

    private static VoidRiftProjection.Projected project(Matrix4f projection, float x, float y, float z) {
        return VoidRiftProjection.project(projection, new Matrix4f().translation(x, y, z), 1920, 1080, 8, 31, 0);
    }

    private static void lifetime() {
        for (float life : new float[]{24, 31}) {
            close(VoidRiftProjection.envelope(0, life), 0, 0, "Closed at spawn");
            close(VoidRiftProjection.envelope(life, life), 0, 0, "Closed at expiry");
            close(VoidRiftProjection.envelope(life + 20, life), 0, 0, "Expired entities cannot leave ghost lenses");
            close(VoidRiftProjection.envelope(-2, life), 0, 0, "Negative age is closed");
            float previous = 0;
            for (int i = 0; i <= (int) (life * 20); i++) {
                float age = i / 20F;
                float strength = VoidRiftProjection.envelope(age, life);
                check(strength >= 0 && strength <= 1 && Float.isFinite(strength), "Bounded lifetime envelope");
                if (age <= 2) check(strength >= previous, "Opening is monotonic");
                if (age > life - 6) check(strength <= previous, "Closing is monotonic");
                previous = strength;
            }
        }
        close(VoidRiftProjection.envelope(Float.NaN, 31), 0, 0, "NaN age cannot reach shader");
        close(VoidRiftProjection.envelope(3, 0), 0, 0, "Invalid lifetime is closed");
    }

    private static void wire() {
        check(!new PaleLightningPacket(0, 0, 0, 6).immediate, "Challenge constructor retains delayed telegraph");
        for (boolean immediate : new boolean[]{false, true}) {
            for (double x : new double[]{-29999990.5, 0, 12345.25}) {
                PaleLightningPacket packet = new PaleLightningPacket(x, 81.125, -19.5, 6, immediate);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.encode(buffer);
                    check(buffer.readableBytes() == 29, "Three doubles, width float and immediate boolean occupy 29 bytes");
                    PaleLightningPacket decoded = new PaleLightningPacket(buffer);
                    check(decoded.x == x && decoded.y == 81.125 && decoded.z == -19.5 && decoded.width == 6,
                            "Lightning coordinates/width round-trip");
                    check(decoded.immediate == immediate, "Both lightning profiles round-trip");
                    check(buffer.readableBytes() == 0, "Packet fully consumed");
                } finally { buffer.release(); }
            }
        }
    }

    private static void lightningTiming() {
        for (float age : new float[]{0, .001F, .5F, 1, 5, 9}) {
            close(PaleLightningPillarParticle.fallProgress(true, age), 1, 0,
                    "Immediate profile renders the complete bolt from its first frame");
        }
        close(PaleLightningPillarParticle.fallProgress(false, 0), 0, 0, "Challenge starts with a telegraph");
        close(PaleLightningPillarParticle.fallProgress(false, 30), 0, 0, "Challenge delay preserved");
        close(PaleLightningPillarParticle.fallProgress(false, 32.5F), .5F, 0, "Challenge fall duration preserved");
        close(PaleLightningPillarParticle.fallProgress(false, 35), 1, 0, "Challenge bolt completes after delay");
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static long countCalls(ClassNode node, String methodName, String calledName) {
        return node.methods.stream().filter(m -> methodName == null || m.name.equals(methodName))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .filter(i -> i instanceof MethodInsnNode m && m.name.equals(calledName)).count();
    }

    private static void combatHooks() throws Exception {
        var handler = read("event/VoidRiftOnHurtHandler");
        for (String guard : new String[]{"isCanceled", "isFinite", "getDirectEntity", "getMode", "isOnCooldown"}) {
            check(countCalls(handler, "onLivingHurt", guard) > 0, "Authoritative melee guard retained: " + guard);
        }
        check(has(handler, "onLivingHurt", i -> i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.INSTANCEOF &&
                t.desc.equals("net/minecraft/server/level/ServerLevel")), "Rifts are server-authoritative");
        check(has(handler, "onLivingHurt", i -> i instanceof MethodInsnNode m && m.name.equals("getMode") &&
                m.getNext().getOpcode() == Opcodes.ICONST_3 && m.getNext().getNext().getOpcode() == Opcodes.IF_ICMPEQ),
                "Only void-shatter mode passes the trigger gate");
        for (String type : new String[]{"PLAYER_ATTACK", "MOB_ATTACK", "MOB_ATTACK_NO_AGGRO"}) {
            check(has(handler, "onLivingHurt", i -> i instanceof FieldInsnNode f && f.name.equals(type)),
                    "Direct melee allowlist retains " + type);
        }
        var hurt = handler.methods.stream().filter(m -> m.name.equals("onLivingHurt")).findFirst().orElseThrow();
        int reserved = -1, spawned = -1;
        for (int i = 0; i < hurt.instructions.size(); i++) {
            if (hurt.instructions.get(i) instanceof MethodInsnNode m) {
                if (m.name.equals("putLong")) reserved = i;
                if (m.name.equals("spawnRift")) spawned = i;
            }
        }
        check(reserved >= 0 && spawned > reserved, "Reserve attacker cooldown before spawn to suppress sweep/reentrant duplicates");
        check(countCalls(handler, "onLivingHurt", "spawnRift") == 1, "One authoritative spawn call per accepted hit");
        var rift = read("entity/projectile/VoidRiftEntity");
        check(has(rift, "tick", i -> i instanceof MethodInsnNode m && m.owner.equals("net/minecraft/world/damagesource/DamageSource") &&
                m.name.equals("<init>") && m.desc.equals("(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V")),
                "Rift pulse uses separate direct source and credited owner, never direct playerAttack damage");
        check(!has(read("item/PoemOfTheEndItem"), null, i -> i instanceof TypeInsnNode t &&
                t.getOpcode() == Opcodes.NEW && t.desc.endsWith("/VoidRiftEntity")), "Item callbacks cannot spawn duplicate rifts");
        var input = read("client/event/ClientForgeEvents");
        check(countCalls(input, "handlePoemRapidFire", "invoke") == 1, "Single rapid-fire attack invocation");
        check(countCalls(input, "handlePoemRapidFire", "swing") == 0, "startAttack owns its swing without a duplicate packet");
        for (String guard : new String[]{"getMode", "isMouseGrabbed", "isUsingItem"}) {
            check(countCalls(input, "handlePoemRapidFire", guard) == 1, "Rapid-fire input guard retained: " + guard);
        }
        check(countCalls(read("client/event/ClientEvents$ForgeClientEvents"), "onClientTick", "invoke") == 0,
                "Renderer tick cannot auto-attack a second time");
        var particle = read("fight/particles/PaleLightningPillarParticle");
        check(countCalls(particle, "render", "fallProgress") == 1, "Renderer uses verified first-frame timing");
        long noCircleGuards = particle.methods.stream().filter(m -> m.name.equals("render"))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .filter(i -> i instanceof FieldInsnNode f && f.name.equals("immediate") &&
                        i.getNext() instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFNE).count();
        check(noCircleGuards == 2, "Immediate profile skips both warning ring and ground dissipation");
        check(has(read("client/network/ClientFxHandler"), "handlePaleLightning", i -> i instanceof FieldInsnNode f &&
                f.owner.endsWith("/PaleLightningPacket") && f.name.equals("immediate")), "Immediate flag reaches client particle");
    }

    private static ClassNode read(String relative) throws Exception {
        try (var input = PoemModeFxCheck.class.getClassLoader().getResourceAsStream(PACKAGE + '/' + relative + ".class")) {
            check(input != null, "Compiled class exists: " + relative);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static boolean has(ClassNode node, String methodName, Predicate<AbstractInsnNode> predicate) {
        for (MethodNode method : node.methods) {
            if (methodName != null && !method.name.equals(methodName)) continue;
            for (var instruction : method.instructions) if (predicate.test(instruction)) return true;
        }
        return false;
    }

    private static void hooks() throws Exception {
        var item = read("item/PoemOfTheEndItem");
        check(has(item, "performThunderCall", i -> i instanceof MethodInsnNode m && m.owner.equals(PACKAGE + "/network/PaleLightningPacket") && m.name.equals("<init>") &&
                m.desc.equals("(DDDFZ)V") && previousCode(i).getOpcode() == Opcodes.ICONST_1),
                "Thunder Call explicitly selects immediate no-circle lightning");
        check(has(item, "performThunderCall", i -> i instanceof MethodInsnNode m && m.owner.equals(PACKAGE + "/network/PacketHandler") && m.name.equals("sendToTracking")),
                "Thunder Call reaches observing clients");
        check(!has(item, "performThunderCall", i -> (i instanceof FieldInsnNode f && f.name.equals("LIGHTNING_BOLT")) ||
                        (i instanceof MethodInsnNode m && m.owner.endsWith("/LightningBolt"))), "Thunder Call no longer spawns vanilla lightning");
        String challenge = PACKAGE.contains("/modid/") ? "fight/goal/HeroPhase1Goal" : "client/fight/goal/HeroPhase1Goal";
        check(has(read(challenge), null, i -> i instanceof MethodInsnNode m && m.owner.equals(PACKAGE + "/network/PaleLightningPacket")),
                "The referenced packet is also used by Herobrine challenge mode");
        check(has(read("client/network/ClientFxHandler"), "handlePaleLightning", i -> i instanceof TypeInsnNode t && t.desc.endsWith("/PaleLightningPillarParticle")),
                "Shared client handler creates the same pillar particle");
        var renderer = read("client/render/VoidRiftRenderer");
        check(has(renderer, "render", i -> i instanceof MethodInsnNode m && m.owner.equals(PACKAGE + "/client/render/VoidRiftPostEffect") && m.name.equals("submit")),
                "Entity renderer submits to the new lens");
        check(!has(renderer, "render", i -> i instanceof MethodInsnNode m && m.name.equals("isVisualOnly")),
                "Visual-only hit rifts also use the new lens while retaining server-side damage suppression");
        var post = read("client/render/VoidRiftPostEffect");
        check(has(post, "onWorldRender", i -> i instanceof FieldInsnNode f && f.name.equals("AFTER_LEVEL")),
                "Lens renders after the world, before hands/HUD");
        check(has(post, "render", i -> i instanceof MethodInsnNode m && m.name.equals("copyDepthFrom")), "World depth is snapshotted/restored");
        check(has(post, "ensureChain", i -> i instanceof MethodInsnNode m && m.name.equals("resize")), "Post targets resize with the scene");
        check(has(read("client/render/VoidRiftPostEffect$Resources"), "onReloadRegistration", i -> i instanceof MethodInsnNode m && m.name.equals("registerReloadListener")),
                "Resource reload closes and resets the shader");
        check(PoemModeFxCheck.class.getClassLoader().getResource(PACKAGE + "/client/render/VoidRiftDistortionHandler.class") == null,
                "Obsolete textured distortion renderer cannot render a duplicate effect");
    }

    private static void shaderResources() throws Exception {
        Path assets = Path.of("src/main/resources/assets/herobrine_companion/shaders");
        var post = JsonParser.parseString(Files.readString(assets.resolve("post/void_rift_lens.json"))).getAsJsonObject();
        var passes = post.getAsJsonArray("passes");
        check(passes.size() == 2, "Single lens pass and final copy");
        check(passes.get(1).getAsJsonObject().get("name").getAsString().equals("herobrine_companion:void_rift_copy"),
                "Final pass must not alpha-blend sky/horizon RGB against the cleared black target");
        var copy = JsonParser.parseString(Files.readString(assets.resolve("program/void_rift_copy.json"))).getAsJsonObject();
        check(copy.getAsJsonObject("blend").get("srcrgb").getAsString().equals("one") &&
                copy.getAsJsonObject("blend").get("dstrgb").getAsString().equals("zero"), "Scene copy overwrites color regardless of world-buffer alpha");
        var lens = passes.get(0).getAsJsonObject();
        check(lens.get("intarget").getAsString().equals("minecraft:main"), "Lens samples rendered scene color");
        check(lens.getAsJsonArray("auxtargets").get(0).getAsJsonObject().get("id").getAsString().equals("rift_depth:depth"),
                "Auxiliary sampler binds saved world depth");
        var program = JsonParser.parseString(Files.readString(assets.resolve("program/void_rift_lens.json"))).getAsJsonObject();
        check(program.getAsJsonArray("samplers").size() == 2, "Procedural tear has only scene/depth samplers");
        var uniforms = program.getAsJsonArray("uniforms");
        for (int i = 0; i < VoidRiftProjection.MAX_RIFTS; i++) {
            final String shape = "Rift" + i, state = "RiftState" + i;
            check(uniforms.asList().stream().anyMatch(e -> e.getAsJsonObject().get("name").getAsString().equals(shape)), "Shape uniform for slot " + i);
            check(uniforms.asList().stream().anyMatch(e -> e.getAsJsonObject().get("name").getAsString().equals(state)), "State uniform for slot " + i);
        }
    }
}
