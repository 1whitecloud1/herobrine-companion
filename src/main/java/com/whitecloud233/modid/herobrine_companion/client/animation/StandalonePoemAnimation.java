package com.whitecloud233.modid.herobrine_companion.client.animation;

import net.minecraft.util.Mth;

import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemComboClock;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.modid.herobrine_companion.combat.poem.StandalonePoemController;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PoemAnimationPacket;
import com.whitecloud233.modid.herobrine_companion.network.PoemAnimationRequestPacket;
import com.whitecloud233.modid.herobrine_companion.network.PoemGesturePacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** Client prediction and tracking-player playback; never touches player yaw or position. */
public final class StandalonePoemAnimation {
    private static final Map<UUID, Playback> PLAYING = new HashMap<>();
    private static final PoemComboClock LOCAL = new PoemComboClock();
    private static ClientLevel level;
    private static int slot = -1, mode = -1;
    private StandalonePoemAnimation() { }

    public static void attack() {
        Minecraft mc = Minecraft.getInstance();
        if (!validLocal(mc)) return;
        context(mc);
        double now = mc.level.getGameTime();
        int move = PoemMotionLibrary.basicMove(mc.player.isSprinting(), !mc.player.onGround());
        if (LOCAL.request(mode, move, now)) start(mc.player, slot, mode, LOCAL.step(), now);
        PacketHandler.sendToServer(new PoemAnimationRequestPacket(slot, mode));
    }

    public static void gesture(int gesture) {
        Minecraft mc = Minecraft.getInstance();
        if (gesture < 0 || gesture >= 3 || !validLocal(mc)) return;
        context(mc);
        double now = mc.level.getGameTime();
        int move = PoemMotionLibrary.extraMove(gesture, !mc.player.onGround());
        if (LOCAL.request(mode, move, now)) start(mc.player, slot, mode, LOCAL.step(), now);
        PacketHandler.sendToServer(new PoemGesturePacket(gesture, slot, mode));
    }

