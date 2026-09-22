package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroLocomotion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Regression checks for walking after reload and legacy fixed-pose saves. */
public final class HeroAnimationCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        walking();
        savedPoses();
        System.out.println("HERO_ANIMATION_OK: " + checks + " checks; walking cadence, delayed sync, legacy pose NBT");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static float walk(int ticks, double blocksPerTick, boolean walkingFlag) {
        float clock = 0.0F;
        for (int i = 0; i < ticks; i++) {
            clock = HeroLogic.advanceWalkAnimationTicks(clock, false, walkingFlag, blocksPerTick, 0.0);
        }
        return clock;
    }

    private static void walking() {
        // At 4.3 blocks/second, opposing strides must be visible after about 1.1 seconds.
        // This fails when seconds are stored in a tick counter (a 20x slowdown).
        float halfStride = walk(22, 4.3 / 20.0, true);
        check(halfStride > 19.0F && halfStride < 21.0F, "Normal walking reaches half a stride in 22 ticks");
        float[] start = new float[32];
        float[] later = new float[32];
        HeroLocomotion.computeWalkPose(0.0F, start);
        HeroLocomotion.computeWalkPose((halfStride / 20.0F) / 2.0F, later);
        check(start[6] < -20.0F && later[6] > 20.0F, "Right arm swings to the opposite side");
        check(start[18] > 20.0F && later[18] < -20.0F, "Right leg swings to the opposite side");
        check(later[9] < -20.0F && later[21] > 20.0F, "Left limbs oppose the right limbs");
        for (float angle : later) check(Float.isFinite(angle), "Every animated component stays finite");

        close(walk(22, 4.3 / 20.0, false), halfStride, "Position updates animate before the walking flag arrives");
        close(walk(44, 4.3 / 40.0, true), halfStride, "Equal distance gives equal phase at slower speed");
        close(walk(11, 4.3 / 10.0, true), halfStride, "Equal distance gives equal phase at faster speed");
        close(HeroLogic.advanceWalkAnimationTicks(7.0F, false, true, 0.0, 0.0), 7.0F, "A blocked path does not march in place");
        close(HeroLogic.advanceWalkAnimationTicks(7.0F, false, false, 0.0, 0.0), 0.0F, "Stopping resets the walking clock");
        close(HeroLogic.advanceWalkAnimationTicks(7.0F, true, true, 0.2, 0.0), 0.0F, "Floating leaves the walking animation");
        close(HeroLogic.advanceWalkAnimationTicks(0.0F, false, false, 0.3, 0.4),
                HeroLogic.advanceWalkAnimationTicks(0.0F, false, false, 0.5, 0.0), "Diagonal movement uses horizontal distance");
        close(HeroLogic.advanceWalkAnimationTicks(0.0F, false, true, 1000.0, 0.0),
                HeroLogic.advanceWalkAnimationTicks(0.0F, false, true, 4.0, 0.0), "Teleports cannot advance an unbounded number of strides");
        close(HeroLogic.advanceWalkAnimationTicks(7.0F, false, true, Double.NaN, 0.0), 7.0F, "Invalid movement cannot poison the clock");
    }

    private static CompoundTag pose(int angleCount) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("IsPoseEditing", true);
        ListTag angles = new ListTag();
        for (int i = 0; i < angleCount; i++) angles.add(FloatTag.valueOf(i * 0.01F));
        tag.put("CustomPoseAngles", angles);
        return tag;
    }

    private static void savedPoses() throws Exception {
        check(HeroDataHandler.readPoseAngles(new CompoundTag()) == null, "Saves predating the pose editor keep normal animations");
        CompoundTag missing = new CompoundTag();
        missing.putBoolean("IsPoseEditing", true);
        check(HeroDataHandler.readPoseAngles(missing) == null, "An enabled flag without angles cannot freeze the model");
        missing.putString("CustomPoseAngles", "legacy");
        check(HeroDataHandler.readPoseAngles(missing) == null, "A malformed angle tag cannot enable a fixed pose");
        for (int length : new int[]{0, 18, 29, 31}) {
            check(HeroDataHandler.readPoseAngles(pose(length)) == null, "Reject incompatible angle count " + length);
        }

        CompoundTag wrongType = pose(0);
        ListTag doubles = new ListTag();
        for (int i = 0; i < 30; i++) doubles.add(DoubleTag.valueOf(0.5));
        wrongType.put("CustomPoseAngles", doubles);
        check(HeroDataHandler.readPoseAngles(wrongType) == null, "Wrong numeric tag types do not become a zero pose");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            CompoundTag tag = pose(30);
            tag.getList("CustomPoseAngles", 5).set(29, FloatTag.valueOf(invalid));
            check(HeroDataHandler.readPoseAngles(tag) == null, "Reject non-finite angles atomically");
        }

        CompoundTag disabled = pose(30);
        disabled.putBoolean("IsPoseEditing", false);
        check(HeroDataHandler.readPoseAngles(disabled) == null, "Cleared poses stay disabled despite leftover angles");
        CompoundTag neutral = pose(30);
        for (int i = 0; i < 30; i++) neutral.getList("CustomPoseAngles", 5).set(i, FloatTag.valueOf(0.0F));
        check(HeroDataHandler.readPoseAngles(neutral) == null, "Legacy enabled zero poses release the animation override");
        neutral.putInt("PoseDataVersion", 1);
        check(HeroDataHandler.readPoseAngles(neutral) != null, "A newly saved intentional neutral pose remains valid");
        neutral.putInt("PoseDataVersion", 0);
        check(HeroDataHandler.readPoseAngles(neutral) == null, "Explicit legacy version also migrates the zero pose");
        neutral.getList("CustomPoseAngles", 5).set(0, FloatTag.valueOf(-0.0F));
        check(HeroDataHandler.readPoseAngles(neutral) == null, "Signed zero cannot keep a legacy pose frozen");
        neutral.getList("CustomPoseAngles", 5).set(0, FloatTag.valueOf(0.00001F));
        check(HeroDataHandler.readPoseAngles(neutral) != null, "Even a small intentional legacy rotation is preserved");

        CompoundTag valid = pose(30);
        valid.putInt("TrustLevel", 87);
        CompoundTag before = valid.copy();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(valid, bytes);
        CompoundTag restored = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        float[][] angles = HeroDataHandler.readPoseAngles(restored);
        check(angles != null && angles.length == 10, "Valid poses survive compressed NBT round trips");
        for (int i = 0; i < 30; i++) close(angles[i / 3][i % 3], i * 0.01F, "Preserve legacy joint order " + i);
        check(valid.equals(before) && restored.equals(before), "Pose decoding preserves the save and unrelated data");
        float[][] secondRead = HeroDataHandler.readPoseAngles(restored);
        angles[0][0] = 1.0F;
        check(!Arrays.deepEquals(angles, secondRead), "Different entities do not share mutable pose arrays");
    }
}
