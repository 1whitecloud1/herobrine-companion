package com.whitecloud233.herobrine_companion.entity.visual;

import java.util.ArrayList;
import java.util.List;

/**
 * 赠礼手势库 —— 直接移植自 Bedrock hb1 组件包 WhiteCloudScripts/entity/logic/event/hero_gesture_library.py。
 *
 * <p>该 python 文件是动作的"创作源":关键帧以 20tps 编写,播放按秒推进;导出后的
 * hero_offer.animation.json(3 万行)只是其烘焙曲线,因此本类直接移植帧表而非 JSON。
 * 采样按 Catmull-Rom 走(见 {@link #build}:smooth=true),与导出 JSON 的
 * {@code "lerp_mode": "catmullrom"} 对齐——原先用线性插值,转角处速度突变、观感发顿。
 * 角度单位:度。关节语义与 POSE_BONES 一致:head / body(→bssBody) / 双臂(上臂 3 轴 + 前臂 X)。
 *
 * <p>CLIP_IDS 1..12 同时是动画控制器 variable.hero_offer_clip_id 的选择号,保持完全一致。
 */
public final class HeroGestureLibrary {

    // ---- 稳定线缆 id(与 Bedrock CLIP_IDS 一致)----
    public static final int CLIP_REACH_TAKE = 1;
    public static final int CLIP_CHERISH = 2;
    public static final int CLIP_DELIGHTED = 3;
    public static final int CLIP_RELUCTANT = 4;
    public static final int CLIP_PUSH_BACK = 5;
    public static final int CLIP_SUSPICIOUS = 6;
    public static final int CLIP_WARNING = 7;
    public static final int CLIP_JUDGED = 8;
    public static final int CLIP_GIVE_BACK = 9;
    public static final int CLIP_REQUEST = 10;
    public static final int CLIP_SECRET = 11;
    public static final int CLIP_BIRTHDAY_LIFT = 12;

    /**
     * 进食手势:不在 Bedrock 的 offer 控制器 1..12 内(原版走 bssEating 通道),
     * 这里作为第 13 个 id 接入同一条播放通道,姿势用 bss_eating 数据剪辑。
     */
    public static final int CLIP_EAT = 13;

    private HeroGestureLibrary() {
    }

    // ------------------------------------------------------------------
    // 帧定义(与 python _frame 一致;时间为 20tps tick)
    // ------------------------------------------------------------------
    private static final class Frame {
        final float t;                 // 秒(tick/20)
        float[] head;                  // {x,y,z}
        float[] body;                  // {x,y,z}
        float[] rightUpper;            // {x,y,z}
        float rightLowerX;
        float[] leftUpper;
        float leftLowerX;
        boolean holdOn;                // 显式 hold=True(进入持有)
        boolean holdOff;               // 显式 hold=False(解除持有)
        boolean end;
        String sound;                  // "name|volume|pitch"

        Frame(float tick, float[] head, float[] body, float[] right, float[] left,
              boolean holdOn, boolean holdOff, boolean end, String sound) {
            this.t = tick / 20.0F;
            this.head = head;
            this.body = body;
            if (right != null) {
                this.rightUpper = new float[]{right[0], right[1], right[2]};
                this.rightLowerX = right[3];
            }
            if (left != null) {
                this.leftUpper = new float[]{left[0], left[1], left[2]};
                this.leftLowerX = left[3];
            }
            this.holdOn = holdOn;
            this.holdOff = holdOff;
            this.end = end;
            this.sound = sound;
        }
    }

    private static Frame f(float t, float[] head, float[] body, float[] right, float[] left,
                           boolean holdOn, boolean holdOff, boolean end, String sound) {
        return new Frame(t, head, body, right, left, holdOn, holdOff, end, sound);
    }

