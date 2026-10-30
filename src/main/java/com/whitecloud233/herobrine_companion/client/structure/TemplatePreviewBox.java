package com.whitecloud233.herobrine_companion.client.structure;

import net.minecraft.core.BlockPos;

/**
 * 建筑预览的线框数据（客户端）。
 *
 * <p><b>为什么只存包围盒 + 少量关键点</b>：463×141×323 的建筑有 1,435,030 个方块，
 * 把每个方块都发到客户端再逐个画线是不可能的（几十 MB 流量 + 每帧百万级顶点）。
 * 预览只需要回答"它有多大、落在哪、会不会越界"，这些用<b>一个盒子 + 若干角点标记</b>
 * 就能表达，数据量恒定在几十字节。</p>
 *
 * <p><b>单一职责</b>：只保存几何与时间戳，不做渲染、不发包。</p>
 */
public final class TemplatePreviewBox {

    /** 预览存活时间（毫秒）：到点自动消失，避免玩家忘了关掉后一直画。 */
    public static final long LIFETIME_MS = 60_000L;

    private static volatile TemplatePreviewBox current;

    private final BlockPos min;
    private final BlockPos max;
    private final String label;
    private final long createdAt;

    public TemplatePreviewBox(BlockPos min, BlockPos max, String label) {
        this.min = min;
        this.max = max;
        this.label = label == null ? "" : label;
        this.createdAt = System.currentTimeMillis();
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public String label() {
        return label;
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    /** 距离原点最远的那个角（用于画对角线）。 */
    public BlockPos opposite() {
        return new BlockPos(max.getX(), max.getY(), min.getZ());
    }

    public boolean expired() {
        return System.currentTimeMillis() - createdAt > LIFETIME_MS;
    }

    // ---- 当前预览（进程内唯一） ----

    public static void show(BlockPos min, BlockPos max, String label) {
        current = new TemplatePreviewBox(min, max, label);
    }

    /**
     * 收包入口：全 0 表示清空，否则建立预览。
     *
     * <p>供网络层按坐标调用，避免网络包直接 new 这个类（保持"包只搬数据"的分工）。</p>
     */
    public static void accept(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String label) {
        if (minX == 0 && minY == 0 && minZ == 0 && maxX == 0 && maxY == 0 && maxZ == 0) {
            current = null;
            return;
        }
        current = new TemplatePreviewBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), label);
    }

    /** 取当前预览；已过期则自动清除并返回 null。 */
    public static TemplatePreviewBox current() {
        TemplatePreviewBox box = current;
        if (box != null && box.expired()) {
            current = null;
            return null;
        }
        return box;
    }

    public static void clear() {
        current = null;
    }
}
