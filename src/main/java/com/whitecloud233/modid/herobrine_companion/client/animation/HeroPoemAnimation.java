package com.whitecloud233.modid.herobrine_companion.client.animation;

import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemHeroCombo;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroScytheComboLibrary;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

/** Uses the player's native Poem rig; the server's battle timeline still owns attack speed and damage. */
public final class HeroPoemAnimation {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<HeroEntity, Track> TRACKS = new WeakHashMap<>();

    private HeroPoemAnimation() { }

    /**
     * 无 Epic Fight 的战斗模式里，Hero 的全身骨架动画一律使用终末之诗的 rig，
     * <b>与主手武器无关</b>：装扮栏里换成别的武器，动作仍然是这一套。
     *
     * <p>被界面登记的纸娃娃（{@link HeroPoemPreview}）同样必须开启战斗模式，
     * 关闭后回到普通姿态。</p>
     */
    public static boolean shouldAnimate(HeroEntity hero, float partial) {
        boolean preview = HeroPoemPreview.isDriven(hero);
        // 界面纸娃娃永远不会被 Epic Fight 接管，所以只有实体本体才需要让位给 EF。
        boolean ownedByEpicFight = !preview && HeroEpicFightCompat.isRuntimeBridgeReady();
        return !ownedByEpicFight && hero.isAlive() && hero.isBattleModeActive()
                && !hero.isPoseEditing && !hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && !hero.isInspectingScythe() && !hero.isDebugAnim() && !hero.isCastingThunder()
                && (hero.getFloatingAmount(partial) <= 0 || hero.isBattleTapAction()) && !hero.isPassenger() && !hero.isSleeping()
                && !hero.isSwimming() && !hero.isFallFlying()
                // 远程武器拉弓/换弹时沿用原版持弓姿势，其余时间都归终末之诗骨架。
                && !(hero.isUsingItem() && HeroCombatWeaponHelper.isRangedLoadout(hero.getMainHandItem()))
                && !(hero.getOfferBlend(partial) > .001f && hero.getVisualState().getGestureClipId() != 0)
                && hero.getVisualMainHandItem().isEmpty() && hero.getVisualOffHandItem().isEmpty();
    }

    public static Frame frame(HeroEntity hero, float partial) {
        if (!shouldAnimate(hero, partial)) {
            TRACKS.remove(hero);
            return null;
        }
        try {
            return frameUnchecked(hero, partial);
        } catch (RuntimeException error) {
            // 界面纸娃娃没有服务端兜底：动作资源一旦有问题就整块关掉预览，
            // 不能让异常穿透渲染线程。
            TRACKS.remove(hero);
            LOGGER.warn("Hero Poem preview disabled after a render-time data error", error);
            HeroPoemPreview.disable();
            return null;
        }
    }

    private static Frame frameUnchecked(HeroEntity hero, float partial) {
        // Entity ticks advance locally even when several server metadata updates arrive together.
        // GUI paper dolls never tick, so the screen registers them for a wall-clock preview instead.
        Double preview = HeroPoemPreview.clock(hero);
        double now = preview != null ? preview
                : (double) hero.tickCount + Math.max(0, Math.min(1, partial));
        String state = hero.getBattleActionState() == HeroEntity.BATTLE_ACTION_APPROACH
                || hero.walkAnimation.speed() > .05f || hero.getDeltaMovement().horizontalDistanceSqr() > .0025
                ? (hero.isSprinting() ? "sprint" : "run") : "idle";
        Track track = TRACKS.computeIfAbsent(hero, ignored -> new Track(state, now));
        track.base.transition(state, now, hero.walkAnimation.speed());
        Matrix4f[] base = track.base.sample(now);

        boolean attacking;
        int serial;
        int mode;
        int move;
        int ticks;
        int duration;
        if (preview != null) {
            // 纸娃娃不进食、不喝药、也没有服务端战斗序列：直接按时钟演一段连斩演示。
            HeroPoemPreview.Demo demo = HeroPoemPreview.demo(now);
            attacking = demo.attacking();
            serial = demo.serial();
            mode = demo.mode();
            move = demo.move();
            ticks = demo.ticks();
            duration = demo.duration();
        } else {
            int action = hero.getBattleActionState();
            attacking = action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                    || action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
            serial = hero.getBattleActionSerial();
            mode = hero.getMainHandItem().getItem() instanceof PoemOfTheEndItem poem
                    ? poem.getMode(hero.getMainHandItem()) : PoemOfTheEndItem.MODE_NORMAL;
            move = PoemHeroCombo.resolveMove(hero.getBattlePoemMove(), serial);
            ticks = hero.getBattleActionTicks();
            duration = HeroScytheComboLibrary.actionDurationTicks(hero, action);
        }
        Matrix4f[] world = track.attack.sampleMove(attacking, mode, serial, move,
                ticks, duration, now, base);
        boolean left = hero.getMainArm() == HumanoidArm.LEFT;
        return new Frame(world, PoemMotionLibrary.get().deform(world, left), 1, left, track.attack.trail(now));
    }

