package com.whitecloud233.modid.herobrine_companion.client.animation;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemHeroCombo;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.Util;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GUI 纸娃娃的终末之诗动作预览。
 *
 * <p>界面里的 dummy 实体从不真正 tick：{@code Entity.tickCount} 永远是 0，
 * 直接拿它当动作时间会让整套 rig 动作冻在首帧（只剩 partial tick 的抖动）。
 * 本类给被界面登记的实体一个按真实时间推进的预览时钟，并据此排一段
 * 「待机 → 连斩 → 待机」的演示，让纸娃娃真的在演终末之诗的动作。</p>
 *
 * <p>只在客户端渲染线程读写，键为弱引用：界面关闭、dummy 被 GC 后条目自动消失。</p>
 */
public final class HeroPoemPreview {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** 与实体 tick 同频：50ms 一个动作 tick。 */
    private static final double MILLIS_PER_TICK = 50.0D;
    /** 两段演示之间的待机时长（tick），让纸娃娃有呼吸、不是一直挥舞。 */
    private static final int REST_TICKS = 26;
    /** 演示用的攻击段（终末之诗四段地面连招）。 */
    private static final int[] DEMO_MOVES = {0, 1, 2, 3};
    /** 弯刀是右手武器；左手持械时姿势镜像由渲染层处理。 */
    private static final int DEMO_MODE = 0;

    private static final Map<HeroEntity, Long> START_MILLIS = new WeakHashMap<>();
    private static volatile boolean demoUnavailable;

    private HeroPoemPreview() {
    }

    /** 界面每帧渲染纸娃娃前调用：登记该实体并开始按真实时间推进动作。 */
    public static void drive(HeroEntity hero) {
        if (hero == null || demoUnavailable) {
            return;
        }
        START_MILLIS.putIfAbsent(hero, Util.getMillis());
    }

    /** 是否被界面登记为预览实体。 */
    public static boolean isDriven(HeroEntity hero) {
        return !demoUnavailable && hero != null && START_MILLIS.containsKey(hero);
    }

    /**
     * 预览动作时钟（tick，单调递增）；未登记返回 {@code null}。
     *
     * <p>返回值可能为 {@code null}，调用方据此回落到实体自己的 {@code tickCount}。</p>
     */
    public static Double clock(HeroEntity hero) {
        Long start = hero == null ? null : START_MILLIS.get(hero);
        if (start == null) {
            return null;
        }
        return (Util.getMillis() - start) / MILLIS_PER_TICK;
    }

    /** 结束预览（界面关闭时调用）。 */
    public static void release(HeroEntity hero) {
        if (hero != null) {
            START_MILLIS.remove(hero);
        }
    }

    /**
     * 关闭预览：动作资源缺失/损坏时调用，之后所有纸娃娃回落到基础姿态。
     *
     * <p>界面渲染没有服务端兜底，异常会直接把渲染线程打崩，所以这里宁可整块关掉。</p>
     */
    public static void disable() {
        demoUnavailable = true;
        START_MILLIS.clear();
    }

    /** 预览是否已被关闭（资源异常）。 */
    public static boolean unavailable() {
        return demoUnavailable;
    }

    /**
     * 纸娃娃当前该演的那一帧攻击段。{@code attacking=false} 表示处在两刀之间的待机。
     *
     * <p>时间轴完全由预览时钟推导，不写实体同步数据：dummy 不参与联网，
     * 也不该因为界面预览而改变真实状态。</p>
     */
    public static Demo demo(double now) {
        if (demoUnavailable) {
            return Demo.IDLE;
        }
        try {
            int[] durations = new int[DEMO_MOVES.length];
            int cycle = REST_TICKS;
            for (int i = 0; i < DEMO_MOVES.length; i++) {
                durations[i] = Math.max(1, PoemHeroCombo.durationForMove(DEMO_MODE, DEMO_MOVES[i]));
                cycle += durations[i] + REST_TICKS;
            }
            double phase = now % cycle;
            for (int i = 0; i < DEMO_MOVES.length; i++) {
                if (phase < REST_TICKS) {
                    return Demo.IDLE;
                }
                phase -= REST_TICKS;
                if (phase < durations[i]) {
                    return new Demo(true, i + 1, DEMO_MOVES[i], (int) phase, durations[i], DEMO_MODE);
                }
                phase -= durations[i];
            }
            return Demo.IDLE;
        } catch (RuntimeException error) {
            // 动作资源缺失时退回静态待机，界面不能因为预览而崩。
            demoUnavailable = true;
            LOGGER.warn("Poem preview demo disabled after a data error", error);
            return Demo.IDLE;
        }
    }

    /** 一帧演示：攻击段序号/招式/已播 tick/总时长/模式；{@link #IDLE} 表示待机。 */
    public record Demo(boolean attacking, int serial, int move, int ticks, int duration, int mode) {
        public static final Demo IDLE = new Demo(false, 0, -1, 0, 1, DEMO_MODE);

        public boolean leftHanded(HeroEntity hero) {
            return hero.getMainArm() == HumanoidArm.LEFT;
        }
    }
}
