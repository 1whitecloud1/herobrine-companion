package com.whitecloud233.herobrine_companion.client.render;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Checks the compiled material wiring and shipped masks without initializing Minecraft or a GPU. */
public final class PoemGlowChecks {
    private static final String PACKAGE = PoemGlowChecks.class.getPackageName().replace('.', '/');
    private static final String ASSETS = "/assets/herobrine_companion/textures/item/";
    private static final String AUTO_LAYER = "software/bernie/geckolib/renderer/layer/AutoGlowingGeoLayer";
    private static int checks;

    public static void main(String[] args) throws Exception {
        geckoLayer();
        unlitMaterial();
        int emissivePixels = textureCoverage();
        outlineIsolation();
        deferredEmissive();
        frameLifecycle();
        System.out.println("PoemGlowChecks: " + checks + " checks passed; generated GeckoLib mask, deferred Iris passes, unlit shader, base lighting and "
                + emissivePixels + " emissive pixels");
    }

    private static void geckoLayer() throws Exception {
        ClassNode renderer = readClass(PACKAGE + "/GeckoLibPoemOfTheEndRenderer");
        List<ClassNode> layers = new ArrayList<>();
        for (var inner : renderer.innerClasses) {
            if (!inner.name.startsWith(renderer.name + "$")) continue;
            ClassNode type = readClass(inner.name);
            if (AUTO_LAYER.equals(type.superName)) layers.add(type);
        }
        check(layers.size() == 1, "Exactly one custom automatic glow layer is installed");
        ClassNode layer = layers.get(0);
        check(renderer.methods.stream().filter(m -> m.name.equals("<init>"))
                .anyMatch(m -> creates(m, layer.name)), "Renderer constructs the custom glow layer");
        MethodNode override = layer.methods.stream()
                .filter(m -> m.name.equals("getRenderType") && (m.access & Opcodes.ACC_BRIDGE) == 0)
                .findFirst().orElseThrow(() -> new AssertionError("Glow material must override GeckoLib's default"));
        int generation = callIndex(override, AUTO_LAYER, "getRenderType");
        int unlit = callIndex(override, PACKAGE + "/PoemWeaponRenderTypes", "glow");
        check(generation >= 0 && unlit > generation, "Generate GeckoLib's mask before returning the unlit material");
        MethodInsnNode call = (MethodInsnNode) override.instructions.get(generation);
        check(call.getOpcode() == Opcodes.INVOKESPECIAL, "Mask generation calls the superclass, not the override recursively");
        check(callIndex(override, "software/bernie/geckolib/cache/texture/GeoAbstractTexture", "appendToPath") > generation,
                "Use GeckoLib's generated texture naming convention");
        check(hasCall(override, "getTextureResource") && hasConstant(override, "_glowmask"),
                "The mask follows the current resource-pack texture rather than a fixed native texture");
        check(renderer.methods.stream().filter(m -> m.name.equals("renderByItem"))
                .anyMatch(m -> callIndex(m, "net/minecraft/client/renderer/RenderType", "entityTranslucent") >= 0),
                "Ordinary weapon pixels retain world lighting");
    }

    private static void unlitMaterial() throws Exception {
        ClassNode types = readClass(PACKAGE + "/PoemWeaponRenderTypes");
        MethodNode factory = types.methods.stream().filter(m -> hasField(m, "RENDERTYPE_EYES_SHADER"))
                .findFirst().orElseThrow(() -> new AssertionError("Glow must use the eyes shader"));
        for (String state : List.of("TRANSLUCENT_TRANSPARENCY", "NO_CULL", "LEQUAL_DEPTH_TEST", "COLOR_WRITE")) {
            check(hasField(factory, state), "Glow preserves render state " + state);
        }
        check(types.methods.stream().noneMatch(m -> hasField(m, "RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER")),
                "Do not reintroduce the directionally shaded emissive shader");
        check(types.methods.stream().anyMatch(m -> callIndex(m, "net/minecraft/Util", "memoize") >= 0),
                "Material instances are cached by texture");
        check(types.methods.stream().filter(m -> m.name.equals("glow") && m.desc.contains("ResourceLocation;"))
                .anyMatch(m -> hasField(m, "GLOW_TEXTURES") && hasCall(m, "apply")),
                "GeckoLib texture overload uses the cached unlit material factory");
        String vertex = text("/assets/minecraft/shaders/core/rendertype_eyes.vsh");
        String fragment = text("/assets/minecraft/shaders/core/rendertype_eyes.fsh");
        check(!vertex.contains("minecraft_mix_light") && !vertex.contains("light.glsl") && !vertex.contains("Light0_Direction"),
                "The actual eyes vertex shader contains no directional lighting");
        check(!vertex.contains("Sampler2") && !fragment.contains("Sampler2"), "Glow does not sample the world lightmap");
        check(vertex.contains("vertexColor = Color;") && fragment.contains("texture(Sampler0, texCoord0)"),
                "Original glow colors reach the fragment shader without normal attenuation");
    }