    // 快捷:只有头/躯干/臂的部分参数
    private static Frame fh(float t, float[] head) {
        return f(t, head, null, null, null, false, false, false, null);
    }
    private static Frame fhR(float t, float[] head, float[] right) {
        return f(t, head, null, right, null, false, false, false, null);
    }
    private static Frame fhRho(float t, float[] head, float[] right) {
        return f(t, head, null, right, null, false, true, false, null);
    }
    private static Frame fhaR(float t, float[] head, float[] right) {
        return f(t, head, null, right, null, true, false, false, null);
    }
    private static Frame fhaRL(float t, float[] head, float[] right, float[] left) {
        return f(t, head, null, right, left, true, false, false, null);
    }
    private static Frame fhRL(float t, float[] head, float[] right, float[] left) {
        return f(t, head, null, right, left, false, false, false, null);
    }
    private static Frame fhBRL(float t, float[] head, float[] body, float[] right, float[] left) {
        return f(t, head, body, right, left, false, false, false, null);
    }
    private static Frame fR(float t, float[] right) {
        return f(t, null, null, right, null, false, false, false, null);
    }
    private static Frame fL(float t, float[] left) {
        return f(t, null, null, null, left, false, false, false, null);
    }
    private static Frame fhL(float t, float[] head, float[] left) {
        return f(t, head, null, null, left, false, false, false, null);
    }
    private static Frame fhB(float t, float[] head, float[] body) {
        return f(t, head, body, null, null, false, false, false, null);
    }
    private static Frame fhBR(float t, float[] head, float[] body, float[] right) {
        return f(t, head, body, right, null, false, false, false, null);
    }
    private static Frame fEnd(float t) {
        return f(t, null, null, null, null, false, false, true, null);
    }
    private static Frame fHoldOff(float t) {
        return f(t, null, null, null, null, false, true, false, null);
    }
    private static Frame fNeutral(float t) {
        return f(t, null, null, null, null, false, false, false, null);
    }

    // ------------------------------------------------------------------
    // 动作数据(逐条对应 python GESTURE_*)
    // ------------------------------------------------------------------
    private static final Frame[] REACH_TAKE = {
            fNeutral(0), fNeutral(4),
            fhaR(14, h(8, -5, 0), r(-48, -4, -7, -20)),
            fhR(26, h(14, -8, 0), r(-24, -8, -8, -52)),
            fhR(34, h(14, -8, 0), r(-24, -8, -8, -52)),
            fhR(42, h(6, 0, 0), r(-20, -5, -5, -44)),
            fhR(50, h(12, 0, 0), r(-14, 0, -3, -24)),
            fHoldOff(60), fEnd(64),
    };

    private static final Frame[] CHERISH = {
            fNeutral(0), fNeutral(4),
            fhaR(16, h(10, -6, 0), r(-46, -5, -8, -24)),
            fhBRL(32, h(20, -6, 3), b(2, 0, 0), r(-18, -16, -10, -66), l(-14, 18, 10, -54)),
            fhBRL(48, h(20, -6, 3), b(2, 0, 0), r(-18, -16, -10, -66), l(-14, 18, 10, -54)),
            fhRL(62, h(3, 0, 0), r(-18, -16, -10, -60), l(-12, 16, 8, -48)),
            fhRL(72, h(10, 0, 0), r(-10, -6, -5, -28), l(-8, 5, 4, -20)),
            fHoldOff(84), fEnd(88),
    };

    private static final Frame[] DELIGHTED = {
            fNeutral(0), fNeutral(4),
            fhaR(14, h(6, -4, -3), r(-48, -3, -7, -24)),
            fhBRL(26, h(4, -9, -7), b(1, -3, 0), r(-36, -5, -10, -60), l(-12, 0, 12, -28)),
            fhRL(36, h(13, -4, -4), r(-32, -5, -8, -56), l(-10, 0, 10, -24)),
            fhRL(44, h(2, 0, -2), r(-30, -4, -8, -54), l(-8, 0, 8, -20)),
            fhRL(52, h(11, 0, 0), r(-28, -4, -6, -50), l(-6, 0, 6, -16)),
            fhR(62, h(3, 0, 0), r(-14, 0, -3, -26)),
            fHoldOff(72), fEnd(76),
    };

    private static final Frame[] RELUCTANT = {
            fNeutral(0), fNeutral(4),
            fhaR(14, h(0, -8, 0), r(-42, 0, -6, -18)),
            fhBRL(26, h(-3, 24, 2), b(0, 3, 0), r(-18, -4, -4, -38), null),
            fhBRL(40, h(-3, 24, 2), b(0, 3, 0), r(-12, 0, -3, -24), null),
            fhRho(54, h(0, 12, 0), r(-5, 0, -2, -8)),
            fNeutral(64), fEnd(68),
    };

