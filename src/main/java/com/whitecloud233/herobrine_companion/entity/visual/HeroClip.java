package com.whitecloud233.herobrine_companion.entity.visual;

/**
 * 通用英雄动画裁剪数据。
 *
 * <p>数据直接移植自 Bedrock hb1 组件包(hero.geo.json 骨架 / hero_gesture_library.py /
 * hero_idle*.animation.json / hero_bss_eating.animation.json):
 * <ul>
 *   <li>角度单位为 <b>度</b>(Bedrock 惯例),应用时转弧度;</li>
 *   <li>骨骼 id 与 hero.geo.json 的关节一一对应(含 bssBody/bssChest 躯干、foreArm/lowerLeg
 *       下臂下腿、bss* elbow/knee 关节盖等辅助骨);</li>
 *   <li>采样支持不均匀时间关键帧的 Catmull-Rom(smooth=true:idle/吃蛋糕的导出曲线,
 *       以及 hero_offer 手势帧表——与 Bedrock 的 {@code "lerp_mode": "catmullrom"} 一致)
 *       与线性插值(smooth=false,仅在需要严格线性时使用)。</li>
 * </ul>
 */
public final class HeroClip {

    // ---- hb1 骨架骨 id ----
    public static final int B_HEAD = 0;
    public static final int B_BODY = 1;
    public static final int B_RIGHT_ARM = 2;
    public static final int B_LEFT_ARM = 3;
    public static final int B_RIGHT_FOREARM = 4;
    public static final int B_LEFT_FOREARM = 5;
    public static final int B_RIGHT_LEG = 6;
    public static final int B_LEFT_LEG = 7;
    public static final int B_RIGHT_LOWER_LEG = 8;
    public static final int B_LEFT_LOWER_LEG = 9;
    public static final int B_BSS_BODY = 10;      // 下级躯干(手势"body"导出目标)
    public static final int B_BSS_CHEST = 11;     // 上级躯干
    public static final int B_BSS_R_ELBOW = 12;   // 肘关节盖(Java 模型无对应几何,忽略)
    public static final int B_BSS_L_ELBOW = 13;
    public static final int B_BSS_R_KNEE = 14;    // 膝关节盖(忽略)
    public static final int B_BSS_L_KNEE = 15;

    // ---- 事件类型 ----
    public static final int EV_SOUND = 0;
    public static final int EV_HOLD_ON = 1;
    public static final int EV_HOLD_OFF = 2;
    public static final int EV_END = 3;

    /** 一条骨骼的旋转轨道:times[n] 秒 + xyz[3n] 度。 */
    public record Track(int bone, float[] times, float[] xyz) {
        public int frameCount() {
            return times.length;
        }
    }

    /** 时间点事件。arg:EV_SOUND 时为 音效名|音量|音高(如 "random.eat|0.7|1.0")。 */
    public record Event(float time, int kind, String arg) {
    }

    public final String name;
    public final float duration;   // 秒
    public final boolean loop;
    public final boolean smooth;   // true=Catmull-Rom(导出曲线), false=线性(手势编辑源)
    public final Track[] tracks;
    public final Event[] events;

    public HeroClip(String name, float duration, boolean loop, Track[] tracks, Event[] events) {
        this(name, duration, loop, true, tracks, events);
    }

    public HeroClip(String name, float duration, boolean loop, boolean smooth, Track[] tracks, Event[] events) {
        this.name = name;
        this.duration = duration;
        this.loop = loop;
        this.smooth = smooth;
        this.tracks = tracks;
        this.events = events;
    }

