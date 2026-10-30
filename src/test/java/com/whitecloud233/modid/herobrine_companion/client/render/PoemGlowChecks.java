package com.whitecloud233.modid.herobrine_companion.client.render;

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
    // Pinned count of semi-transparent pixels in poem_of_the_end_geo.png. Changing the art
    // means updating this number along with the derived native/native_glow/outline textures,
    // so a repaint or a lossy re-export cannot silently shrink the emissive blade.
    private static final int EMISSIVE_PIXELS = 269;
    private static int checks;

    public static void main(String[] args) throws Exception {
        geckoLayer();
        unlitMaterial();
        isolatedOutlines();
        textureCoverage();
        System.out.println("PoemGlowChecks: " + checks + " checks passed; isolated outlines, generated GeckoLib mask, unlit shader, base lighting and "
                + EMISSIVE_PIXELS + " emissive pixels");
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

    private static void isolatedOutlines() throws Exception {
        ClassNode effect = readClass(PACKAGE + "/PoemOutlineEffect");
        MethodNode buffer = effect.methods.stream().filter(m -> m.name.equals("buffer")).findFirst().orElseThrow();
        check(hasField(buffer, "collectingWorld") && hasField(buffer, "DRAWS"), "Shader world outlines use a private vertex queue");
        for (String capture : List.of("getProjectionMatrix", "getModelViewMatrix", "getVertexSorting", "viewport")) {
            check(hasCall(buffer, capture), "Capture outline " + capture + " at vertex submission");
        }
        for (String immediate : List.of("draw", "drawWithShader", "setupRenderState", "bindWrite", "setShader", "colorMask", "depthMask")) {
            check(!hasCall(buffer, immediate), "Collecting an outline must not change caller GPU state: " + immediate);
        }
        MethodNode finish = effect.methods.stream().filter(m -> m.name.equals("finishWorldFrame")).findFirst().orElseThrow();
        int masks = callIndex(finish, effect.name, "drawWorldMasks");
        check(masks >= 0 && callIndex(finish, effect.name, "composite") > masks, "Replay isolated masks before compositing their edges");
        for (String renderer : List.of("StandalonePoemRenderer", "PoemOfTheEndMeshRenderer", "GeckoLibPoemOfTheEndRenderer$2")) {
            ClassNode type = readClass(PACKAGE + "/" + renderer);
            check(type.methods.stream().anyMatch(m -> callIndex(m, PACKAGE + "/PoemWeaponRenderTypes", "outlineBuffer") >= 0),
                    renderer + " uses isolated outline submission");
        }
        check(readClass(PACKAGE + "/PoemOfTheEndFallbackRenderer").methods.stream()
                .anyMatch(m -> callIndex(m, effect.name, "buffer") >= 0), "Fallback atlas geometry uses isolated outline submission");
        MethodNode discard = effect.methods.stream().filter(m -> m.name.equals("discardWorldFrame")).findFirst().orElseThrow();
        check(hasCall(discard, "release") && hasCall(discard, "discard") && hasCall(discard, "clear"),
                "Discarded frames release unfinished outline geometry");
    }

    private static void textureCoverage() throws Exception {
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
            check((glow.getRGB(x, y) >>> 24) == (emissive ? 255 : 0), "Native glow covers the same pixels at " + x + "," + y);
            check((base.getRGB(x, y) >>> 24) == (emissive ? 0 : alpha), "Native base does not double-render glow at " + x + "," + y);
            if (emissive) count++;
        }
        check(count == EMISSIVE_PIXELS, "All " + EMISSIVE_PIXELS + " emissive atlas pixels remain covered");
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