    /** beginBattleAction increments the serial before rendering the first cut. */
    public static int step(int serial) { return Math.floorMod(serial - 1, 4); }

    public static Matrix4f[] sample(int mode, int serial, float elapsedTicks, int durationTicks,
                                   Matrix4f[] base, Matrix4f[] from) {
        return sampleMove(mode, step(serial), elapsedTicks, durationTicks, from);
    }

    public static Matrix4f[] sampleMove(int mode, int move, float elapsedTicks, int durationTicks, Matrix4f[] from) {
        PoemMotionLibrary.Clip clip = PoemMotionLibrary.get().clip(mode, move);
        float progress = Math.max(0, Math.min(1, elapsedTicks / Math.max(1, durationTicks)));
        float seconds = progress * clip.timing.duration();
        Matrix4f[] world = clip.sample(seconds, true);
        float enter = smooth(elapsedTicks / 2);
        if (from != null && enter < 1) world = PoemMotionLibrary.blend(from, world, enter);
        // The server links cuts at recovery. Keep the attack pose until the next serial or an
        // explicit stop, rather than fading every cut back to idle halfway through a combo.
        return world;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static final class Track {
        final PoemLocomotion.Track base;
        final Playback attack = new Playback();

        Track(String state, double now) { base = new PoemLocomotion.Track(state, now); }
    }

    /** Render-local clock and hand-off pose, isolated from the server's damage/AI timeline. */
    static final class Playback {
        private double blendTicks = 2;
        private boolean attacking;
        private int serial, mode, move, durationTicks;
        private double started, transitionAt;
        private Matrix4f[] from, world;
        private PoemMotionLibrary.Clip clip;

        Matrix4f[] sample(boolean attacking, int mode, int serial, int syncedTicks, int durationTicks,
                          double now, Matrix4f[] base) {
            return sampleMove(attacking, mode, serial, step(serial), syncedTicks, durationTicks, now, base);
        }

        Matrix4f[] sampleMove(boolean attacking, int mode, int serial, int move, int syncedTicks, int durationTicks,
                             double now, Matrix4f[] base) {
            if (attacking && (!this.attacking || this.serial != serial || this.mode != mode || this.move != move)) {
                from = world == null ? base : world;
                blendTicks = this.attacking && this.mode == mode ? 1 : 2;
                this.serial = serial;
                this.mode = mode;
                this.move = move;
                this.clip = PoemMotionLibrary.get().clip(mode, move);
                this.durationTicks = durationTicks;
                // Anchor once per stroke. Re-reading syncedTicks + partial every frame produces a
                // sawtooth when packets stall, and jumps when a batch of newer ticks arrives.
                started = Math.floor(now) - Math.max(0, syncedTicks);
                transitionAt = now;
            } else if (!attacking && this.attacking) {
                // An interrupted/chained attack may end before the clip's recovery fade finishes.
                from = world;
                blendTicks = 2;
                transitionAt = now;
            }
            this.attacking = attacking;
            Matrix4f[] pose = attacking
                    ? HeroPoemAnimation.sampleMove(mode, move, (float) Math.max(0, now - started), durationTicks, null)
                    : base;
            float blend = smooth((float) ((now - transitionAt) / blendTicks));
            world = from != null && blend < 1 ? PoemMotionLibrary.blend(from, pose, blend) : pose;
            // Keep the hand-off pose for historical Tool_R samples in the blade ribbon.
            return world;
        }

        List<PoemBladeTrail.Ribbon> trail(double now) {
            if (!attacking || clip == null) return List.of();
            float age = (float) Math.max(0, (now - started) * clip.timing.duration() / Math.max(1, durationTicks));
            return PoemBladeTrail.sample(clip.timing, age, this::tool, ignored -> 1);
        }

        private Matrix4f tool(double seconds) {
            int joint = PoemMotionLibrary.get().joint("Tool_R");
            Matrix4f pose = clip.sampleJoint(joint, (float) seconds, true);
            double tick = started + seconds * durationTicks / clip.timing.duration();
            float blend = smooth((float) ((tick - transitionAt) / blendTicks));
            return from != null && blend < 1
                    ? PoemMotionLibrary.blend(new Matrix4f[]{from[joint]}, new Matrix4f[]{pose}, blend)[0] : pose;
        }
    }
}
