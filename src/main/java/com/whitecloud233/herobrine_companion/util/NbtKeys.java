package com.whitecloud233.herobrine_companion.util;

/**
 * NBT / 持久化数据键的单一来源。
 *
 * <p><b>为什么不直接写字面量：</b>这些键名会写进存档（实体 NBT、玩家 persistentData、SavedData）。
 * 拼错一个字符，编译器不会报错、序列化不会报错，只会静默地读不到数据——
 * 表现为「某个功能偶尔不生效」「存档里某个状态莫名丢失」，而且只在特定路径上出现，极难复现。</p>
 *
 * <p><b>为什么不把同一个键复用给不同对象：</b>见下方 {@link #PLAYER_IN_CHALLENGE} 与
 * {@link #HERO_IN_CHALLENGE} 的说明——两者值相同，但语义分离。</p>
 */
public final class NbtKeys {

    private NbtKeys() {
    }

    // ==========================================================================
    // 挑战模式标记（HeroChallengeManager / HeroChallengeState / 各处拦截判定）
    // ==========================================================================

    /**
     * 「这名玩家正在挑战中」。写在 {@code player.getPersistentData()}。
     *
     * <p>用于拦截玩家行为：禁止放置/破坏方块、禁止飞行、挑战占用锁等。</p>
     */
    public static final String PLAYER_IN_CHALLENGE = "IsChallengeActive";

    /**
     * 「这只 Hero 正在挑战中」。写在 {@code hero.getPersistentData()}。
     *
     * <p>用于驱动 Boss 侧逻辑：{@code registerGoals} 跳过日常 AI、免检跨维度传送、
     * 阶段计时恢复等。</p>
     *
     * <p><b>注意：值刻意与 {@link #PLAYER_IN_CHALLENGE} 相同。</b>它们历史上就是同一个键名，
     * 旧存档里两种载体写的是同一串字符，改动这个值会让所有既有存档丢状态。
     * 但两者是<b>两份互相独立的数据</b>，没有任何机制保证同步：
     * 玩家死亡重建时 persistentData 不继承（见 {@code PlayerLifecycleHandler} 的白名单），
     * Hero 跨维度会被重建实体、需要手动在新实体上补写。用两个不同名字的常量，
     * 是为了让「读的是哪一个对象」在代码里一目了然，避免写成
     * 「拿玩家标记判断 Hero 状态」这类虽然能编译、但语义错位的代码。</p>
     */
    public static final String HERO_IN_CHALLENGE = "IsChallengeActive";

    // ==========================================================================
    // 假死演出（「虚晃一枪」）阶段标记
    // ==========================================================================

    /**
     * 「Boss 正处于假死演出中」。写在 {@code hero.getPersistentData()}。
     *
     * <p>演出期间 Boss 绝对无敌、AI 清空、世界向玩家坍塌。演出结束由
     * {@code CPacketCollapseFinished} 或超时兜底推进到 {@code endChallenge}。</p>
     */
    public static final String HERO_FAKE_OUT_PHASE = "IsFakeOutPhase";

    /**
     * 「这名玩家正处于假死演出中」。写在 {@code player.getPersistentData()}。
     *
     * <p>与 {@link #HERO_FAKE_OUT_PHASE} 是<b>两份独立数据</b>，值刻意相同以兼容旧存档。
     * 玩家侧用于锁半颗心、关闭 UI、防止演出期间被鞭尸。</p>
     */
    public static final String PLAYER_FAKE_OUT_PHASE = "HeroFakeOutPhase";

    /** Boss 的阶段计时（tick），写在 {@code hero.getPersistentData()}，用于跨存档续接演出进度。 */
    public static final String HERO_CHALLENGE_PHASE_TICKS = "ChallengePhaseTicks";

    // ==========================================================================
    // 臣服（Submission）压制标记
    //
    // 注意这一组的载体与前几组不同：它写在**任意 Mob** 的 persistentData 上，
    // 由 {@code MobSubmissionMixin}（@Mixin(Mob.class)）统一读取并强制姿态。
    // Hero 的光环、Jean 的登场、觉醒容器释放都会经过同一个开关，因此这里
    // 只有一套常量，不按实体类型拆分——它们的语义本来就是同一个：
    // 「这只生物当前被压制，不许动、不许跳、朝向由写入方锁定」。
    // ==========================================================================

    /** 该生物是否处于臣服压制中。写在任意 {@code Mob.getPersistentData()}。 */
    public static final String MOB_SUBMISSION = "HeroSubmission";

    /** 臣服期间被锁定的朝向（度）。由写入方算好后交给 Mixin 覆盖渲染。 */
    public static final String MOB_SUBMISSION_YAW = "HeroSubmissionYaw";

    // ==========================================================================
    // 末影龙专用：臣服时的定点锚定
    //
    // 这一组只在 EnderDragon 上使用，由 JeanSubmissionAnchor 独占读写，
    // 用来把龙钉在释放位置并保持坐姿。与上面的 MOB_SUBMISSION 是两个独立的
    // 状态：龙可以处于 MOB_SUBMISSION 但不锚定（如骑乘时），也可以被锚定。
    // ==========================================================================

    /** 龙是否被定点锚定（锁定位置与朝向）。 */
    public static final String DRAGON_ANCHORED = "HeroSubmissionStationary";

    /** 锚点坐标，仅在 {@link #DRAGON_ANCHORED} 为真时有效。 */
    public static final String DRAGON_ANCHOR_X = "HeroSubmissionAnchorX";
    public static final String DRAGON_ANCHOR_Y = "HeroSubmissionAnchorY";
    public static final String DRAGON_ANCHOR_Z = "HeroSubmissionAnchorZ";

    // ==========================================================================
    // Jean（末影龙家族成员）骑乘状态
    //
    // 全部写在 {@code EnderDragon.getPersistentData()} 上，除了 FRONT_SEAT 系列
    // 写在骑在前座的 Hero 身上。读写方横跨事件、容器洗刷与三个 Mixin，
    // 历史上各自复制了一份私有常量，这里统一为一处。
    // ==========================================================================

    /** 骑在龙前座的那只 Hero 的 UUID。 */
    public static final String JEAN_FRONT_HERO = "HerobrineCompanionJeanFrontHero";

    /** 前座 Hero 的上座时间（gameTime），用于防止同 tick 反复上下。 */
    public static final String JEAN_FRONT_HERO_TIME = "HerobrineCompanionJeanFrontHeroTime";

    /** 玩家当前是否骑在这条龙上。 */
    public static final String JEAN_PLAYER_RIDDEN = "HerobrineCompanionJeanPlayerRidden";

    /** 骑乘期间临时关闭龙 AI，下龙后需恢复。 */
    public static final String JEAN_MOUNT_NO_AI = "HerobrineCompanionJeanMountNoAi";

    /** 这只 Hero 正坐在龙前座上。写在 {@code hero.getPersistentData()}。 */
    public static final String HERO_FRONT_SEAT = "HerobrineCompanionJeanFrontSeat";

    /** 前座 Hero 的 AI 被临时关闭，离座时需恢复。写在 {@code hero.getPersistentData()}。 */
    public static final String HERO_FRONT_SEAT_NO_AI = "HerobrineCompanionJeanFrontSeatNoAi";

    /** 实体标签：标记这条龙是 Jean。用 {@code Entity#addTag}，不是 NBT 键。 */
    public static final String JEAN_ENTITY_TAG = "herobrine_companion_jean";
}
