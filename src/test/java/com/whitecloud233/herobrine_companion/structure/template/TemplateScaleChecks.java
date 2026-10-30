package com.whitecloud233.herobrine_companion.structure.template;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 大建筑放置与预览的契约检查。
 *
 * <p>覆盖本次改动的三条硬约束：</p>
 * <ol>
 *   <li>偏移上限已放宽（463 格建筑能表达原点）；</li>
 *   <li>区块数有上限且能正确估算（真正的性能瓶颈是区块数，不是方块数）；</li>
 *   <li>预览数据是<b>定长</b>的（不随建筑体积增长），否则大建筑会撑爆网络包。</li>
 * </ol>
 *
 * <p>故意不启动游戏：这些都是纯计算与常量契约，能在 JVM 里确定性地验证。</p>
 */
public final class TemplateScaleChecks {

    public static void main(String[] args) throws Exception {
        int checks = 0;

        // ---- 1. 偏移上限放宽到 4096（原 256 连 463 格建筑都放不下） ----
        checks += check(TemplateLimits.MAX_PLACEMENT_OFFSET >= 4096,
                "偏移上限必须至少 4096，否则大型建筑无法表达放置位置");
        checks += check(TemplateLimits.MAX_PLACEMENT_OFFSET >= 463,
                "汀洲书院这类 463 格建筑必须能放下");
        // 越界仍要判得出来（放宽不等于不设限）。
        checks += check(Math.abs(5000) > TemplateLimits.MAX_PLACEMENT_OFFSET,
                "超出上限的偏移仍必须被判定为越界");
        checks += check(Math.abs(-4096) <= TemplateLimits.MAX_PLACEMENT_OFFSET,
                "负方向同样按绝对值判定");

        // ---- 2. 区块数估算与上限 ----
        // 463×323 的真实建筑：约 29 × 21 = 609 个区块，必须在上限内。
        long irrChunks = TemplateLimits.placementChunks(463, 323);
        checks += check(irrChunks == 29L * 21L, "463×323 应估算为 609 个区块，实际 " + irrChunks);
        checks += check(irrChunks <= TemplateLimits.MAX_PLACEMENT_CHUNKS,
                "汀洲书院必须落在区块上限内，否则仍会被拒");
        // 正好 16×16 = 1 个区块；17×17 = 4 个（跨边界）；1×1 = 1 个。
        checks += check(TemplateLimits.placementChunks(16, 16) == 1L, "16×16 应算 1 个区块");
        checks += check(TemplateLimits.placementChunks(17, 17) == 4L, "17×17 应算 4 个区块");
        checks += check(TemplateLimits.placementChunks(1, 1) == 1L, "极小建筑也应算 1 个区块");
        // 0 或负数按 1 处理，不能除出 0 或负数。
        checks += check(TemplateLimits.placementChunks(0, 0) == 1L, "非正尺寸必须按 1 处理");
        // 超大建筑要被挡住。
        checks += check(TemplateLimits.placementChunks(4096, 4096) > TemplateLimits.MAX_PLACEMENT_CHUNKS,
                "4096×4096 必须超过区块上限（明确拒绝而不是卡死）");

        // ---- 3. 预览数据定长：不能随建筑体积增长 ----
        Class<?> packet = Class.forName("com.whitecloud233.herobrine_companion.network.TemplatePreviewPacket");
        int intFields = 0;
        for (java.lang.reflect.Field field : packet.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String type = field.getType().getSimpleName();
            if (!type.equals("int") && !type.equals("String")) {
                throw new AssertionError("预览包不允许持有 " + type + " 字段（会导致包体随建筑体积增长）: "
                        + field.getName());
            }
            if (type.equals("int")) {
                intFields++;
            }
        }
        checks += check(intFields == 6, "预览包必须是 6 个 int（包围盒两角点），实际 " + intFields);

        // 预览盒本身也只存两个角点 + 标签，不含方块列表。
        Class<?> box = Class.forName("com.whitecloud233.herobrine_companion.client.structure.TemplatePreviewBox");
        boolean hasCollection = false;
        for (java.lang.reflect.Field field : box.getDeclaredFields()) {
            if (java.util.Collection.class.isAssignableFrom(field.getType())
                    || java.util.Map.class.isAssignableFrom(field.getType())) {
                hasCollection = true;
            }
        }
        checks += check(!hasCollection, "预览盒不得持有集合（大建筑会撑爆内存）");

        // 尺寸换算：463×141×323 的包围盒必须精确还原。
        var ctor = box.getDeclaredConstructor(net.minecraft.core.BlockPos.class,
                net.minecraft.core.BlockPos.class, String.class);
        ctor.setAccessible(true);
        Object instance = ctor.newInstance(
                new net.minecraft.core.BlockPos(0, 64, 0),
                new net.minecraft.core.BlockPos(462, 204, 322),
                "汀洲书院");

        checks += check((Integer) box.getMethod("sizeX").invoke(instance) == 463, "sizeX 必须为 463");
        checks += check((Integer) box.getMethod("sizeY").invoke(instance) == 141, "sizeY 必须为 141");
        checks += check((Integer) box.getMethod("sizeZ").invoke(instance) == 323, "sizeZ 必须为 323");

        // ---- 4. 通道一致性：包注册在哪个通道，就只能由哪个通道发送 ----
        // 实际事故：TemplatePreviewPacket 注册在 TemplateSyncChannel，
        // 却用 PacketHandler.sendToPlayer（主通道）发送，服务端抛
        // IllegalArgumentException: Invalid message ... on channel herobrine_companion:main。
        checks += check(templatePacketsOnlySentOnTemplateChannel(),
                "模板相关包只能经 TemplateSyncChannel 发送（用主通道会抛 Invalid message）");

        System.out.println("TemplateScaleChecks: " + checks + " checks passed");
    }

    /**
     * 源码契约：{@code TemplatePlacementService} 不得用主通道发送模板包。
     *
     * <p>主通道与模板通道是两张不同的注册表；把注册在模板通道的包交给
     * {@code PacketHandler.sendToPlayer} 会在运行时抛 {@code Invalid message}，
     * 编译期完全看不出来（这正是本次线上日志里的错误）。</p>
     *
     * @return true 表示用法正确
     */
    private static boolean templatePacketsOnlySentOnTemplateChannel() {
        // 测试类与 TemplatePlacementService 同包，直接用包名推导源码路径。
        java.nio.file.Path service = java.nio.file.Path.of(
                "src/main/java/" + TemplateScaleChecks.class.getPackageName().replace('.', '/')
                        + "/TemplatePlacementService.java");
        try {
            String source = java.nio.file.Files.readString(service, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : source.split("\\R")) {
                if (line.contains("PacketHandler.sendToPlayer") || line.contains("PacketHandler.sendToServer")) {
                    return false;
                }
            }
            return source.contains("TemplatePreviewPacket(");
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 TemplatePlacementService 源码: " + service + " -> " + error);
        }
    }

    private static int check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        return 1;
    }

    private TemplateScaleChecks() {
    }
}
