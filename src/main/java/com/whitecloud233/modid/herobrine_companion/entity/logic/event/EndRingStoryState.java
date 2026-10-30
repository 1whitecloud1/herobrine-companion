package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import net.minecraft.nbt.CompoundTag;

/** Persistent story progress; gaze duration and disconnect countdown belong to one session only. */
public final class EndRingStoryState {
    public static final String RETURN_PENDING = "EndRingVoidReturnPending";

    private EndRingStoryState() {}

    public static boolean resume(CompoundTag data, long gameTime) {
        boolean returnToRing = data.getBoolean(RETURN_PENDING);
        data.remove(RETURN_PENDING);
        data.remove("SkyGazeTicks");
        if (!data.contains("EnteredEndRingTime")) data.putLong("EnteredEndRingTime", gameTime);
        // 缺少进入时间的旧存档也不能丢掉已完成的对话进度。
        return returnToRing;
    }

    public static boolean beginVoidReturn(CompoundTag data) {
        data.remove("SkyGazeTicks");
        if (data.getInt("WakeUpStage") < 3 || data.getBoolean("HasSimulatedCrash")) return false;
        data.putBoolean("HasSimulatedCrash", true);
        data.putBoolean(RETURN_PENDING, true);
        return true;
    }

    public static boolean tickSkyGaze(CompoundTag data, float pitch) {
        if (data.getInt("WakeUpStage") < 3 || data.getBoolean(RETURN_PENDING) || pitch >= -60) {
            data.remove("SkyGazeTicks");
            return false;
        }
        int ticks = data.getInt("SkyGazeTicks") + 1;
        if (ticks >= 200) {
            data.remove("SkyGazeTicks");
            return true;
        }
        data.putInt("SkyGazeTicks", ticks);
        return false;
    }
}
