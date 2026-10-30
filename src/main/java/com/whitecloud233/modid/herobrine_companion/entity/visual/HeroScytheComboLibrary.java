package com.whitecloud233.modid.herobrine_companion.entity.visual;

import com.google.gson.Gson;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemHeroCombo;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 终末之诗镰刀普攻动作库：把 {@code animmodels/animations/hero/} 下的普攻 clip
 * （poem 20 关节 rig，矩阵轨道）在加载时重定向成 {@link HeroClip}（10 规范关节、度），
 * 供<b>未安装 Epic Fight</b> 时的战斗模式播放——按攻击序号轮换全部普攻动作。
 *
 * <p>主手武器<b>不参与</b>这个判断：装扮栏里给 Herobrine 换成别的武器，动作依旧取自
 * 终末之诗（武器只决定伤害数值与手持渲染）。装了 Epic Fight 时另走 EF 的武器动作，
 * 本库不会被问到。</p>
 *
 * <p>重定向规则与玩家侧独立渲染管线共用同一套空间换算（{@link PoemBladeTrail#toModel()}，
 * 该管线在玩家手持终末之诗时定向已验证正确）：关节本地旋转先减除 rig 绑定姿态
 * （{@code poem_standalone/rig.json}），再经 DCC→模型空间共轭，最后按 ModelPart 的
 * Z·Y·X 欧拉序分解成 (x,y,z) 度。</p>
 *
 * <p>骨骼映射：{@code Arm_X}→上臂、{@code Hand_X}→前臂（实测全部素材里 Shoulder 轨道
 * 都静止在绑定姿态，不携带动画，17 关节导出缺该轨道无信息损失）、{@code Torso·Chest}→
 * 躯干（两级本地旋转复合）、{@code Head}→头、{@code Thigh_X}→大腿、{@code Leg_X}→小腿；
 * Elbow/Knee 关节盖与 Tool 挂点忽略（Java 模型无对应几何）。缺失轨道一律按绑定姿态
 * （零旋转）处理。</p>
 */
public final class HeroScytheComboLibrary {
    private static final String ASSETS = "/assets/herobrine_companion/";
    /** 整段连招（v3/v4 full、dash、hold、ready、air）与 data/ 原始导出不算普攻，按时长过滤。 */
    private static final float MAX_ATTACK_SECONDS = 2.0F;
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger("HeroScytheComboLibrary");

    private static volatile List<HeroClip> clips;
    private static volatile boolean loadFailed;

    private HeroScytheComboLibrary() {
    }

    /** 素材清单（存在才加载）：顶层独立段 + MediaPipe 逐段 + Unity09 四模式各 4 段。 */
    private static List<String> sources() {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            names.add("hero_scythe_combo_" + i);
        }
        for (int i = 1; i <= 4; i++) {
            names.add("hero_scythe_combo_v4_" + i);
        }
        for (int i = 1; i <= 12; i++) {
            names.add("poem_mediapipe/combo_" + String.format("%02d", i));
        }
        for (String mode : List.of("normal", "realm_breaker", "thunder", "void_shatter")) {
            for (int i = 1; i <= 4; i++) {
                names.add("poem_unity09/" + mode + "/combo_" + String.format("%02d", i));
            }
        }
        return names;
    }

    public static boolean available() {
        List<HeroClip> loaded = clips;
        if (loaded == null && !loadFailed) {
            synchronized (HeroScytheComboLibrary.class) {
                if (clips == null && !loadFailed) {
                    try {
                        clips = load();
                    } catch (Exception failure) {
                        loadFailed = true;
                        LOGGER.warning("Poem scythe combo library unavailable: " + failure);
                    }
                }
                loaded = clips;
            }
        }
        return loaded != null && !loaded.isEmpty();
    }

    /** 攻击序号 → 轮换的普攻裁剪；库不可用时返回 null（调用方保留基础架势，不再程序化挥砍）。 */
    public static HeroClip clipForSerial(int serial) {
        if (!available()) {
            return null;
        }
        List<HeroClip> loaded = clips;
        return loaded.get(Math.floorMod(serial, loaded.size()));
    }

    /**
     * 战斗模式（无 Epic Fight）时由本库驱动攻击姿势：<b>只要在战斗里就驱动，与主手武器无关</b>。
     * 动作资源只有终末之诗这一套，所以给 Herobrine 换任何武器都不换动作。
     * 仅在 Epic Fight 姿势未接管时才会被问到（{@code HeroModel} 的战斗分支本身就是无 EF 路径）。
     */
    public static boolean shouldDriveBattlePose(HeroEntity hero) {
        return hero.isBattleModeActive() && available();
    }

    /**
     * 无 EF 战斗的攻击动作时长（tick）——{@code HeroBattleStanceGoal} 的节奏表与
     * 客户端裁剪进度采样共用这一份，避免两处漂移。与主手武器无关。
     */
    public static int actionDurationTicks(HeroEntity hero, int action) {
        return PoemHeroCombo.durationForMove(battleMode(hero),
                PoemHeroCombo.resolveMove(hero.getBattlePoemMove(), hero.getBattleActionSerial()));
    }

    /** 战斗动作模式：只有终末之诗本体携带模式；换成别的武器时按普通模式播放。 */
    public static int battleMode(HeroEntity hero) {
        return hero.getMainHandItem().getItem() instanceof PoemOfTheEndItem poem
                ? poem.getMode(hero.getMainHandItem()) : PoemOfTheEndItem.MODE_NORMAL;
    }

    // ------------------------------------------------------------------
    // 加载与重定向
    // ------------------------------------------------------------------

    private record Chan(String name, float[] time, float[][] transform) {
    }

    private record ClipFile(List<Chan> animation) {
    }

    private record RigFile(int format, List<JointData> joints) {
    }

    private record JointData(String name, int parent, float[] bind) {
    }

    private static final Gson GSON = new Gson();

    private static <T> T read(String path, Class<T> type) {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(HeroScytheComboLibrary.class.getResourceAsStream(ASSETS + path), path),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read hero scythe animation " + path, failure);
        }
    }

    /** 资源不存在返回 null（仓库间的素材子集不同，按存在性过滤）。 */
    private static <T> T tryRead(String path, Class<T> type) {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(HeroScytheComboLibrary.class.getResourceAsStream(ASSETS + path), path),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<HeroClip> load() {
        RigFile rig = read("poem_standalone/rig.json", RigFile.class);
        Map<String, Quaternionf> binds = new HashMap<>();
        for (JointData joint : rig.joints()) {
            binds.put(joint.name(), rotationOf(rowMajor(joint.bind())));
        }
        Quaternionf toModel = rotationOf(PoemBladeTrail.toModel());
        Quaternionf toModelInverse = new Quaternionf(toModel).invert();

        List<HeroClip> loaded = new ArrayList<>();
        for (String source : sources()) {
            ClipFile file = tryRead("animmodels/animations/hero/" + source + ".json", ClipFile.class);
            if (file == null || file.animation() == null || file.animation().isEmpty()) {
                continue;
            }
            HeroClip clip = convert(source.replace('/', '_'), file, binds, toModel, toModelInverse);
            if (clip != null) {
                loaded.add(clip);
            }
        }
        return loaded;
    }

    /** 输出轨道：规范骨 ← poem 关节链（按序复合本地旋转；缺失关节视为零旋转）。 */
    private record BoneSpec(int bone, String[] chain) {
    }

    private static final BoneSpec[] BONE_SPECS = {
            new BoneSpec(HeroClip.B_HEAD, new String[]{"Head"}),
            new BoneSpec(HeroClip.B_BODY, new String[]{"Torso", "Chest"}),
            new BoneSpec(HeroClip.B_RIGHT_ARM, new String[]{"Arm_R"}),
            new BoneSpec(HeroClip.B_LEFT_ARM, new String[]{"Arm_L"}),
            new BoneSpec(HeroClip.B_RIGHT_FOREARM, new String[]{"Hand_R"}),
            new BoneSpec(HeroClip.B_LEFT_FOREARM, new String[]{"Hand_L"}),
            new BoneSpec(HeroClip.B_RIGHT_LEG, new String[]{"Thigh_R"}),
            new BoneSpec(HeroClip.B_LEFT_LEG, new String[]{"Thigh_L"}),
            new BoneSpec(HeroClip.B_RIGHT_LOWER_LEG, new String[]{"Leg_R"}),
            new BoneSpec(HeroClip.B_LEFT_LOWER_LEG, new String[]{"Leg_L"}),
    };

    private static HeroClip convert(String name, ClipFile file, Map<String, Quaternionf> binds,
                                    Quaternionf toModel, Quaternionf toModelInverse) {
        Map<String, Chan> channels = new HashMap<>();
        for (Chan chan : file.animation()) {
            if (chan != null && chan.name() != null && chan.time() != null && chan.transform() != null) {
                channels.put(chan.name(), chan);
            }
        }
        float duration = 0.0F;
        for (Chan chan : channels.values()) {
            if (chan.time().length > 0) {
                duration = Math.max(duration, chan.time()[chan.time().length - 1]);
            }
        }
        if (duration <= 0.0F || duration > MAX_ATTACK_SECONDS) {
            return null;
        }

        List<HeroClip.Track> tracks = new ArrayList<>();
        for (BoneSpec spec : BONE_SPECS) {
            // 以链上第一个存在的轨道时间为基准（同一次导出所有轨道共享同一时间轴）
            float[] times = null;
            for (String joint : spec.chain()) {
                Chan chan = channels.get(joint);
                if (chan != null) {
                    times = chan.time();
                    break;
                }
            }
            if (times == null) {
                continue;
            }
            float[] xyz = new float[times.length * 3];
            Quaternionf delta = new Quaternionf();
            Quaternionf jointLocal = new Quaternionf();
            Matrix4f modelRotation = new Matrix4f();
            for (int i = 0; i < times.length; i++) {
                delta.identity();
                for (String joint : spec.chain()) {
                    Chan chan = channels.get(joint);
                    Quaternionf bind = binds.get(joint);
                    if (chan == null || bind == null) {
                        continue;
                    }
                    int key = Math.min(i, chan.transform().length - 1);
                    rotationOf(rowMajor(chan.transform()[key]), jointLocal);
                    // 减除绑定姿态后按链序复合（父×子），再换算到模型空间
                    jointLocal.premul(new Quaternionf(bind).invert());
                    delta.mul(jointLocal);
                }
                Quaternionf inModel = new Quaternionf(toModel).mul(delta).mul(toModelInverse);
                modelRotation.rotation(inModel);
                float[] euler = eulerZyx(modelRotation);
                xyz[3 * i] = euler[0];
                xyz[3 * i + 1] = euler[1];
                xyz[3 * i + 2] = euler[2];
            }
            tracks.add(new HeroClip.Track(spec.bone(), times, xyz));
        }
        if (tracks.isEmpty()) {
            return null;
        }
        // 120Hz 稠密关键帧用线性插值即可（Catmull-Rom 在高速挥砍段容易过冲）
        return new HeroClip(name, duration, false, false,
                tracks.toArray(new HeroClip.Track[0]), new HeroClip.Event[0]);
    }

    /** 行主序 16 元素 → JOML 矩阵（与 PoemMotionLibrary.matrix 同一约定）。 */
    private static Matrix4f rowMajor(float[] values) {
        return new Matrix4f().set(values).transpose();
    }

    private static Quaternionf rotationOf(Matrix4f matrix, Quaternionf out) {
        matrix.getUnnormalizedRotation(out);
        return out.normalize();
    }

    private static Quaternionf rotationOf(Matrix4f matrix) {
        return rotationOf(matrix, new Quaternionf());
    }

    /**
     * 按 ModelPart.translateAndRotate 的组合序（mulPose Z→Y→X，即 R = Rz·Ry·Rx）分解欧拉角，单位度。
     */
    private static float[] eulerZyx(Matrix4f rotation) {
        float m00 = rotation.m00(), m10 = rotation.m10(), m20 = rotation.m20();
        float m01 = rotation.m01(), m11 = rotation.m11();
        float m21 = rotation.m21(), m22 = rotation.m22();
        float sinY = Math.max(-1.0F, Math.min(1.0F, -m20));
        float y = (float) Math.asin(sinY);
        float x;
        float z;
        if (Math.abs(sinY) < 0.99995F) {
            x = (float) Math.atan2(m21, m22);
            z = (float) Math.atan2(m10, m00);
        } else {
            // 万向节锁：退化取 x=0，z 由剩余自由度恢复
            x = 0.0F;
            z = (float) Math.atan2(-m01, m11);
        }
        return new float[]{
                (float) Math.toDegrees(x),
                (float) Math.toDegrees(y),
                (float) Math.toDegrees(z),
        };
    }
}
