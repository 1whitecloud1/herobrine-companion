package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.Gson;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

/** Four native Unity modes; the existing V6/MediaPipe library remains available. */
final class PoemScythePlayerAnimations {
    static final ResourceLocation WEAPON_TYPE = ResourceLocation.parse("herobrine_companion:poem_scythe_v6");
    private static final String PREFIX = "player/poem_v6/";
    // Preserve this 1.20.1 port's V6 rates: 4x for the 18-step combo and
    // 4/3x for sprint/air, including the existing recovery window below.
    private static final float COMBO_SPEED = 4F;
    private static final float SPECIAL_SPEED = COMBO_SPEED / 3F;
    // Real seconds of input slack left between the recovery window and the end
    // of a segment, so the next attack never opens before the blade's contact
    // interval finishes. Deliberately NOT multiplied by the playback rate: the
    // animation end it is measured against is compressed by that rate too, so a
    // scaled window would push recovery past the end and make the timeline
    // validator reject the whole moveset.
    private static final float COMBO_LINK_WINDOW_SECONDS = .15F;
    // Actual blade and ring bounds in Tool_R space. The animated socket carries
    // the V6 weapon scale and changing grip; rendering and collision share it.
    private static final Collider BLADE = new MultiOBBCollider(3,
            .190283D, 1.481955D, .747754D, -.026746D, -.473613D, -2.071904D);
    // The blade-only box leaves a gap beside the grip and can swing away from
    // nearby targets. Root space keeps this sweep facing the player's attack
    // direction, covering point-blank targets through 4.5 blocks in front.
    private static final Collider FRONT_SWEEP = new MultiOBBCollider(3,
            1.25D, 1.25D, 2.35D, 0D, .85D, -2.15D);
    private static List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combo = List.of();
    private static AnimationManager.AnimationAccessor<StaticAnimation> ready;
    private static AnimationManager.AnimationAccessor<StaticAnimation> hold;
    private static MediaPipeScytheAnimations.MotionSet mediaPipe;
    private static List<UnityScytheAnimations.MotionSet> unityModes = List.of();

    private PoemScythePlayerAnimations() { }

    static void registerStyles() {
        Style.ENUM_MANAGER.registerEnumCls("herobrine_companion", PoemScytheStyle.class);
    }

    static Style styleForMode(int mode) {
        return switch (mode) {
            case PoemOfTheEndItem.MODE_REALM_BREAKER -> PoemScytheStyle.POEM_REALM_BREAKER;
            case PoemOfTheEndItem.MODE_THUNDER_CALL -> PoemScytheStyle.POEM_THUNDER;
            case PoemOfTheEndItem.MODE_VOID_SHATTER -> PoemScytheStyle.POEM_VOID_SHATTER;
            default -> PoemScytheStyle.POEM_NORMAL;
        };
    }

    private static Style styleForStack(ItemStack stack) {
        return styleForMode(stack.getItem() instanceof PoemOfTheEndItem poem
                ? poem.getMode(stack) : PoemOfTheEndItem.MODE_NORMAL);
    }

