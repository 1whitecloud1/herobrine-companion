package com.whitecloud233.herobrine_companion.client.animation;

import com.whitecloud233.herobrine_companion.combat.poem.PoemComboClock;
import com.whitecloud233.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.herobrine_companion.combat.poem.StandalonePoemController;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.PoemAnimationPacket;
import com.whitecloud233.herobrine_companion.network.PoemAnimationRequestPacket;
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
        if (LOCAL.request(mode, now)) start(mc.player, slot, mode, LOCAL.step(), now);
        PacketHandler.sendToServer(new PoemAnimationRequestPacket(slot, mode));
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
        if (packet.mode() < 0 || packet.mode() > 3 || packet.step() < 0 || packet.step() > 3 || packet.slot() < 0 || packet.slot() > 8) return;
        long age = packet.serverTick() - packet.startTick();
        if (age < 0 || age > 100) return;
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
        Matrix4f[] from = previous == null || previous.weight(now) == 0 ? null : previous.world(now);
        float fromWeight = from == null ? 0 : previous.weight(now);
        PLAYING.put(player.getUUID(), new Playback(player.getId(), slot, mode, step, tick, from, fromWeight));
    }

    public static Frame frame(Player player, float partialTick) {
        if (!StandalonePoemController.enabled()) return null;
        Playback playback = PLAYING.get(player.getUUID());
        if (playback == null || !matches(player, playback)) return null;
        double now = player.level().getGameTime() + partialTick;
        float weight = playback.weight(now);
        if (weight <= .0001f) return null;
        Matrix4f[] world = playback.world(now);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        List<PoemBladeTrail.Ribbon> trail = PoemBladeTrail.sample(playback.timing, playback.age(now),
                time -> playback.tool(playback.start + time * 20), time -> playback.weight(playback.start + time * 20));
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
        Playback(int entityId, int slot, int mode, int step, double start, Matrix4f[] from, float fromWeight) {
            this.entityId = entityId; this.slot = slot; this.mode = mode; this.step = step; this.start = start; this.from = from; this.fromWeight = fromWeight;
            clip = PoemMotionLibrary.get().clip(mode, step); timing = clip.timing;
        }
        float age(double now) { return Math.max(0, (float) ((now - start) / 20)); }
        float weight(double now) {
            float age = age(now), enter = smooth(age / PoemBladeTrail.blendTime(timing, from != null));
            return (fromWeight + (1 - fromWeight) * enter) * (1 - smooth((age - timing.recovery()) / .12f));
        }
        Matrix4f[] world(double now) {
            float age = age(now);
            Matrix4f[] world = clip.sample(Math.min(age, timing.duration()), true);
            float blend = PoemBladeTrail.blendTime(timing, true);
            return from != null && age < blend ? PoemMotionLibrary.blend(from, world, smooth(age / blend)) : world;
        }
        Matrix4f tool(double now) {
            int joint = PoemMotionLibrary.get().joint("Tool_R");
            float age = age(now);
            Matrix4f tool = clip.sampleJoint(joint, Math.min(age, timing.duration()), true);
            float blend = PoemBladeTrail.blendTime(timing, true);
            return from != null && age < blend
                    ? PoemMotionLibrary.blend(new Matrix4f[]{from[joint]}, new Matrix4f[]{tool}, smooth(age / blend))[0] : tool;
        }
        static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    }
}