    private static final Frame[] PUSH_BACK = {
            fNeutral(0), fNeutral(4),
            fhR(14, h(1, -18, 0), r(-48, 0, -10, -18)),
            fhR(24, h(1, 18, 0), r(-58, 0, -12, -12)),
            fhR(34, h(0, -10, 0), r(-42, 0, -8, -16)),
            fR(44, r(-18, 0, -4, -8)),
            fNeutral(52), fEnd(56),
    };

    private static final Frame[] SUSPICIOUS = {
            fNeutral(0), fNeutral(4),
            fhB(16, h(10, -16, 7), b(-1, -3, 0)),
            fhB(28, h(10, -16, 7), b(-1, -3, 0)),
            fhR(40, h(3, 9, -4), r(-40, 0, -10, -28)),
            fhBR(50, h(-2, 12, 0), b(-1, 2, 0), r(-56, 0, -12, -16)),
            fNeutral(64), fEnd(68),
    };

    private static final Frame[] WARNING = {
            fNeutral(0), fNeutral(4),
            fhaR(14, h(6, -4, 0), r(-44, -4, -6, -22)),
            fhRL(28, h(8, 0, 0), r(-16, -8, -6, -40), l(-30, 0, 10, -64)),
            fhRL(42, h(11, 0, 0), r(-14, -8, -6, -36), l(-42, 0, 12, -42)),
            fhRL(50, h(6, 0, 0), r(-12, -4, -4, -28), l(-30, 0, 8, -50)),
            fhRL(60, h(3, 0, 0), r(-5, 0, -2, -10), l(-10, 0, 4, -14)),
            fHoldOff(68), fEnd(72),
    };

    private static final Frame[] JUDGED = {
            fNeutral(0), fNeutral(4),
            fhB(18, h(12, 0, 0), b(-1, 0, 0)),
            fhRL(30, h(10, 0, 0), r(-60, 0, -12, -18), l(-8, 0, 6, -16)),
            fhBRL(42, h(7, 6, 0), b(0, 4, 0), r(-48, 16, 12, -22), null),
            fhBRL(54, h(7, 6, 0), b(0, 4, 0), r(-30, 12, 10, -16), null),
            fNeutral(68), fEnd(72),
    };

    private static final Frame[] GIVE_BACK = {
            fNeutral(0), fNeutral(4),
            fhaR(16, h(14, -7, -3), r(-18, -8, -7, -48)),
            fhBR(32, h(3, 0, -3), b(2, 0, 0), r(-50, -2, -8, -22)),
            fhBR(44, h(11, 0, -2), b(2, 0, 0), r(-50, -2, -8, -22)),
            fhRho(52, h(3, 0, 0), r(-38, 0, -6, -18)),
            fNeutral(68), fEnd(72),
    };

    private static final Frame[] REQUEST = {
            fNeutral(0), fNeutral(4),
            fhL(16, h(-6, 15, 6), l(-12, 0, 8, -48)),
            fhL(28, h(-6, 15, 6), l(-12, 0, 8, -48)),
            fhL(40, h(4, 0, 0), l(-42, 0, 10, -26)),
            fhL(50, h(8, 0, 0), l(-34, 0, 8, -30)),
            fNeutral(60), fEnd(64),
    };

    private static final Frame[] SECRET = {
            fNeutral(0), fNeutral(4),
            fh(14, h(-3, -5, 0)), fh(24, h(-3, -5, 0)),
            fhaR(38, h(9, -6, 0), r(-46, -6, -8, -28)),
            fhBRL(52, h(17, -4, 2), b(2, 0, 0), r(-20, -18, -10, -66), l(-16, 18, 10, -60)),
            fhBRL(68, h(17, -4, 2), b(2, 0, 0), r(-20, -18, -10, -66), l(-16, 18, 10, -60)),
            fhRL(80, h(3, 0, 0), r(-16, -12, -8, -50), l(-12, 12, 8, -40)),
            fhRL(88, h(12, 0, 0), r(-8, -4, -4, -24), l(-6, 4, 4, -20)),
            fHoldOff(96), fEnd(100),
    };

