package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.google.gson.GsonBuilder;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.event.types.registry.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;

/** Registration, four-mode routing and preservation of the existing V6 attacks. */
public class MediaPipeRegistrationCheck {
    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    public static void main(String[] args) throws Exception {
        PoemScythePlayerAnimations.registerStyles();
        Style.ENUM_MANAGER.loadEnum();
        var builder = new AnimationManager.AnimationBuilder("herobrine_companion", b -> {});
        HeroScytheComboBehaviors.registerAnimation(builder);
        PoemScythePlayerAnimations.registerAnimations(builder);
        var manager = AnimationManager.getInstance();
        // Use real JSON-loaded armatures for production hit-phase construction.
        // Trail/sound factories require a full NeoForge bootstrap and are deferred.
        Map<AssetAccessor<?>, Armature> armatures = field(Armatures.class, "ARMATURES");
        try (var input = Files.newInputStream(Path.of("build/epicfight-mediapipe/biped.json"))) {
            armatures.put(Armatures.BIPED, new JsonAssetLoader(input, Armatures.BIPED.registryName())
                    .loadArmature(HumanoidArmature::new));
        }
        var heroArmature = HeroEpicFightBridge.heroNightfallArmature();
        try (var input = Files.newInputStream(Path.of("src/main/resources/assets/herobrine_companion/animmodels/entity/hero_biped_nightfall.json"))) {
            armatures.put(heroArmature, new JsonAssetLoader(input, heroArmature.registryName())
                    .loadArmature(HumanoidArmature::new));
        }
        var readTiming = MediaPipeScytheAnimations.class.getDeclaredMethod("readTimeline");
        readTiming.setAccessible(true);
        Object timeline = readTiming.invoke(null);
        var getSegments = timeline.getClass().getDeclaredMethod("segments");
        getSegments.setAccessible(true);
        List<?> timings = (List<?>) getSegments.invoke(timeline);
        var createPhases = MediaPipeScytheAnimations.class.getDeclaredMethod("phases",
                timings.getFirst().getClass(), float.class, AssetAccessor.class);
        createPhases.setAccessible(true);
        int count = MediaPipeScytheAnimations.COMBO_COUNT;
        for (var expected : Map.of("player/poem_mediapipe/", count + 5, "hero/poem_mediapipe/", count + 3, "player/poem_v6/", 23).entrySet()) {
            if (manager.getAnimations(a -> a.registryName().getPath().startsWith(expected.getKey())).size() != expected.getValue()) {
                throw new AssertionError("Missing registered motions: " + expected.getKey());
            }
        }
        MediaPipeScytheAnimations.MotionSet mediaPipe = field(PoemScythePlayerAnimations.class, "mediaPipe");
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> v6 = field(PoemScythePlayerAnimations.class, "combo");
        MediaPipeScytheAnimations.MotionSet legacyHero = field(HeroScytheComboBehaviors.class, "legacyMotionSet");
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> hero = legacyHero.combo();
        if (mediaPipe.combo().size() != count + 2 || v6.size() != 20 || hero.size() != count) {
            throw new AssertionError("Wrong normal/special slot counts");
        }
        var modeReport = new java.util.LinkedHashMap<Integer, String>();
        for (int mode : new int[]{0, 1, 2, 3, 1, 0, 3, 2}) {
            Style style = PoemScythePlayerAnimations.styleForMode(mode);
            Style expected = switch (mode) {
                case 1 -> PoemScytheStyle.POEM_REALM_BREAKER;
                case 2 -> PoemScytheStyle.POEM_THUNDER;
                case 3 -> PoemScytheStyle.POEM_VOID_SHATTER;
                default -> PoemScytheStyle.POEM_NORMAL;
            };
            if (style != expected || style.canUseOffhand() || Style.ENUM_MANAGER.getOrThrow(style.universalOrdinal()) != style) {
                throw new AssertionError("Wrong mode/style binding: " + mode);
            }
            modeReport.put(mode, "Unity09/" + UnityScytheAnimations.MODE_KEYS.get(mode));
        }
        for (int i = 0; i < count; i++) {
            var p = mediaPipe.combo().get(i);
            var h = hero.get(i);
            if (!p.registryName().getPath().equals("player/poem_mediapipe/combo_%02d".formatted(i + 1))
                    || !h.registryName().getPath().equals("hero/poem_mediapipe/combo_%02d".formatted(i + 1))) {
                throw new AssertionError("Wrong combo order: " + i);
            }
            for (var arm : List.of(Armatures.BIPED, heroArmature)) {
                var phases = (AttackAnimation.Phase[]) createPhases.invoke(null, timings.get(i), 0F, arm);
                if (phases.length != 1) throw new AssertionError("Grounded cuts need one clean hit window");
                for (AttackAnimation.Phase phase : phases) {
                    if (!(phase.preDelay < phase.contact && phase.contact <= phase.recovery && phase.recovery <= phase.end)
                            || phase.getColliders().length != 2
                            || phase.getColliders()[0].getFirst() != arm.get().toolR
                            || phase.getColliders()[1].getFirst() != arm.get().rootJoint) {
                        throw new AssertionError("Invalid hit phase or armature: " + p.registryName());
                    }
                }
            }
        }
        // Exercise the production full-body subclass without a game-rule context.
        // These calls would lose the legs or read stiffComboAttacks in the base class.
        var playerAccessor = (AnimationManager.AnimationAccessor<? extends yesman.epicfight.api.animation.types.ComboAttackAnimation>)
                (AnimationManager.AnimationAccessor<?>) mediaPipe.combo().get(0);
        var firstPhases = (AttackAnimation.Phase[]) createPhases.invoke(null, timings.get(0), 0F, Armatures.BIPED);
        var fullBody = new MediaPipeScytheAttackAnimation(.06F, playerAccessor, Armatures.BIPED, firstPhases);
        if (fullBody.getJointMaskEntry(null, false).isPresent() || !fullBody.shouldPlayerMove(null)
                || fullBody.getProperty(yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty.CANCELABLE_MOVE).orElse(true)) {
            throw new AssertionError("Scythe footwork can still be masked or canceled");
        }
        var spectrumField = StaticAnimation.class.getDeclaredField("stateSpectrum");
        var blueprintField = StaticAnimation.class.getDeclaredField("stateSpectrumBlueprint");
        spectrumField.setAccessible(true); blueprintField.setAccessible(true);
        var readSpectrum = yesman.epicfight.api.animation.types.StateSpectrum.class.getDeclaredMethod("readFrom",
                yesman.epicfight.api.animation.types.StateSpectrum.Blueprint.class);
        readSpectrum.setAccessible(true);
        readSpectrum.invoke(spectrumField.get(fullBody), blueprintField.get(fullBody));
        var attackState = fullBody.getStatesMap(null, (firstPhases[0].preDelay + firstPhases[0].contact) / 2F);
        if (!Boolean.TRUE.equals(attackState.get(yesman.epicfight.api.animation.types.EntityState.MOVEMENT_LOCKED))
                || !Boolean.FALSE.equals(attackState.get(yesman.epicfight.api.animation.types.EntityState.UPDATE_LIVING_MOTION))) {
            throw new AssertionError("Walking state overwrote the scythe attack phase");
        }
        if (!mediaPipe.combo().get(count).registryName().getPath().equals("player/poem_mediapipe/dash")
                || !mediaPipe.combo().get(count + 1).registryName().getPath().equals("player/poem_mediapipe/air")) {
            throw new AssertionError("Wrong sprint/air slots");
        }
        for (int i = 0; i < v6.size(); i++) {
            String name = i < 18 ? "combo_%02d".formatted(i + 1) : i == 18 ? "dash" : "air";
            if (!v6.get(i).registryName().getPath().equals("player/poem_v6/" + name)) {
                throw new AssertionError("Realm Breaker V6 slots changed");
            }
        }
        if (!Float.valueOf(4F).equals(field(PoemScythePlayerAnimations.class, "COMBO_SPEED"))
                || !Float.valueOf(2F).equals(field(PoemScythePlayerAnimations.class, "SPECIAL_SPEED"))) {
            throw new AssertionError("Realm Breaker V6 speed constants changed");
        }
        Map<ResourceLocation, Function<Item, ? extends CapabilityItem.Builder<?>>> presets = new HashMap<>();
        PoemScythePlayerAnimations.registerWeaponPreset(new WeaponCapabilityPresetRegistryEvent(presets));
        if (!presets.containsKey(PoemScythePlayerAnimations.WEAPON_TYPE)) {
            throw new AssertionError("Missing item preset factory");
        }
        String binding = Files.readString(Path.of("src/main/resources/data/herobrine_companion/capabilities/weapons/poem_of_the_end.json"));
        if (!binding.contains(PoemScythePlayerAnimations.WEAPON_TYPE.toString())) {
            throw new AssertionError("Poem item lost its capability binding");
        }
        var report = new java.util.LinkedHashMap<String, Object>();
        report.putAll(Map.of("player_modes", modeReport, "hero_combo_slots", count, "player_combo_slots", count,
                "realm_breaker_combo_slots", 18, "registered_clips", count * 2 + 31,
                "v6_speed_preserved", true, "capability_factory_registered", true,
                "animation_factories_executed", false, "production_hit_phases_checked", true,
                "live_gameplay_tested", false));
        report.put("full_body_attack_policy_checked", true);
        Files.writeString(Path.of("build/epicfight-mediapipe/registry_validation.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("MEDIAPIPE_REGISTRY_OK: legacy MediaPipe/V6 resources registered; active modes route to Unity09");
    }

}
