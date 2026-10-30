package com.whitecloud233.modid.herobrine_companion.util;

import net.minecraft.world.phys.Vec3;

public class EndRingContext {
    public static final double CENTER_X = 0.5D;
    public static final double CENTER_Y = 102.0D;
    public static final double CENTER_Z = 0.5D;

    // 内环的基岩位于 Y=100..102，玩家恢复点必须远离中央末地传送门。
    public static final double SAFE_X = 55.5D;
    public static final double SAFE_Y = 104.0D;
    public static final double SAFE_Z = 0.5D;

    /** 非挑战状态的 Herobrine 始终留在圆环中央，玩家恢复点独立使用 SAFE 坐标。 */
    public static Vec3 heroAnchor() {
        return new Vec3(CENTER_X, CENTER_Y, CENTER_Z);
    }
    
    public static final String TAG_FIXED = "hero_end_ring_fixed";
    public static final String TAG_INTRO = "hero_intro_sequence";
    public static final String TAG_RESPAWNED_SAFE = "hero_respawned_safe";
    public static final String TAG_TELEPORTING = "hero_teleporting";
    public static final String TAG_TRANSFER_STAGED = "hero_dimension_transfer_staged";
}