    private static final Frame[] BIRTHDAY_LIFT = {
            fNeutral(0), fNeutral(4),
            fhaRL(18, h(9, -4, 0), r(-46, -8, -8, -24), l(-22, 10, 8, -24)),
            fhBRL(34, h(19, -5, 3), b(2, 0, 0), r(-26, -18, -10, -60), l(-24, 18, 10, -56)),
            fhBRL(52, h(19, -5, 3), b(2, 0, 0), r(-26, -18, -10, -60), l(-24, 18, 10, -56)),
            fhRL(66, h(3, 0, -3), r(-22, -16, -8, -56), l(-20, 16, 8, -52)),
            fhBRL(78, h(12, 0, 0), b(2, 0, 0), r(-18, -12, -7, -48), l(-16, 12, 7, -44)),
            fhRL(90, h(4, 0, 0), r(-8, -4, -3, -24), l(-6, 4, 3, -20)),
            fHoldOff(100), fEnd(104),
    };

    // 进食:只携带音效时间轴;姿势由 bss_eating 数据剪辑(见 HeroIdleClips.EATING)接管
    private static final Frame[] EAT = {
            f(0, null, null, null, null, true, false, false, null),
            f(31, null, null, null, null, false, false, false, "random.eat|0.7|1.0"),
            f(43, null, null, null, null, false, false, false, "random.eat|0.7|1.05"),
            f(58, null, null, null, null, false, false, false, "random.eat|0.7|0.95"),
            f(120, null, null, null, null, false, false, true, null),
    };

    // ------------------------------------------------------------------
    // 裁剪构建
    // ------------------------------------------------------------------

    private static final HeroClip REACH_TAKE_CLIP = build("reach_take", REACH_TAKE);
    private static final HeroClip CHERISH_CLIP = build("cherish", CHERISH);
    private static final HeroClip DELIGHTED_CLIP = build("delighted", DELIGHTED);
    private static final HeroClip RELUCTANT_CLIP = build("reluctant", RELUCTANT);
    private static final HeroClip PUSH_BACK_CLIP = build("push_back", PUSH_BACK);
    private static final HeroClip SUSPICIOUS_CLIP = build("suspicious", SUSPICIOUS);
    private static final HeroClip WARNING_CLIP = build("warning", WARNING);
    private static final HeroClip JUDGED_CLIP = build("judged", JUDGED);
    private static final HeroClip GIVE_BACK_CLIP = build("give_back", GIVE_BACK);
    private static final HeroClip REQUEST_CLIP = build("request", REQUEST);
    private static final HeroClip SECRET_CLIP = build("secret", SECRET);
    private static final HeroClip BIRTHDAY_LIFT_CLIP = build("birthday_lift", BIRTHDAY_LIFT);
    private static final HeroClip EAT_CLIP = build("eat", EAT);

    /** clipId(1..12) → 手势裁剪;未知 id 返回 null。 */
    public static HeroClip byClipId(int clipId) {
        return switch (clipId) {
            case CLIP_REACH_TAKE -> REACH_TAKE_CLIP;
            case CLIP_CHERISH -> CHERISH_CLIP;
            case CLIP_DELIGHTED -> DELIGHTED_CLIP;
            case CLIP_RELUCTANT -> RELUCTANT_CLIP;
            case CLIP_PUSH_BACK -> PUSH_BACK_CLIP;
            case CLIP_SUSPICIOUS -> SUSPICIOUS_CLIP;
            case CLIP_WARNING -> WARNING_CLIP;
            case CLIP_JUDGED -> JUDGED_CLIP;
            case CLIP_GIVE_BACK -> GIVE_BACK_CLIP;
            case CLIP_REQUEST -> REQUEST_CLIP;
            case CLIP_SECRET -> SECRET_CLIP;
            case CLIP_BIRTHDAY_LIFT -> BIRTHDAY_LIFT_CLIP;
            case CLIP_EAT -> EAT_CLIP;
            default -> null;
        };
    }

