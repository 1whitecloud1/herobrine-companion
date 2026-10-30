package com.whitecloud233.herobrine_companion.combat.poem;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The native clips, with no client, loader or Epic Fight classes on its classpath. */
public final class PoemMotionLibrary {
    private static final String ASSETS = "/assets/herobrine_companion/";
    // Custom rig/mesh data must stay outside GeckoLib's scanned animations/ tree.
    public static final String DATA_ROOT = "poem_standalone/";
    public static final List<String> MODES = List.of("normal", "realm_breaker", "thunder", "void_shatter");
    public static final List<String> LOCOMOTION = List.of("idle", "run", "sprint");
    public static final int DASH = 4, AIR = 5, FIRST_EXTRA = 6, MOVE_COUNT = 12;
    private final Extras extras;
    public final Rig rig;
    private final Timeline timeline;
    private final Matrix4f[] bindWorld;
    private final Matrix4f[] inverseBind;
    private final Map<Integer, Clip> clips = new ConcurrentHashMap<>();
    private final Map<String, LocomotionClip> locomotion = new ConcurrentHashMap<>();
    private volatile LocomotionData locomotionData;

    private PoemMotionLibrary() {
        rig = read(DATA_ROOT + "rig.json", Rig.class);
        timeline = read("epicfight/poem_unity09_timing.json", Timeline.class);
        extras = read("epicfight/poem_unity09_extras.json", Extras.class);
        if (extras.moves().size() != 3) throw new IllegalStateException("Missing standalone Poem gestures");
        if (rig.format() != 1 || rig.joints().size() != 20 || timeline.modes().size() != 4) {
            throw new IllegalStateException("Invalid standalone Poem rig/timeline");
        }
        bindWorld = new Matrix4f[rig.joints().size()];
        inverseBind = new Matrix4f[bindWorld.length];
        for (int i = 0; i < bindWorld.length; i++) {
            Joint j = rig.joints().get(i);
            if (j.parent() >= i || j.parent() < -1) throw new IllegalStateException("Non-topological Poem rig");
            bindWorld[i] = matrix(j.bind());
            if (j.parent() >= 0) bindWorld[i] = new Matrix4f(bindWorld[j.parent()]).mul(bindWorld[i]);
            inverseBind[i] = new Matrix4f(bindWorld[i]).invert();
        }
        for (int m = 0; m < 4; m++) {
            Mode mode = timeline.modes().get(m);
            if (mode.mode() != m || !MODES.get(m).equals(mode.key()) || mode.segments().size() != 4 || mode.specials().size() != 2) {
                throw new IllegalStateException("Invalid standalone Poem mode " + m);
            }
            for (int move = 0; move < MOVE_COUNT; move++) {
                Timing t = timing(m, move);
                if (!(0 < t.recovery() && t.recovery() < t.duration()) || t.contacts().isEmpty()) {
                    throw new IllegalStateException("Invalid standalone Poem timing");
                }
                float previousEnd = 0;
                for (Contact contact : t.contacts()) {
                    if (!(previousEnd <= contact.start() && contact.start() < contact.end() && contact.end() <= t.recovery())) {
                        throw new IllegalStateException("Invalid standalone Poem contact window: " + mode.key() + "/" + t.name());
                    }
                    previousEnd = contact.end();
                }
            }
        }
    }

    private static final class Holder { static final PoemMotionLibrary INSTANCE = new PoemMotionLibrary(); }
    public static PoemMotionLibrary get() { return Holder.INSTANCE; }

    public Timing timing(int mode, int step) {
        if (mode < 0 || mode >= 4 || !validMove(step)) throw new IllegalArgumentException("Poem mode/step");
        if (step < DASH) return timeline.modes().get(mode).segments().get(step);
        if (step < FIRST_EXTRA) return timeline.modes().get(mode).specials().get(step - DASH);
        Extra extra = extras.moves().get((step - FIRST_EXTRA) / 2);
        return (step - FIRST_EXTRA) % 2 == 0 ? extra.ground() : extra.air();
    }

    public static boolean validMove(int move) { return move >= 0 && move < MOVE_COUNT; }
    public static boolean combo(int move) { return move >= 0 && move < DASH; }
    public static int basicMove(boolean sprinting, boolean airborne) { return airborne ? AIR : sprinting ? DASH : 0; }
    public static int extraMove(int gesture, boolean airborne) {
        if (gesture < 0 || gesture >= 3) throw new IllegalArgumentException("Poem gesture");
        return FIRST_EXTRA + gesture * 2 + (airborne ? 1 : 0);
    }

    public int joint(String name) {
        for (int i = 0; i < rig.joints().size(); i++) if (rig.joints().get(i).name().equals(name)) return i;
        throw new IllegalArgumentException("Unknown Poem joint " + name);
    }