    private static int textureCoverage() throws Exception {
        BufferedImage source = image("poem_of_the_end_geo.png");
        BufferedImage glow = image("poem_of_the_end_native_glow.png");
        BufferedImage base = image("poem_of_the_end_native.png");
        int width = source.getWidth(), height = source.getHeight();
        check(glow.getWidth() == width && glow.getHeight() == height
                && base.getWidth() == width && base.getHeight() == height, "All passes use the original atlas dimensions");
        boolean[][] mask = new boolean[height][width];
        try (var input = new InputStreamReader(resource(ASSETS + "poem_of_the_end_geo.png.mcmeta"), StandardCharsets.UTF_8)) {
            var sections = JsonParser.parseReader(input).getAsJsonObject().getAsJsonObject("glowsections").getAsJsonArray("sections");
            for (var entry : sections) {
                var section = entry.getAsJsonObject();
                int x1 = section.get("x1").getAsInt(), y1 = section.get("y1").getAsInt();
                int x2 = section.get("x2").getAsInt(), y2 = section.get("y2").getAsInt();
                check(x1 >= 0 && y1 >= 0 && x2 >= x1 && y2 >= y1 && x2 < width && y2 < height,
                        "Glow rectangle stays inside the atlas");
                check(section.get("alpha").getAsInt() == 255, "Low-alpha emission markers render as solid glow");
                for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) mask[y][x] = true;
            }
        }
        int count = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = source.getRGB(x, y), alpha = pixel >>> 24;
            boolean emissive = alpha > 0 && alpha < 255;
            check(mask[y][x] == emissive, "GeckoLib mask matches original emission at " + x + "," + y);
            check(glow.getRGB(x, y) == (emissive ? (pixel | 0xFF000000) : 0),
                    "Native glow preserves the current emission colors and coverage at " + x + "," + y);
            check(base.getRGB(x, y) == (emissive ? (pixel & 0x00FFFFFF) : pixel),
                    "Native base preserves the current texture without double-rendering glow at " + x + "," + y);
            if (emissive) count++;
        }
        check(count > 0, "The current atlas retains emissive pixels");
        return count;
    }

    private static void outlineIsolation() throws Exception {
        ClassNode outline = readClass(PACKAGE + "/PoemOutlineEffect");
        check(outline.methods.stream().noneMatch(m -> m.name.equals("renderType")),
                "Outline is never submitted as a RenderType that Iris can wrap");
        check(outline.fields.stream().noneMatch(f -> f.desc.equals("Lcom/mojang/blaze3d/vertex/BufferBuilder;")),
                "One-shot BufferBuilder instances are not retained across frames");
        MethodNode composite = outline.methods.stream().filter(m -> m.name.equals("composite")).findFirst().orElseThrow();
        check(creates(composite, "com/mojang/blaze3d/vertex/BufferBuilder"), "Each composite starts a fresh quad builder");
        check(outline.methods.stream().filter(m -> m.name.equals("render"))
                .anyMatch(m -> creates(m, "com/mojang/blaze3d/vertex/BufferBuilder") && hasCall(m, "accept") && hasCall(m, "draw")),
                "Outline geometry is captured and drawn from its private buffer");
        for (String name : List.of("StandalonePoemRenderer", "PoemOfTheEndMeshRenderer")) {
            ClassNode renderer = readClass(PACKAGE + "/" + name);
            check(renderer.methods.stream().anyMatch(m -> callIndex(m, outline.name, "render") >= 0),
                    name + " renders its outline without the caller's material wrappers");
            check(renderer.methods.stream().noneMatch(m -> hasCall(m, "outlineGlow")), name + " does not enqueue a mask as world geometry");
        }
        ClassNode gecko = readClass(PACKAGE + "/GeckoLibPoemOfTheEndRenderer");
        boolean isolatedGecko = false;
        for (var inner : gecko.innerClasses) {
            if (!inner.name.startsWith(gecko.name + "$")) continue;
            ClassNode layer = readClass(inner.name);
            if (layer.methods.stream().anyMatch(m -> callIndex(m, outline.name, "render") >= 0)) {
                isolatedGecko = true;
                check(layer.methods.stream().noneMatch(m -> callIndex(m, "net/minecraft/client/renderer/MultiBufferSource", "getBuffer") >= 0),
                        "GeckoLib outline cannot be wrapped by the caller's MultiBufferSource");
            }
        }
        check(isolatedGecko, "GeckoLib also uses the isolated outline path");
        BufferedImage source = image("poem_of_the_end_geo.png"), mask = image("poem_of_the_end_outline.png");
        check(source.getWidth() == mask.getWidth() && source.getHeight() == mask.getHeight(), "Outline has the complete atlas dimensions");
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            check(source.getRGB(x, y) == mask.getRGB(x, y), "Independent outline includes ordinary and glowing pixels");
        }
    }

    private static void deferredEmissive() throws Exception {
        ClassNode effect = readClass(PACKAGE + "/PoemEmissiveEffect");
        MethodNode buffer = method(effect, "buffer");
        check(callIndex(buffer, PACKAGE + "/PoemShaderCompat", "shadowPass") >= 0,
                "Shadow passes cannot enqueue visible emissive geometry");
        check(creates(buffer, "com/mojang/blaze3d/vertex/BufferBuilder") && hasField(buffer, "DRAWS"),
                "Emissive vertices have a private frame-local queue");
        check(!hasCall(buffer, "auxiliaryFirstPersonBody"),
                "Local third-person players and first-person hands both use the deferred path");
        for (String forbidden : List.of("drawWithShader", "setShader", "bindWrite", "apply")) {
            check(!hasCall(buffer, forbidden), "Collecting emissive vertices must not call " + forbidden);
        }
        for (String capture : List.of("getProjectionMatrix", "getModelViewMatrix", "getShaderColor", "glGetDoublev", "glGetIntegerv")) {
            check(hasCall(buffer, capture), "Capture submission-time state with " + capture);
        }
        MethodNode finish = method(effect, "finishWorldFrame");
        for (String replay : List.of("setProjectionMatrix", "applyModelViewMatrix", "viewport", "glDepthRange", "setShaderColor", "depthMask", "restore", "discardWorldFrame")) {
            check(hasCall(finish, replay), "Replay and restore emissive state with " + replay);
        }
        check(effect.methods.stream().noneMatch(m -> callIndex(m, PACKAGE + "/PoemOutlineEffect", "enabled") >= 0),
                "Turning outlines off cannot disable weapon emission or ribbons");
        for (String name : List.of("StandalonePoemRenderer", "PoemOfTheEndMeshRenderer")) {
            check(readClass(PACKAGE + "/" + name).methods.stream()
                    .anyMatch(m -> callIndex(m, PACKAGE + "/PoemWeaponRenderTypes", "glowBuffer") >= 0),
                    name + " submits glow through the Iris-safe buffer");
        }
        ClassNode gecko = readClass(PACKAGE + "/GeckoLibPoemOfTheEndRenderer");
        boolean deferredGecko = false;
        for (var inner : gecko.innerClasses) {
            if (!inner.name.startsWith(gecko.name + "$")) continue;
            ClassNode layer = readClass(inner.name);
            if (AUTO_LAYER.equals(layer.superName)) {
                deferredGecko = layer.methods.stream().anyMatch(m -> m.name.equals("render")
                        && callIndex(m, PACKAGE + "/PoemWeaponRenderTypes", "glowBuffer") >= 0 && hasCall(m, "reRender"));
            }
        }
        check(deferredGecko, "Generated GeckoLib glow masks also use deferred geometry");
        MethodNode trail = method(readClass(PACKAGE + "/StandalonePoemTrailRenderer"), "render");
        check(hasField(trail, "TRAIL_BODY") && hasField(trail, "TRAIL_GLOW")
                        && callIndex(trail, effect.name, "buffer") >= 0,
                "Both blade-ribbon materials use the isolated emissive queue");
        String vertex = text("/assets/herobrine_companion/shaders/core/poem_emissive.vsh");
        String fragment = text("/assets/herobrine_companion/shaders/core/poem_emissive.fsh");
        check(!vertex.contains("minecraft_mix_light") && !vertex.contains("Sampler2") && !fragment.contains("Sampler2"),
                "The deferred shader is unlit and cannot sample terrain or lightmap textures");
        check(vertex.contains("ProjMat * ModelViewMat * vec4(Position, 1.0)"),
                "Replay applies the captured projection and model-view once");
        check(fragment.contains("if (OpaqueSurface != 0) color.a = 1.0;")
                        && fragment.indexOf("discard") < fragment.indexOf("color.a = 1.0"),
                "Emission markers become opaque without filling transparent atlas pixels or ribbon edges");
        var definition = JsonParser.parseString(text("/assets/herobrine_companion/shaders/core/poem_emissive.json")).getAsJsonObject();
        check(definition.get("vertex").getAsString().equals("herobrine_companion:poem_emissive")
                        && definition.get("fragment").getAsString().equals("herobrine_companion:poem_emissive"),
                "The registered emissive shader ships both stages");
        check(definition.getAsJsonArray("samplers").size() == 1
                        && definition.getAsJsonArray("samplers").get(0).getAsJsonObject().get("name").getAsString().equals("Sampler0"),
                "Emission samples only its own material texture");
    }

    private static void frameLifecycle() throws Exception {
        String outline = PACKAGE + "/PoemOutlineEffect", emissive = PACKAGE + "/PoemEmissiveEffect";
        ClassNode hook = readClass("com/whitecloud233/herobrine_companion/mixin/client/PoemOutlineFrameMixin");
        MethodNode begin = method(hook, "poem$beginOutlines");
        MethodNode finish = method(hook, "poem$compositeOutlines");
        MethodNode end = method(hook, "poem$endOutlines");
        for (String type : List.of(outline, emissive)) {
            check(callIndex(begin, type, "beginWorldFrame") >= 0, type + " starts collecting before world rendering");
            check(callIndex(finish, type, "finishWorldFrame") >= 0, type + " is replayed after Iris final");
            check(callIndex(end, type, "discardWorldFrame") >= 0, type + " discards stale geometry at frame end");
        }
        check(callIndex(finish, emissive, "finishWorldFrame") < callIndex(finish, outline, "finishWorldFrame"),
                "Opaque emission fills weapon depth before outlines are composited");
        var inject = finish.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var points = (List<org.objectweb.asm.tree.AnnotationNode>) annotationValue(inject, "at");
        var at = points.get(0);
        check("INVOKE".equals(annotationValue(at, "value"))
                        && ((String) annotationValue(at, "target")).startsWith("Lnet/minecraft/client/renderer/LevelRenderer;renderLevel("),
                "Replay is anchored outside LevelRenderer, not an unordered world-stage listener");
        check(((String[]) annotationValue(at, "shift"))[1].equals("AFTER"),
                "Replay follows every Iris LevelRenderer RETURN callback and precedes the hand depth clear");
        ClassNode effect = readClass(outline);
        MethodNode collect = effect.methods.stream().filter(m -> m.name.equals("render") && m.desc.contains("ResourceLocation;")).findFirst().orElseThrow();
        check(hasField(collect, "PENDING") && callIndex(collect, outline, "draw") > callIndex(collect, "java/util/List", "add"),
                "Outline capture queues meshes before the immediate draw path");
        check(method(effect, "finishWorldFrame").instructions.size() > 0
                        && hasField(method(effect, "finishWorldFrame"), "replayingWorld"),
                "Only replay writes the deferred world mask");
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode node, String name) {
        for (int i = 0; i < node.values.size(); i += 2) if (name.equals(node.values.get(i))) return node.values.get(i + 1);
        throw new AssertionError("Missing annotation value: " + name);
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean creates(MethodNode method, String type) {
        for (var instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode node && node.getOpcode() == Opcodes.NEW && node.desc.equals(type)) return true;
        }
        return false;
    }

    private static boolean hasField(MethodNode method, String name) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode node && node.name.equals(name)) return true;
        return false;
    }

    private static boolean hasConstant(MethodNode method, String value) {
        for (var instruction : method.instructions) if (instruction instanceof LdcInsnNode node && value.equals(node.cst)) return true;
        return false;
    }

    private static boolean hasCall(MethodNode method, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode node && node.name.equals(name)) return true;
        return false;
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode node && node.owner.equals(owner) && node.name.equals(name)) return i;
        }
        return -1;
    }

    private static ClassNode readClass(String name) throws Exception {
        try (var input = resource("/" + name + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static BufferedImage image(String name) throws Exception {
        try (var input = resource(ASSETS + name)) { return ImageIO.read(input); }
    }

    private static String text(String name) throws Exception {
        try (var input = resource(name)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static InputStream resource(String name) {
        InputStream input = PoemGlowChecks.class.getResourceAsStream(name);
        if (input == null) throw new AssertionError("Missing resource: " + name);
        return input;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