    /** 手势名 → 裁剪(供 Phase B 结果码解析用);未知返回 null。 */
    public static HeroClip byName(String name) {
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "reach_take" -> REACH_TAKE_CLIP;
            case "cherish" -> CHERISH_CLIP;
            case "delighted" -> DELIGHTED_CLIP;
            case "reluctant" -> RELUCTANT_CLIP;
            case "push_back" -> PUSH_BACK_CLIP;
            case "suspicious" -> SUSPICIOUS_CLIP;
            case "warning" -> WARNING_CLIP;
            case "judged" -> JUDGED_CLIP;
            case "give_back" -> GIVE_BACK_CLIP;
            case "request" -> REQUEST_CLIP;
            case "secret" -> SECRET_CLIP;
            case "birthday_lift" -> BIRTHDAY_LIFT_CLIP;
            case "eat" -> EAT_CLIP;
            default -> null;
        };
    }

    public static int clipIdOf(HeroClip clip) {
        if (clip == null) {
            return 0;
        }
        return switch (clip.name) {
            case "reach_take" -> CLIP_REACH_TAKE;
            case "cherish" -> CLIP_CHERISH;
            case "delighted" -> CLIP_DELIGHTED;
            case "reluctant" -> CLIP_RELUCTANT;
            case "push_back" -> CLIP_PUSH_BACK;
            case "suspicious" -> CLIP_SUSPICIOUS;
            case "warning" -> CLIP_WARNING;
            case "judged" -> CLIP_JUDGED;
            case "give_back" -> CLIP_GIVE_BACK;
            case "request" -> CLIP_REQUEST;
            case "secret" -> CLIP_SECRET;
            case "birthday_lift" -> CLIP_BIRTHDAY_LIFT;
            case "eat" -> CLIP_EAT;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // 结果码 → 手势名(Bedrock resolve_gesture_name 的移植,Phase B 使用)
    // ------------------------------------------------------------------

    private static final String[] CONSUMED_CODES = {
            "accepted", "remembered", "food_accepted", "warning", "request_fulfilled"
    };

    /**
     * 引导类反馈的轻量手势。
     *
     * <p>Bedrock 原版对这几类提示不播动作(见 hero_gesture_library._CODE_GESTURES),
     * 但玩家在这些交互里同样期待身体反馈,故补一个最贴近语义的手势;
     * 若要与原版严格一致,清空本表即可。
     */
    private static final java.util.Map<String, String> GUIDANCE_GESTURES = java.util.Map.of(
            "cooldown", "reluctant",
            "request_expired", "reluctant",
            "food_required", "suspicious",
            "item_changed", "suspicious",
            "empty_first", "suspicious",
            "empty_repeat", "suspicious");

    private static boolean isConsumed(String code) {
        for (String c : CONSUMED_CODES) {
            if (c.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String baseGesture(String code) {
        String guidance = GUIDANCE_GESTURES.get(code);
        if (guidance != null) {
            return guidance;
        }
        return switch (code) {
            case "accepted" -> "reach_take";
            case "remembered", "request_fulfilled" -> "cherish";
            case "warning", "received_warning" -> "warning";
            case "food_accepted" -> "eat";
            case "returned_last_bite", "empty_return", "return_item", "birthday_return" -> "give_back";
            case "rejected_repeat", "rejected_taboo" -> "push_back";
            case "rejected_suspicious" -> "suspicious";
            case "request_asked" -> "request";
            case "judged" -> "judged";
            case "secret_hit" -> "secret";
            case "birthday" -> "birthday_lift";
            default -> null;
        };
    }

    /**
     * 结果码 + 心情/口味 → 最终手势名(python resolve_gesture_name 语义):
     * 未消耗的码不带动画或重启动作;已消耗的码按 mood( delighted/grumpy )、
     * warning 优先、taste&lt;=-2 冷淡、cherish 或 taste&gt;=3 珍视、食物走 eat。
     */
    public static String resolveGestureName(String code, String kind, int tasteLevel, String mood) {
        String name = baseGesture(code);
        if (!isConsumed(code)) {
            return name;
        }
        if ("delighted".equals(mood)) {
            return "delighted";
        }
        if ("grumpy".equals(mood)) {
            return "reluctant";
        }
        if ("warning".equals(code) || "received_warning".equals(code)) {
            return "warning";
        }
        if (tasteLevel <= -2) {
            return "reluctant";
        }
        if ("cherish".equals(name) || tasteLevel >= 3) {
            return "cherish";
        }
        if ("food".equals(kind)) {
            return "eat";
        }
        return name;
    }

    // ------------------------------------------------------------------
    // 内部:帧表 → HeroClip 轨道
    // ------------------------------------------------------------------

    private static final class JointKeys {
        final List<Float> times = new ArrayList<>();
        final List<Float> vals = new ArrayList<>();

        void add(float t, float x, float y, float z) {
            times.add(t);
            vals.add(x);
            vals.add(y);
            vals.add(z);
        }

        HeroClip.Track track(int bone) {
            if (times.isEmpty()) {
                return null;
            }
            float[] t = new float[times.size()];
            for (int i = 0; i < t.length; i++) {
                t[i] = times.get(i);
            }
            float[] v = new float[vals.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = vals.get(i);
            }
            return new HeroClip.Track(bone, t, v);
        }
    }

    private static HeroClip build(String name, Frame[] frames) {
        JointKeys head = new JointKeys();
        JointKeys body = new JointKeys();
        JointKeys rArm = new JointKeys();
        JointKeys rFore = new JointKeys();
        JointKeys lArm = new JointKeys();
        JointKeys lFore = new JointKeys();
        List<HeroClip.Event> events = new ArrayList<>();
        boolean hold = false;

        for (Frame fr : frames) {
            if (fr.head != null) {
                head.add(fr.t, fr.head[0], fr.head[1], fr.head[2]);
            }
            if (fr.body != null) {
                body.add(fr.t, fr.body[0], fr.body[1], fr.body[2]);
            }
            if (fr.rightUpper != null) {
                rArm.add(fr.t, fr.rightUpper[0], fr.rightUpper[1], fr.rightUpper[2]);
                rFore.add(fr.t, fr.rightLowerX, 0.0F, 0.0F);
            }
            if (fr.leftUpper != null) {
                lArm.add(fr.t, fr.leftUpper[0], fr.leftUpper[1], fr.leftUpper[2]);
                lFore.add(fr.t, fr.leftLowerX, 0.0F, 0.0F);
            }
            if (fr.holdOn && !hold) {
                events.add(new HeroClip.Event(fr.t, HeroClip.EV_HOLD_ON, null));
                hold = true;
            } else if (fr.holdOff && hold) {
                events.add(new HeroClip.Event(fr.t, HeroClip.EV_HOLD_OFF, null));
                hold = false;
            }
            if (fr.sound != null) {
                events.add(new HeroClip.Event(fr.t, HeroClip.EV_SOUND, fr.sound));
            }
            if (fr.end) {
                if (hold) {
                    events.add(new HeroClip.Event(fr.t, HeroClip.EV_HOLD_OFF, null));
                }
                events.add(new HeroClip.Event(fr.t, HeroClip.EV_END, null));
            }
        }

        List<HeroClip.Track> tracks = new ArrayList<>(6);
        HeroClip.Track t;
        t = head.track(HeroClip.B_HEAD);
        if (t != null) {
            tracks.add(t);
        }
        t = body.track(HeroClip.B_BODY);
        if (t != null) {
            tracks.add(t);
        }
        t = rArm.track(HeroClip.B_RIGHT_ARM);
        if (t != null) {
            tracks.add(t);
        }
        t = rFore.track(HeroClip.B_RIGHT_FOREARM);
        if (t != null) {
            tracks.add(t);
        }
        t = lArm.track(HeroClip.B_LEFT_ARM);
        if (t != null) {
            tracks.add(t);
        }
        t = lFore.track(HeroClip.B_LEFT_FOREARM);
        if (t != null) {
            tracks.add(t);
        }

        float duration = 0.0F;
        for (HeroClip.Event e : events) {
            if (e.kind() == HeroClip.EV_END) {
                duration = Math.max(duration, e.time());
            }
        }
        if (duration <= 0.0F) {
            for (HeroClip.Track tr : tracks) {
                float[] times = tr.times();
                if (times.length > 0) {
                    duration = Math.max(duration, times[times.length - 1]);
                }
            }
        }
        // smooth=true:帧表按 Catmull-Rom 插值,与 Bedrock 导出 JSON 里这些关键帧的
        // "lerp_mode": "catmullrom" 一致(线性插值会在每个关键帧出现速度折角,观感发顿)
        return new HeroClip(name, duration, false, true,
                tracks.toArray(new HeroClip.Track[0]),
                events.toArray(new HeroClip.Event[0]));
    }

    // 帧参数构造小工具(语义与 python 一致:角度=度)
    private static float[] h(float x, float y, float z) {
        return new float[]{x, y, z};
    }
    private static float[] b(float x, float y, float z) {
        return new float[]{x, y, z};
    }
    private static float[] r(float x, float y, float z, float fx) {
        return new float[]{x, y, z, fx};
    }
    private static float[] l(float x, float y, float z, float fx) {
        return new float[]{x, y, z, fx};
    }
}