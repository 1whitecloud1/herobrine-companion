package com.whitecloud233.herobrine_companion.client.animation;

import net.minecraft.util.Mth;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.util.DebugLog;
import com.whitecloud233.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.herobrine_companion.combat.poem.StandalonePoemController;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Idle/run/sprint base layer for the standalone Poem, with a short crossfade between states.
 *
 * <p>Only the third-person body is animated. First-person players keep the vanilla hand,
 * and any attack playback owns the pose while it is visible; the renderer crossfades back
 * to this layer instead of popping to the vanilla model.
 */
public final class PoemLocomotion {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Same 0.10 s blend the Bedrock locomotion controller uses. */
    public static final double BLEND_TICKS = 2.0;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Set<UUID> REPORTED = new HashSet<>();
    private static boolean unavailable;

    private PoemLocomotion() { }

    /** @return the current world pose, or null when the player should use the vanilla pose. */
    public static Matrix4f[] world(Player player, double now) {
        if (unavailable) return null;
        String desired = state(player);
        // Keep the phase while airborne/sneaking so landing or standing up resumes the same loop instead of restarting it.
        if (desired == null) return null;
        try {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), ignored -> new Track(desired, now));
            track.transition(desired, now, player.walkAnimation.speed());
            Matrix4f[] pose = track.sample(now);
            if (REPORTED.add(player.getUUID())) {
                DebugLog.info(LOGGER, "Standalone Poem locomotion active for {}: {}, {} joints", player.getName().getString(), desired, pose.length);
            }
            return pose;
        } catch (RuntimeException error) {
            // A missing/corrupt optional clip must not crash every entity render; vanilla animation remains usable.
            unavailable = true;
            TRACKS.clear();
            REPORTED.clear();
            LOGGER.error("Standalone Poem locomotion disabled after a data error", error);
            return null;
        }
    }

    public static void tick(ClientLevel level) {
        if (level == null) { TRACKS.clear(); return; }
        if (!StandalonePoemController.enabled()) { TRACKS.clear(); return; }
        TRACKS.keySet().removeIf(uuid -> {
            Player player = level.getPlayerByUUID(uuid);
            return player == null || !player.isAlive() || !(player.getMainHandItem().getItem() instanceof PoemOfTheEndItem);
        });
    }

    public static void clear() { TRACKS.clear(); }

    /** Null means no standalone locomotion: no weapon, in the air, sneaking, swimming, riding, sleeping or invisible. */
    private static String state(Player player) {
        if (!StandalonePoemController.enabled()
                || !(player.getMainHandItem().getItem() instanceof PoemOfTheEndItem)
                || !player.isAlive() || player.isSpectator() || player.isSleeping() || player.isPassenger()
                || player.isSwimming() || player.isFallFlying()
                || player.isShiftKeyDown() || player.isUsingItem() || !player.onGround()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) return null;
        boolean moving = player.walkAnimation.speed() > .05f || player.getDeltaMovement().horizontalDistanceSqr() > .0025;
        if (!moving) return "idle";
        return player.isSprinting() ? "sprint" : "run";
    }

    private static float smooth(double value) {
        float t = (float) Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3 - 2 * t);
    }

    static final class Track {
        private String current;
        private double currentStarted;
        private String previous;
        private double previousStarted;
        private double transitionAt;

        Track(String state, double now) {
            current = state;
            currentStarted = now;
            transitionAt = now;
        }

        void transition(String state, double now, float speed) {
            if (state.equals(current)) return;
            // A 0.03..0.05 dead band keeps run/sprint from flickering back to idle at the threshold.
            if ("idle".equals(state) && ("run".equals(current) || "sprint".equals(current)) && speed > .03f) return;
            previous = current;
            previousStarted = currentStarted;
            current = state;
            currentStarted = now;
            transitionAt = now;
        }

        Matrix4f[] sample(double now) {
            Matrix4f[] target = sample(current, now - currentStarted);
            double age = now - transitionAt;
            if (previous == null || age >= BLEND_TICKS) {
                previous = null;
                return target;
            }
            Matrix4f[] from = sample(previous, now - previousStarted);
            return PoemMotionLibrary.blend(from, target, smooth(age / BLEND_TICKS));
        }

        private static Matrix4f[] sample(String name, double ticks) {
            PoemMotionLibrary.LocomotionClip clip = PoemMotionLibrary.get().locomotion(name);
            // Wrap before narrowing to float, so long sessions retain sub-frame precision.
            return clip.sample(clip.phase(ticks / 20.0), true);
        }
    }
}