    public Matrix4f bind(int joint) { return new Matrix4f(bindWorld[joint]); }
    public Matrix4f inverseBind(int joint) { return new Matrix4f(inverseBind[joint]); }

    public Clip clip(int mode, int step) {
        Timing timing = timing(mode, step);
        int key = (step >= FIRST_EXTRA ? 4 : mode) * MOVE_COUNT + step;
        return clips.computeIfAbsent(key, ignored -> new Clip(read(
                "animmodels/animations/player/poem_unity09/" + (step >= FIRST_EXTRA ? "extra" : MODES.get(mode)) + "/" + timing.name() + ".json",
                ClipData.class), timing));
    }

    /** Bedrock-era idle/run/sprint loops, retargeted onto this rig and stored outside GeckoLib's tree. */
    public LocomotionClip locomotion(String name) {
        return locomotion.computeIfAbsent(name, key -> {
            LocomotionData data = locomotionData;
            if (data == null) {
                synchronized (this) {
                    if (locomotionData == null) locomotionData = read(DATA_ROOT + "locomotion.json", LocomotionData.class);
                    data = locomotionData;
                }
            }
            if (data.format() != 1) throw new IllegalStateException("Invalid Poem locomotion format");
            for (LocomotionClipData clip : data.clips()) if (clip.name().equals(key)) return new LocomotionClip(clip);
            throw new IllegalStateException("Missing Poem locomotion clip " + key);
        });
    }

    public void preload() {
        for (int m = 0; m < 4; m++) for (int s = 0; s < MOVE_COUNT; s++) clip(m, s);
        for (String name : LOCOMOTION) locomotion(name);
    }

    /** Local-space quaternion interpolation preserves every turn in the 240 Hz source. */
    public final class Clip {
        private final Channel[] channels = new Channel[rig.joints().size()];
        public final Timing timing;

        private Clip(ClipData data, Timing timing) {
            this.timing = timing;
            for (ChannelData channel : data.animation()) {
                int index = joint(channel.name());
                if (channels[index] != null) throw new IllegalStateException("Duplicate Poem channel");
                channels[index] = new Channel(channel);
            }
            if (Arrays.stream(channels).anyMatch(Objects::isNull)) throw new IllegalStateException("Missing Poem joint");
        }

        public Matrix4f[] sample(float seconds, boolean inPlace) {
            Matrix4f[] world = new Matrix4f[channels.length];
            for (int i = 0; i < world.length; i++) {
                Matrix4f local = channels[i].sample(seconds);
                // Horizontal steps move the entity through vanilla physics, never just its rendered body.
                // Keep the source height, crouch and all limb motion.
                if (i == 0 && inPlace) local.m30(0).m31(0);
                int parent = rig.joints().get(i).parent();
                world[i] = parent < 0 ? local : new Matrix4f(world[parent]).mul(local);
            }
            return world;
        }

        /** Sample only a socket's ancestors for the densely sampled blade ribbon. */
        public Matrix4f sampleJoint(int joint, float seconds, boolean inPlace) {
            if (joint < 0 || joint >= channels.length) throw new IllegalArgumentException("Poem joint");
            Matrix4f world = new Matrix4f();
            for (int i = joint; i >= 0; i = rig.joints().get(i).parent()) {
                Matrix4f local = channels[i].sample(seconds);
                if (i == 0 && inPlace) local.m30(0).m31(0);
                world = local.mul(world);
            }
            return world;
        }
    }

    /** A loop-only clip with no contact windows; the root's horizontal motion is removed. */
    public final class LocomotionClip {
        private final Channel[] channels = new Channel[rig.joints().size()];
        private final float duration;

        private LocomotionClip(LocomotionClipData data) {
            if (!(data.duration() > 0) || !Float.isFinite(data.duration())) throw new IllegalStateException("Invalid Poem locomotion duration");
            duration = data.duration();
            for (ChannelData channel : data.animation()) {
                int index = joint(channel.name());
                if (channels[index] != null) throw new IllegalStateException("Duplicate Poem locomotion channel");
                channels[index] = new Channel(channel);
            }
            if (Arrays.stream(channels).anyMatch(Objects::isNull)) throw new IllegalStateException("Missing Poem locomotion joint");
            for (Channel channel : channels) {
                if (channel.times[0] < 0 || channel.times[channel.times.length - 1] > duration + 1e-4f) {
                    throw new IllegalStateException("Poem locomotion key outside its duration");
                }
            }
        }

        public float duration() { return duration; }

        /** Wrap a timeline in seconds into this loop, including negative input. */
        public float phase(double seconds) {
            double value = seconds % duration;
            return (float) (value < 0 ? value + duration : value);
        }