    public int trackIndex(int bone) {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i].bone() == bone) {
                return i;
            }
        }
        return -1;
    }

    /** 循环时间取模。 */
    public float wrapTime(float t) {
        if (duration <= 0.0F) {
            return 0.0F;
        }
        float r = t % duration;
        return r < 0.0F ? r + duration : r;
    }

    // ------------------------------------------------------------------
    // 采样
    // ------------------------------------------------------------------

    /**
     * 采样单条轨道到 out3(度)。t 为裁剪内秒,未循环裁剪不做 wrap,超出端点用端点值。
     */
    public static void sampleTrack(Track track, float t, boolean smooth, float[] out3) {
        int n = track.frameCount();
        if (n == 0) {
            out3[0] = 0.0F;
            out3[1] = 0.0F;
            out3[2] = 0.0F;
            return;
        }
        if (n == 1 || t <= track.times()[0]) {
            out3[0] = track.xyz()[0];
            out3[1] = track.xyz()[1];
            out3[2] = track.xyz()[2];
            return;
        }
        float[] times = track.times();
        float[] xyz = track.xyz();
        float lastT = times[n - 1];
        if (t >= lastT) {
            out3[0] = xyz[3 * (n - 1)];
            out3[1] = xyz[3 * (n - 1) + 1];
            out3[2] = xyz[3 * (n - 1) + 2];
            return;
        }
        // 找段 [i, i+1)
        int i = 1;
        while (i < n - 1 && times[i] <= t) {
            i++;
        }
        i--;
        float t0 = times[Math.max(0, i - 1)];
        float t1 = times[i];
        float t2 = times[i + 1];
        float t3 = times[Math.min(n - 1, i + 2)];
        float segLen = t2 - t1;
        float u = segLen <= 0.0F ? 0.0F : (t - t1) / segLen;
        for (int axis = 0; axis < 3; axis++) {
            float p0 = xyz[3 * Math.max(0, i - 1) + axis];
            float p1 = xyz[3 * i + axis];
            float p2 = xyz[3 * (i + 1) + axis];
            float p3 = xyz[3 * Math.min(n - 1, i + 2) + axis];
            float v;
            if (smooth) {
                float u2 = u * u;
                float u3 = u2 * u;
                v = 0.5F * ((2.0F * p1) + (-p0 + p2) * u
                        + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u2
                        + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u3);
            } else {
                v = p1 + (p2 - p1) * u;
            }
            out3[axis] = v;
        }
    }

    // ------------------------------------------------------------------
    // 规范姿势输出(供 HeroModel 应用)
    // ------------------------------------------------------------------

    /** 规范 10 关节序(与 Bedrock POSE_BONES / Java customPoseAngles 一致): */
    public static final int J_HEAD = 0;
    public static final int J_BODY = 1;
    public static final int J_RIGHT_ARM_UPPER = 2;
    public static final int J_LEFT_ARM_UPPER = 3;
    public static final int J_RIGHT_ARM_LOWER = 4;
    public static final int J_LEFT_ARM_LOWER = 5;
    public static final int J_RIGHT_LEG_UPPER = 6;
    public static final int J_LEFT_LEG_UPPER = 7;
    public static final int J_RIGHT_LEG_LOWER = 8;
    public static final int J_LEFT_LEG_LOWER = 9;
    public static final int CANONICAL_JOINTS = 10;

    private static final float[] ZERO3 = {0.0F, 0.0F, 0.0F};

    /**
     * 把裁剪在 t 时刻的姿势采样进 out30(10 关节 × 3 度,顺序见 J_*)。
     *
     * <p>骨骼映射规则(与 Java 模型关节一一对应):
     * <ul>
     *   <li>bssBody、bssChest → BODY(两者同现时相加;Java 为单躯干近似);</li>
     *   <li>rightForeArm/leftForeArm → 下臂,rightLowerLeg/leftLowerLeg → 下腿;</li>
     *   <li>bss* elbow/knee 关节盖数据忽略(Java 模型无盖几何,弯曲由下臂/下腿承载);</li>
     *   <li>head/body/rightArm/leftArm/rightLeg/leftLeg 直接对应。</li>
     * </ul>
     */
    public static void samplePose(HeroClip clip, float time, float[] out30) {
        for (int i = 0; i < CANONICAL_JOINTS; i++) {
            out30[3 * i] = 0.0F;
            out30[3 * i + 1] = 0.0F;
            out30[3 * i + 2] = 0.0F;
        }
        float[] tmp = new float[3];
        float[] t0 = new float[3];
        float[] t1 = new float[3];

        int idx;
        idx = clip.trackIndex(B_HEAD);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            copy(tmp, out30, J_HEAD);
        }
        idx = clip.trackIndex(B_BODY);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, t0);
        }
        idx = clip.trackIndex(B_BSS_BODY);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            add(t0, tmp);
        }
        idx = clip.trackIndex(B_BSS_CHEST);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            add(t0, tmp);
        }
        if (clip.trackIndex(B_BODY) >= 0 || clip.trackIndex(B_BSS_BODY) >= 0 || clip.trackIndex(B_BSS_CHEST) >= 0) {
            copy(t0, out30, J_BODY);
        }
        sampleUpperArm(clip, B_RIGHT_ARM, time, out30, J_RIGHT_ARM_UPPER);
        sampleUpperArm(clip, B_LEFT_ARM, time, out30, J_LEFT_ARM_UPPER);
        idx = clip.trackIndex(B_RIGHT_FOREARM);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            out30[3 * J_RIGHT_ARM_LOWER] = tmp[0];
        }
        idx = clip.trackIndex(B_LEFT_FOREARM);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            out30[3 * J_LEFT_ARM_LOWER] = tmp[0];
        }
        idx = clip.trackIndex(B_RIGHT_LEG);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            copy(tmp, out30, J_RIGHT_LEG_UPPER);
        }
        idx = clip.trackIndex(B_LEFT_LEG);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            copy(tmp, out30, J_LEFT_LEG_UPPER);
        }
        idx = clip.trackIndex(B_RIGHT_LOWER_LEG);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            out30[3 * J_RIGHT_LEG_LOWER] = tmp[0];
        }
        idx = clip.trackIndex(B_LEFT_LOWER_LEG);
        if (idx >= 0) {
            sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
            out30[3 * J_LEFT_LEG_LOWER] = tmp[0];
        }
        // bss elbow/knee 关节盖:忽略(见类注释)
    }

    private static void sampleUpperArm(HeroClip clip, int bone, float time, float[] out30, int joint) {
        int idx = clip.trackIndex(bone);
        if (idx < 0) {
            return;
        }
        float[] tmp = new float[3];
        sampleTrack(clip.tracks[idx], time, clip.smooth, tmp);
        copy(tmp, out30, joint);
    }

    public static void groupRot(float[] canonical, int joint, float[] out3) {
        out3[0] = canonical[3 * joint];
        out3[1] = canonical[3 * joint + 1];
        out3[2] = canonical[3 * joint + 2];
    }

    private static void copy(float[] src, float[] dst, int joint) {
        dst[3 * joint] = src[0];
        dst[3 * joint + 1] = src[1];
        dst[3 * joint + 2] = src[2];
    }

    private static void add(float[] dst, float[] src) {
        dst[0] += src[0];
        dst[1] += src[1];
        dst[2] += src[2];
    }

    /** 事件查询:返回 t 时刻之后(含)最近的事件;供客户端音效/持有道具 cue。 */
    public static Event nextEvent(HeroClip clip, float t, int[] cursor) {
        while (cursor[0] < clip.events.length && clip.events[cursor[0]].time() <= t) {
            cursor[0]++;
        }
        if (cursor[0] < clip.events.length) {
            return clip.events[cursor[0]];
        }
        return null;
    }
}