    public static void cancelLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (slot >= 0 && mc.player != null && mc.getConnection() != null) {
            PLAYING.remove(mc.player.getUUID());
            PacketHandler.sendToServer(new PoemAnimationRequestPacket(-1, -1));
        }
        LOCAL.reset(); slot = mode = -1;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level) {
            PLAYING.clear(); LOCAL.reset(); slot = mode = -1; level = mc.level;
            PoemLocomotion.clear();
        }
        if (mc.level == null) return;
        if (validLocal(mc)) {
            context(mc);
            if (LOCAL.tick(mc.level.getGameTime())) start(mc.player, slot, mode, LOCAL.step(), LOCAL.start());
        } else cancelLocal();
        PLAYING.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            return player == null || !matches(player, entry.getValue()) || entry.getValue().age(mc.level.getGameTime()) > entry.getValue().timing.duration() + .6f;
        });
        PoemLocomotion.tick(mc.level);
    }

    private static void context(Minecraft mc) {
        if (level != mc.level) { PLAYING.clear(); LOCAL.reset(); slot = mode = -1; level = mc.level; }
        int newSlot = mc.player.getInventory().selected, newMode = StandalonePoemController.mode(mc.player);
        if (slot != newSlot || mode != newMode) {
            cancelLocal(); slot = newSlot; mode = newMode;
        }
    }

    public static boolean validLocal(Minecraft mc) {
        return mc.level != null && mc.screen == null && !mc.isPaused() && mc.isWindowActive()
                && StandalonePoemController.usable(mc.player);
    }

    public static void receive(PoemAnimationPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!StandalonePoemController.enabled() || mc.level == null) return;
        if (level != mc.level) { PLAYING.clear(); LOCAL.reset(); slot = mode = -1; level = mc.level; }
        if (!(mc.level.getEntity(packet.entityId()) instanceof AbstractClientPlayer player)
                || !player.getUUID().equals(packet.playerId())) return;
        if (packet.step() == -1) {
            Playback current = PLAYING.get(player.getUUID());
            // A delayed cancellation for the previous hotbar slot/mode cannot cancel the new set.
            if (current != null && current.slot == packet.slot() && current.mode == packet.mode()) {
                PLAYING.remove(player.getUUID());
                if (player == mc.player && slot == packet.slot() && mode == packet.mode()) LOCAL.reset();
            }
            return;
        }
        if (packet.mode() < 0 || packet.mode() > 3 || !PoemMotionLibrary.validMove(packet.step()) || packet.slot() < 0 || packet.slot() > 8) return;
        long age = packet.serverTick() - packet.startTick();
        if (age < 0 || age > (long) Math.ceil(PoemMotionLibrary.get().timing(packet.mode(), packet.step()).duration() * 20) + 12) return;
        double start = mc.level.getGameTime() - age;
        Playback existing = PLAYING.get(player.getUUID());
        // Do not restart a locally predicted stroke when its server confirmation arrives.
        if (player == mc.player && existing != null && existing.mode == packet.mode() && existing.step == packet.step()
                && existing.slot == packet.slot() && Math.abs(existing.start - start) <= 5) return;
        start(player, packet.slot(), packet.mode(), packet.step(), start);
        if (player == mc.player) LOCAL.synchronize(packet.mode(), packet.step(), start);
    }

    private static void start(Player player, int slot, int mode, int step, double tick) {
        double now = player.level().getGameTime();
        Playback previous = PLAYING.get(player.getUUID());
        Matrix4f[] from;
        float fromWeight;
        boolean locomotion = false;
        if (previous != null && previous.weight(now) > .0001f && matches(player, previous)) {
            from = previous.world(now);
            fromWeight = previous.weight(now);
        } else {
            // A cut that starts while idle/running lifts out of the locomotion pose instead of the vanilla rest pose.
            from = PoemLocomotion.world(player, now);
            fromWeight = from == null ? 0 : 1;
            locomotion = from != null;
        }
        Playback playback = new Playback(player.getId(), slot, mode, step, tick, from, fromWeight);
        playback.locomotion = locomotion;
        PLAYING.put(player.getUUID(), playback);
    }

    /** Third-person body: attacks on top of the idle/run/sprint base layer. */
    public static Frame frame(Player player, float partialTick) { return compose(player, partialTick, true); }

    /** True while this player's current cut still owns the body; locomotion alone keeps vanilla body-yaw smoothing. */
    public static boolean attacking(Player player, float partialTick) {
        if (!StandalonePoemController.enabled()) return false;
        Playback playback = PLAYING.get(player.getUUID());
        return playback != null && matches(player, playback)
                && playback.weight(player.level().getGameTime() + partialTick) > .0001f;
    }

    /** First-person viewmodel: only actual cuts, so the vanilla hand keeps idle/run. */
    public static Frame attackFrame(Player player, float partialTick) { return compose(player, partialTick, false); }

    private static Frame compose(Player player, float partialTick, boolean locomotionBase) {
        if (!StandalonePoemController.enabled()) return null;
        double now = player.level().getGameTime() + partialTick;
        Playback playback = PLAYING.get(player.getUUID());
        if (playback != null && !matches(player, playback)) {
            PLAYING.remove(player.getUUID());
            playback = null;
        }
        float attackWeight = playback == null ? 0 : playback.weight(now);
        // At full attack weight the cut already owns the body; only start sampling the base layer for its recovery fade.
        Matrix4f[] base = locomotionBase && attackWeight < .999f ? PoemLocomotion.world(player, now) : null;
        if (attackWeight <= .0001f && base == null) return null;
        if (player.isInvisible() && player != Minecraft.getInstance().player) return null;
        Matrix4f[] world;
        float weight;
        if (playback != null && attackWeight > .0001f) {
            Matrix4f[] attackWorld = playback.world(now);
            world = base == null ? attackWorld : PoemMotionLibrary.blend(base, attackWorld, attackWeight);
            // The base layer already owns the whole body, so keep the pose fully weighted and fade the cut inside it.
            weight = base == null ? attackWeight : 1;
        } else {
            world = base;
            weight = 1;
        }
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        Playback cut = playback;
        List<PoemBladeTrail.Ribbon> trail = cut != null && attackWeight > .0001f
                ? PoemBladeTrail.sample(cut.timing, cut.age(now),
                        time -> cut.tool(cut.start + time * 20), time -> cut.weight(cut.start + time * 20))
                : List.of();
        return new Frame(world, PoemMotionLibrary.get().deform(world, left), weight, left, trail);
    }

    private static boolean matches(Player player, Playback playback) {
        return player.getId() == playback.entityId && StandalonePoemController.usable(player) && StandalonePoemController.mode(player) == playback.mode
                && (!(player == Minecraft.getInstance().player) || player.getInventory().selected == playback.slot);
    }

    public record Frame(Matrix4f[] world, Matrix4f[] skin, float weight, boolean leftHanded,
                        List<PoemBladeTrail.Ribbon> trail) { }

    private static final class Playback {
        final int entityId, slot, mode, step;
        final double start;
        final Matrix4f[] from;
        final float fromWeight;
        final PoemMotionLibrary.Clip clip;
        final PoemMotionLibrary.Timing timing;
        boolean locomotion;
        Playback(int entityId, int slot, int mode, int step, double start, Matrix4f[] from, float fromWeight) {
            this.entityId = entityId; this.slot = slot; this.mode = mode; this.step = step; this.start = start; this.from = from; this.fromWeight = fromWeight;
            clip = PoemMotionLibrary.get().clip(mode, step); timing = clip.timing;
        }
        float age(double now) { return Math.max(0, (float) ((now - start) / 20)); }
        float weight(double now) {
            float age = age(now), enter = smooth(age / blend());
            // Four ticks back to the held idle/run pose: long enough to read as a transition, short enough to stay responsive.
            return (fromWeight + (1 - fromWeight) * enter) * (1 - smooth((age - timing.recovery()) / .20f));
        }
        /** A locomotion hand-off is a fresh pose, not a same-combo link, so it gets the slower full-body blend. */
        float blend() {
            return locomotion ? Mth.clamp(timing.contacts().get(0).start(), .00001f, .1f)
                    : PoemBladeTrail.blendTime(timing, from != null);
        }
        Matrix4f[] world(double now) {
            float age = age(now);
            Matrix4f[] world = clip.sample(Math.min(age, timing.duration()), true);
            float blend = blend();
            return from != null && age < blend ? PoemMotionLibrary.blend(from, world, smooth(age / blend)) : world;
        }
        Matrix4f tool(double now) {
            int joint = PoemMotionLibrary.get().joint("Tool_R");
            float age = age(now);
            Matrix4f tool = clip.sampleJoint(joint, Math.min(age, timing.duration()), true);
            float blend = blend();
            return from != null && age < blend
                    ? PoemMotionLibrary.blend(new Matrix4f[]{from[joint]}, new Matrix4f[]{tool}, smooth(age / blend))[0] : tool;
        }
        static float smooth(float t) { t = Mth.clamp(t, 0F, 1F); return t * t * (3 - 2 * t); }
    }
}
