package com.whitecloud233.modid.herobrine_companion.compat.safety;

import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Preflight never defines classes belonging to optional mods. */
public final class CompatPreflight {
    public static final String MIXIN_PACKAGE = "com.whitecloud233.modid.herobrine_companion.compat.mixin.";
    private static final Map<String, String> VERSION_RANGES = Map.of(
            "accessories", "[1.0.0-beta21,1.1)",
            "armourers_workshop", "[3.0.0-beta.21,3.4)",
            "epicfight", "[20.14.1,20.15)",
            "simplehats", "[0.4,0.5),[1.20.1-0.3.1,1.20.2)",
            "waveycapes", "[1.9.2,2)",
            "wom", "[2.0.171]",
            "efn", "[3.2.6,3.5)",
            "epic_fight_avalon", "[0,)");
    private static final Map<String, List<String>> CLASSES = Map.of(
            "accessories", List.of("io.wispforest.accessories.api.AccessoriesCapability", "io.wispforest.accessories.api.AccessoriesContainer"),
            "armourers_workshop", List.of("moe.plushie.armourers_workshop.core.menu.SkinWardrobeMenu"),
            "epicfight", List.of("yesman.epicfight.api.animation.AnimationManager", "yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch", "yesman.epicfight.api.asset.AssetAccessor"),
            "simplehats", List.of("fonnymunkey.simplehats.SimpleHatsCommon"),
            "waveycapes", List.of("dev.tr7zw.waveycapes.WaveyCapesBase"),
            "wom", List.of("reascer.wom.gameasset.WOMAnimations"),
            "efn", List.of("com.hm.efn.gameasset.EFNAnimations"),
            "epic_fight_avalon", List.of());
    public record Patch(String integration, String mixin, String target, boolean clientOnly) { }
    public static final List<Patch> PATCHES = List.of(
            new Patch("accessories", "AccessoriesHeroEquipmentMixin", "io.wispforest.accessories.client.AccessoriesMenu", false),
            new Patch("armourers_workshop", "ArmourersWorkshopHeroWardrobeRangeMixin", "moe.plushie.armourers_workshop.core.menu.SkinWardrobeMenu", false),
            new Patch("epicfight", "epicfight.AnimationClipMixin", "yesman.epicfight.api.animation.AnimationClip", false),
            new Patch("epicfight", "epicfight.PoemAttackControlMixin", "yesman.epicfight.client.events.engine.ControlEngine", true),
            new Patch("efn", "epicfight.JsonAssetLoaderMixin", "yesman.epicfight.api.asset.JsonAssetLoader", false),
            new Patch("efn", "epicfight.EffekUnitsVfxMixin", "com.hm.efn.util.EffekUnits", false),
            new Patch("wom", "epicfight.HeroWomAnimationEventGuardMixin", "yesman.epicfight.api.animation.property.AnimationEvent", false),
            new Patch("wom", "epicfight.WomAntitheusLapseMixin", "reascer.wom.gameasset.WOMAnimations", false),
            new Patch("epic_fight_avalon", "epicfight.RenderAnimationItemMixin", "com.merlin204.avalon.entity.client.renderer.patch.item.RenderAnimationItem", true));
    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();
    private static final Map<String, ClassNode> BYTECODE = new ConcurrentHashMap<>();

    private CompatPreflight() { }

    public static String version(String id) {
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) return null;
        return mods.getMods().stream().filter(mod -> mod.getModId().equals(id))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
    }

    public static synchronized boolean available(String integration) {
        String id = CompatSafety.normalize(integration);
        if (!CompatSafety.allowed(id)) return false;
        if (CHECKED.contains(id)) return CompatSafety.allowed(id);
        String version = version(id);
        if (version == null) return false;
        if (List.of("wom", "efn", "epic_fight_avalon").contains(id) && !available("epicfight")) return false;
        if (CHECKED.add(id)) {
            String issue = inspect(id, version, FMLLoader.getDist().isClient(), CompatPreflight::bytecode);
            if (issue != null) CompatSafety.disable(id, issue, null);
        }
        return CompatSafety.allowed(id);
    }

    public static String inspect(String id, String version, boolean client, Function<String, ClassNode> reader) {
        String range = VERSION_RANGES.get(id);
        if (range == null) return "unknown integration";
        try {
            if (!VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version))) {
                return "unsupported version " + version + "; supported range " + range;
            }
            for (String name : CLASSES.getOrDefault(id, List.of())) {
                // Purely visual integrations must not resolve their client classes on a dedicated server.
                if (!client && (id.equals("simplehats") || id.equals("waveycapes"))) continue;
                if (reader.apply(name) == null) return "missing API class " + name;
            }
            for (Patch patch : PATCHES) {
                if (!patch.integration().equals(id) || patch.clientOnly() && !client) continue;
                String issue = MixinContract.mismatch(reader.apply(MIXIN_PACKAGE + patch.mixin()), reader.apply(patch.target()), reader);
                if (issue != null) return patch.mixin() + ": " + issue;
            }
            return null;
        } catch (RuntimeException | LinkageError exception) {
            CompatSafety.rethrowFatal(exception);
            return "cannot inspect optional API: " + exception.getClass().getSimpleName();
        } catch (org.apache.maven.artifact.versioning.InvalidVersionSpecificationException exception) {
            return "invalid supported version range";
        }
    }

    public static boolean allowsMixin(String target, String mixin) {
        String relative = mixin.startsWith(MIXIN_PACKAGE) ? mixin.substring(MIXIN_PACKAGE.length()) : mixin;
        Patch patch = PATCHES.stream().filter(p -> p.mixin().equals(relative) && p.target().equals(target)).findFirst().orElse(null);
        if (patch == null || !available(patch.integration())) return false;
        String issue = MixinContract.mismatch(bytecode(mixin), bytecode(target), CompatPreflight::bytecode);
        if (issue != null) {
            CompatSafety.disable(patch.integration(), relative + ": " + issue, null);
            return false;
        }
        return true;
    }

    public static String integrationForMixin(String mixin) {
        String relative = mixin.startsWith(MIXIN_PACKAGE) ? mixin.substring(MIXIN_PACKAGE.length()) : mixin;
        return PATCHES.stream().filter(p -> p.mixin().equals(relative)).map(Patch::integration).findFirst().orElse(null);
    }

    private static ClassNode bytecode(String name) {
        if (name == null || name.equals("java/lang/Object")) return null;
        String normalized = name.replace('/', '.');
        ClassNode cached = BYTECODE.get(normalized);
        if (cached != null) return cached;
        ClassNode node = null;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(normalized, false);
        } catch (Exception | LinkageError exception) {
            CompatSafety.rethrowFatal(exception);
        }
        if (node == null) {
            try (InputStream input = CompatPreflight.class.getClassLoader().getResourceAsStream(normalized.replace('.', '/') + ".class")) {
                if (input != null) {
                    node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            } catch (Exception | LinkageError exception) { CompatSafety.rethrowFatal(exception); }
        }
        if (node != null) BYTECODE.put(normalized, node);
        return node;
    }
}
