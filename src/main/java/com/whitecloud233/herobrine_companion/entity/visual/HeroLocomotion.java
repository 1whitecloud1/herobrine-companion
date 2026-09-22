package com.whitecloud233.herobrine_companion.entity.visual;

/**
 * 英雄移动/待机程序化动画 —— 移植自 Bedrock hb1 组件包 animations/hero_move.animation.json
 * 的 Molang 数学表达式(animation.hero.move / animation.hero.idle)。
 *
 * <p>说明:
 * <ul>
 *   <li>Bedrock 的 move 用 <code>query.anim_time</code>(= 行走距离换算)驱动;本端保持 Java 原有的
 *       2 秒行走环节奏,只移植<b>姿态数学</b>(摆动模式/幅度),节拍由调用方提供(0..1 相位);</li>
 *   <li>位置单位为"模型单位"(与骨架 pivot 同单位,1 单位 = 1/16 格)——Bedrock 实体动画的
 *       position 与 pivot 一致,飞行动画的现有 Java 移植(FLYING_POSE + floatBodyY)可证实该换算;</li>
 *   <li>bss* elbow/knee 关节盖曲线(半角闭合)不在本类移植:Java 模型无盖几何,弯曲已由
 *       下臂/下腿承载(见 HeroClip 类注释)。</li>
 * </ul>
 */
public final class HeroLocomotion {

    private HeroLocomotion() {
    }

    /**
     * 计算 2 秒行走环在相位 phase(0..1)时的全身姿态。
     *
     * @param phase 0..1(一个完整步态周期)
     * @param out   长 32 数组:[0..29] 规范 10 关节旋转(度,顺序同 HeroClip.J_*),
     *              [30] = 躯干 position.x(模型单位), [31] = 躯干 position.y(模型单位)
     */
    public static void computeWalkPose(float phase, float[] out) {
        float d = phase * 360.0F;          // sin/cos 统一以 360 度为一周期
        float d2 = phase * 720.0F;
        float sinD = Mth.sinDeg(d);
        float cosD = Mth.cosDeg(d);
        float sinD2 = Mth.sinDeg(d2);
        float cosD2 = Mth.cosDeg(d2);

        for (int i = 0; i < 30; i++) {
            out[i] = 0.0F;
        }
        // body position(模型单位)
        out[30] = sinD * 0.12F;
        out[31] = -0.15F - cosD2 * 0.18F;
        // body rotation
        out[3] = 1.2F;
        out[4] = cosD * 1.5F;
        out[5] = sinD * 0.65F;
        // head
        out[0] = -0.8F;
        out[1] = -cosD * 1.0F;
        out[2] = -sinD * 0.5F;
        // right arm / fore arm
        out[6] = -cosD * 22.0F;
        out[8] = 2.0F + sinD * 0.5F;
        out[12] = -11.0F - Mth.cosDeg(d - 30.0F) * 4.0F;
        // left arm / fore arm
        out[9] = cosD * 22.0F;
        out[11] = -2.0F + sinD * 0.5F;
        out[15] = -11.0F + Mth.cosDeg(d - 30.0F) * 4.0F;
        // legs
        out[18] = cosD * 27.0F - 2.0F;
        out[21] = -cosD * 27.0F - 2.0F;
        // lower legs(着地阶段二次 kick)
        float sqR = Math.max(0.0F, sinD);
        float sqL = Math.max(0.0F, -sinD);
        out[24] = 2.0F + sqR * sqR * 22.0F;
        out[27] = 2.0F + sqL * sqL * 22.0F;
    }

    /**
     * 基础待机微动(animation.hero.idle,life_time 驱动,秒):
     * 躯干轻微起伏/回旋、头微倾、双臂微摆。值很小,叠加在 vanilla 待机之上。
     * 输出布局同 {@link #computeWalkPose}(positions 在 [30]/[31])。
     */
    public static void computeIdleMicro(float lifeSeconds, float[] out) {
        for (int i = 0; i < 30; i++) {
            out[i] = 0.0F;
        }
        float deg90 = lifeSeconds * 90.0F;
        float deg45 = lifeSeconds * 45.0F;
        out[30] = 0.0F;
        out[31] = Mth.sinDeg(deg90) * 0.06F;
        out[3] = Mth.sinDeg(deg90 + 20.0F) * 0.4F;
        out[5] = Mth.sinDeg(deg45) * 0.25F;
        out[2] = -Mth.sinDeg(deg45) * 0.25F;      // head.z
        out[8] = 1.5F + Mth.sinDeg(deg90) * 0.5F; // rightArm.z
        out[11] = -1.5F - Mth.sinDeg(deg90) * 0.5F; // leftArm.z
    }

    // 简易度制三角函数(避免在渲染热路径上做重复的 DEG2RAD 转换)
    private static final class Mth {
        static float sinDeg(float deg) {
            return (float) Math.sin(Math.toRadians(deg));
        }

        static float cosDeg(float deg) {
            return (float) Math.cos(Math.toRadians(deg));
        }
    }
}