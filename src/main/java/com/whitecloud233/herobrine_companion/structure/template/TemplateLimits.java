package com.whitecloud233.herobrine_companion.structure.template;

import java.io.IOException;

/** Shared import/transport bounds; neither client nor server trusts file metadata. */
public final class TemplateLimits {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_NBT_BYTES = 64 * 1024 * 1024;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_TEMPLATES = 128;
    public static final int MAX_NAME = 80;

    /**
     * 相对玩家的放置偏移上限（格）。
     *
     * <p>从 256 放宽到 4096：原先 463 格宽的「汀洲书院」这类大型建筑，
     * 连"放在哪里"都无法表达（原点偏移被直接拒绝），更谈不上放置。
     *
     * <p><b>为什么不是无限</b>：偏移最终要转成绝对坐标并逐区块加载，
     * 过大的值会跨越数十万个区块。4096 格已经能覆盖"在附近几个区域安置大型建筑"，
     * 同时保证位移运算不会溢出、区块票据数量可控。真正的边界仍由
     * 世界边界 / 建筑高度 / 区块数量检查兜底。</p>
     */
    public static final int MAX_PLACEMENT_OFFSET = 4096;

    /**
     * 单次放置允许跨过的最大区块数。
     *
     * <p>大建筑的问题不是方块数，而是<b>需要加载的区块数</b>——每个区块都要
     * 申请票据、等待 FULL 状态。463×323 约 1435 个区块，已经接近可行上限；
     * 再大就该拆分而不是硬塞。超过这个数量时明确拒绝并说明原因，而不是卡死服务器。</p>
     */
    public static final int MAX_PLACEMENT_CHUNKS = 4096;

    private TemplateLimits() { }

    /** 建筑包围盒跨过的区块数（按水平面估算，覆盖 x/z 两个方向）。 */
    public static long placementChunks(int x, int z) {
        long cx = (Math.max(1, x) + 15L) / 16L;
        long cz = (Math.max(1, z) + 15L) / 16L;
        return cx * cz;
    }

    public static long volume(int x, int y, int z) throws IOException {
        if (x < 1 || y < 1 || z < 1) {
            throw new IOException("Template dimensions must be positive");
        }
        try { return Math.multiplyExact(Math.multiplyExact((long) x, y), z); }
        catch (ArithmeticException error) { throw new IOException("Template volume cannot be represented", error); }
    }

    /** Dense formats address Java arrays with int indices; sparse NBT bounding boxes use long volumes. */
    static int denseVolume(int x, int y, int z) throws IOException {
        try { return Math.toIntExact(volume(x, y, z)); }
        catch (ArithmeticException error) { throw new IOException("Template block array cannot be represented", error); }
    }
}