    static void registerAnimations(AnimationManager.AnimationBuilder builder) {
        unityModes = UnityScytheAnimations.registerPlayer(builder);
        mediaPipe = MediaPipeScytheAnimations.registerPlayer(builder);
        Timeline timeline = readTimeline();
        var registered = new ArrayList<AnimationManager.AnimationAccessor<? extends AttackAnimation>>();
        for (Timing t : timeline.segments) {
            var accessor = builder.<BasicAttackAnimation>nextAccessor(PREFIX + t.name, a -> segment(t, a));
            accessor.get();
            registered.add(accessor);
        }
        Timing dash = timeline.specials.get(0);
        var dashAccessor = builder.<DashAttackAnimation>nextAccessor(PREFIX + dash.name, a -> configure(
                new DashAttackAnimation(.10F, a, Armatures.BIPED, phase(dash, 0F, true)) {
                    @Override
                    public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation animation) {
                        return SPECIAL_SPEED;
                    }
                }));
        dashAccessor.get();
        Timing air = timeline.specials.get(1);
        var airAccessor = builder.<AirSlashAnimation>nextAccessor(PREFIX + air.name, a -> configure(
                new AirSlashAnimation(.08F, a, Armatures.BIPED, phase(air, 0F, true)) {
                    @Override
                    public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation animation) {
                        return SPECIAL_SPEED;
                    }
                }));
        airAccessor.get();
        // Epic Fight reserves the final two entries for sprint and air attacks.
        registered.add(dashAccessor);
        registered.add(airAccessor);
        combo = List.copyOf(registered);
        ready = builder.nextAccessor(PREFIX + "ready", a -> new StaticAnimation(.2F, true, a, Armatures.BIPED));
        hold = builder.nextAccessor(PREFIX + "hold", a -> new StaticAnimation(.2F, true, a, Armatures.BIPED));
        ready.get();
        hold.get();
        // Keep the complete performance available to the EF animation viewer.
        builder.<AttackAnimation>nextAccessor(PREFIX + "full", a -> {
            var phases = timeline.segments.stream()
                    .map(t -> phase(t, t.start, false)).toArray(AttackAnimation.Phase[]::new);
            return configure(new AttackAnimation(.15F, a, Armatures.BIPED, phases));
        }).get();
    }

    static void registerWeaponPreset(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(WEAPON_TYPE, item -> createCapabilityBuilder());
    }

    // This pinned EF build retains these public builder methods. A fresh preset
    // avoids inheriting the sword preset's overriding moveset.
    @SuppressWarnings({"deprecation", "removal", "unchecked"})
    private static WeaponCapability.Builder createCapabilityBuilder() {
        if (unityModes.size() != UnityScytheAnimations.MODE_COUNT || combo.size() != 20 || ready == null || hold == null || mediaPipe == null
                || mediaPipe.combo().size() != MediaPipeScytheAnimations.COMBO_COUNT + 2) {
            throw new IllegalStateException("All four Poem animation sets must be registered before item capabilities");
        }
        var capability = WeaponCapability.builder()
                .category(CapabilityItem.WeaponCategories.GREATSWORD)
                .styleProvider(patch -> styleForStack(patch.getOriginal().getMainHandItem()))
                .canBePlacedOffhand(false).collider(BLADE)
                .swingSound(EpicFightSounds.WHOOSH_BIG.get()).hitSound(EpicFightSounds.BLADE_HIT.get())
                .newStyleCombo(CapabilityItem.Styles.TWO_HAND, combo.toArray(AnimationManager.AnimationAccessor[]::new))
                .newStyleCombo(PoemScytheStyle.POEM_TWO_HAND, mediaPipe.combo().toArray(AnimationManager.AnimationAccessor[]::new))
                .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.IDLE, ready)
                .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.WALK, hold)
                .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.RUN, hold)
                .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.SNEAK, hold)
                .livingMotionModifier(PoemScytheStyle.POEM_TWO_HAND, LivingMotions.IDLE, mediaPipe.ready())
                .livingMotionModifier(PoemScytheStyle.POEM_TWO_HAND, LivingMotions.WALK, mediaPipe.hold())
                .livingMotionModifier(PoemScytheStyle.POEM_TWO_HAND, LivingMotions.RUN, mediaPipe.hold())
                .livingMotionModifier(PoemScytheStyle.POEM_TWO_HAND, LivingMotions.SNEAK, mediaPipe.hold());
        for (UnityScytheAnimations.MotionSet set : unityModes) {
            Style style = styleForMode(set.mode());
            capability.newStyleCombo(style, set.attacks().toArray(AnimationManager.AnimationAccessor[]::new))
                    .livingMotionModifier(style, LivingMotions.IDLE, set.ready())
                    .livingMotionModifier(style, LivingMotions.WALK, set.hold())
                    .livingMotionModifier(style, LivingMotions.RUN, set.hold())
                    .livingMotionModifier(style, LivingMotions.SNEAK, set.hold());
        }
        return capability;
    }

    /** Builds one combo segment. {@code t} is a parameter, so the lambda captures it safely. */
    private static BasicAttackAnimation segment(Timing t, AnimationManager.AnimationAccessor<BasicAttackAnimation> accessor) {
        return configure(new BasicAttackAnimation(.10F, accessor, Armatures.BIPED,
                phase(t, 0F, true)) {
            @Override
            public float getPlaySpeed(LivingEntityPatch<?> patch, DynamicAnimation animation) {
                return COMBO_SPEED;
            }
        });
    }

    private static AttackAnimation.Phase phase(Timing t, float start, boolean clampRecovery) {
        // Never open the next attack before the blade's contact interval ends:
        // leave the last COMBO_LINK_WINDOW_SECONDS of animation time as input
        // slack. The window is applied in animation time, never in real time,
        // because the clip's own end is compressed by the playback rate too.
        // The clamp only applies to the per-segment clips: the combined `full`
        // viewer clip keeps the authored recovery so the segments butt together.
        float recovery = clampRecovery
                ? Math.min(t.recovery, Math.max(t.contact, t.duration - COMBO_LINK_WINDOW_SECONDS))
                : t.recovery;
        // Both colliders share one phase: EF deduplicates targets and applies
        // the same hit window, strike limit and already-hit tracking to them.
        return new AttackAnimation.Phase(start, start + t.hit_start, start + t.hit_start, start + t.contact,
                start + recovery, start + t.duration, InteractionHand.MAIN_HAND,
                AttackAnimation.JointColliderPair.of(Armatures.BIPED.get().toolR, BLADE),
                AttackAnimation.JointColliderPair.of(Armatures.BIPED.get().rootJoint, FRONT_SWEEP));
    }

    private static <T extends AttackAnimation> T configure(T animation) {
        // Player attacks override getPlaySpeed so the player clock, root motion,
        // phase/trail times AND collider sampling all use the same speed multiplier.
        animation.addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1F);
        animation.addProperty(AnimationProperty.AttackAnimationProperty.ATTACK_SPEED_FACTOR, 0F);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.RAW_COORD);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_TICK, MoveCoordFunctions.RAW_COORD);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.MOVE_VERTICAL, false);
        animation.addProperty(AnimationProperty.ActionAnimationProperty.MOVE_ON_LINK, false);
        animation.addProperty(AnimationProperty.StaticAnimationProperty.FIXED_HEAD_ROTATION, true);
        // Preserve the authored relationship between the torso, both hands and blade.
        animation.addProperty(AnimationProperty.StaticAnimationProperty.POSE_MODIFIER,
                (self, pose, patch, time, partialTicks) -> { });
        List<TrailInfo> trails = new ArrayList<>();
        for (AttackAnimation.Phase p : animation.phases) {
            // Trail times use the same animation clock as the clip and collider.
            trails.add(TrailInfo.builder().joint("Tool_R").time(p.preDelay, p.contact)
                    .startPos(new Vec3(-.026746D, -1.835568D, -2.071904D))
                    .endPos(new Vec3(-.026746D, .888342D, -2.071904D))
                    .itemSkinHand(InteractionHand.MAIN_HAND).create());
        }
        animation.addProperty(ClientAnimationProperties.TRAIL_EFFECT, List.copyOf(trails));
        return animation;
    }

    private static Timeline readTimeline() {
        try (var input = Objects.requireNonNull(PoemScythePlayerAnimations.class.getResourceAsStream(
                "/assets/herobrine_companion/epicfight/poem_v6_timing.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Timeline t = new Gson().fromJson(reader, Timeline.class);
            if (t.segments.size() != 18 || t.specials.size() != 2
                    || !t.specials.get(0).name.equals("dash") || !t.specials.get(1).name.equals("air")) {
                throw new IllegalStateException("Invalid Poem V6 combo layout");
            }
            for (Timing p : java.util.stream.Stream.concat(t.segments.stream(), t.specials.stream()).toList()) {
                if (!(0 <= p.hit_start && p.hit_start < p.contact && p.contact <= p.recovery && p.recovery < p.duration)) {
                    throw new IllegalStateException("Invalid Poem V6 attack timing: " + p.name);
                }
            }
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Poem V6 animation timing", e);
        }
    }

    private record Timeline(List<Timing> segments, List<Timing> specials) { }
    private record Timing(String name, float start, float duration, float hit_start, float contact, float recovery) { }
}