        public Matrix4f[] sample(float seconds, boolean inPlace) {
            seconds = phase(seconds);
            Matrix4f[] world = new Matrix4f[channels.length];
            for (int i = 0; i < world.length; i++) {
                Matrix4f local = channels[i].sample(seconds);
                if (i == 0 && inPlace) local.m30(0).m31(0);
                int parent = rig.joints().get(i).parent();
                world[i] = parent < 0 ? local : new Matrix4f(world[parent]).mul(local);
            }
            return world;
        }
    }

    public Matrix4f[] deform(Matrix4f[] world, boolean leftHanded) {
        Matrix4f[] skin = new Matrix4f[world.length];
        Matrix4f mirror = new Matrix4f().scaling(-1, 1, 1);
        for (int i = 0; i < skin.length; i++) {
            String name = rig.joints().get(i).name();
            int source = leftHanded && (name.endsWith("_R") || name.endsWith("_L"))
                    ? joint(name.substring(0, name.length() - 1) + (name.endsWith("R") ? "L" : "R")) : i;
            skin[i] = new Matrix4f(world[source]).mul(inverseBind[source]);
            if (leftHanded) skin[i] = new Matrix4f(mirror).mul(skin[i]).mul(mirror);
        }
        return skin;
    }

    public static Matrix4f[] blend(Matrix4f[] from, Matrix4f[] to, float alpha) {
        Matrix4f[] result = new Matrix4f[to.length];
        for (int i = 0; i < result.length; i++) result[i] = interpolate(from[i], to[i], alpha);
        return result;
    }

    private static Matrix4f interpolate(Matrix4f a, Matrix4f b, float t) {
        Vector3f p = a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()), t);
        Quaternionf q = a.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(b.getUnnormalizedRotation(new Quaternionf()).normalize(), t);
        Vector3f scale = a.getScale(new Vector3f()).lerp(b.getScale(new Vector3f()), t);
        return new Matrix4f().translationRotateScale(p, q, scale);
    }

    private static final class Channel {
        final float[] times;
        final Vector3f[] positions;
        final Quaternionf[] rotations;
        final Vector3f[] scales;

        Channel(ChannelData data) {
            times = data.time();
            if (times.length < 2 || times.length != data.transform().length) throw new IllegalStateException("Invalid Poem keys");
            positions = new Vector3f[times.length]; rotations = new Quaternionf[times.length]; scales = new Vector3f[times.length];
            for (int i = 0; i < times.length; i++) {
                if (!Float.isFinite(times[i]) || (i > 0 && times[i] <= times[i - 1])) throw new IllegalStateException("Invalid Poem key time");
                Matrix4f m = matrix(data.transform()[i]);
                positions[i] = m.getTranslation(new Vector3f());
                rotations[i] = m.getUnnormalizedRotation(new Quaternionf()).normalize();
                scales[i] = m.getScale(new Vector3f());
            }
        }

        Matrix4f sample(float seconds) {
            int index = Arrays.binarySearch(times, Math.max(times[0], Math.min(seconds, times[times.length - 1])));
            if (index >= 0) return new Matrix4f().translationRotateScale(positions[index], rotations[index], scales[index]);
            int next = -index - 1, prev = next - 1;
            float t = (seconds - times[prev]) / (times[next] - times[prev]);
            return new Matrix4f().translationRotateScale(new Vector3f(positions[prev]).lerp(positions[next], t),
                    new Quaternionf(rotations[prev]).slerp(rotations[next], t), new Vector3f(scales[prev]).lerp(scales[next], t));
        }
    }

    public static Matrix4f matrix(float[] rowMajor) {
        if (rowMajor.length != 16) throw new IllegalArgumentException("Expected a 4x4 matrix");
        for (float f : rowMajor) if (!Float.isFinite(f)) throw new IllegalArgumentException("Non-finite Poem matrix");
        return new Matrix4f().set(rowMajor).transpose();
    }

    public static <T> T read(String path, Class<T> type) {
        try (var input = Objects.requireNonNull(PoemMotionLibrary.class.getResourceAsStream(ASSETS + path), path);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, type);
        } catch (Exception e) { throw new IllegalStateException("Cannot read Poem animation " + path, e); }
    }

    public record Rig(int format, List<Joint> joints, float[][] weapon_to_tool) { }
    public record Joint(String name, int parent, float[] bind) { }
    private record ClipData(List<ChannelData> animation) { }
    private record LocomotionData(int format, List<LocomotionClipData> clips) { }
    private record LocomotionClipData(String name, float duration, List<ChannelData> animation) { }
    private record ChannelData(String name, float[] time, float[][] transform) { }
    private record Timeline(List<Mode> modes) { }
    private record Mode(int mode, String key, List<Timing> segments, List<Timing> specials) { }
    private record Extras(List<Extra> moves) { }
    private record Extra(String key, Timing ground, Timing air) { }
    public record Timing(String name, float duration, float recovery, List<Contact> contacts) { }
    public record Contact(float start, float end) { }
}
