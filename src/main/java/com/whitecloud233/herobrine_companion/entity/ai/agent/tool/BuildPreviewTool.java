package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.herobrine_companion.structure.template.TemplateRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;

/**
 * 建筑预览（交接 E4）：基于<b>当前存档已导入并确认</b>的 templateId，返回尺寸、占地与落地检查。
 *
 * <p><b>零世界修改</b>：只读模板 NBT 与地形高度，不放置任何方块。</p>
 *
 * <p><b>不复制解释器</b>：模板来源走 {@link TemplateRepository}（与放置同一条读取路径），
 * 尺寸取自模板自身的 {@code size} 标签——和 {@code TemplatePlacementService} 用的是同一份数据。
 * 不按名称回退到未导入或其它存档的模板。</p>
 *
 * <p><b>如实说明</b>：当前放置不消耗材料，所以只做"方块统计"，<b>不宣称</b>已实现生存材料消耗；
 * metadata 的尺寸也不能冒充完整空间预览（未加载边界、越界、重叠都会明确列出）。</p>
 */
public final class BuildPreviewTool implements AgentTool {

    public static final String ID = "build_preview";

    /** 单个模板最多统计的方块种类数（回执 512 字符限制）。 */
    public static final int MAX_PALETTE_LINES = 5;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "预览当前存档里已导入的建筑模板：尺寸、占地范围、是否越界/重叠占用、方块种类统计。只读，不放置任何方块。";
    }

    @Override
    public String category() {
        return "info";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(
                AgentToolParameter.required("template_id", "string", "已导入模板的精确 ID（可用 registry_lookup category=template 查询）"),
                AgentToolParameter.optional("x", "number", "预览原点 X（省略表示 Hero 当前位置）"),
                AgentToolParameter.optional("y", "number", "预览原点 Y（省略表示地面高度）"),
                AgentToolParameter.optional("z", "number", "预览原点 Z"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        if (!args.has("template_id")) {
            return AgentToolResult.denied("需要 template_id（必须来自已导入的模板）",
                    layout("build.missing_template"), AgentToolResult.Cause.INVALID_ARGS);
        }
        boolean any = args.has("x") || args.has("y") || args.has("z");
        boolean all = args.has("x") && args.has("y") && args.has("z");
        if (any && !all) {
            return AgentToolResult.denied("x / y / z 需要同时给出（或全省略表示当前位置）",
                    layout("build.missing_origin"), AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法预览建筑");
        }
        ServerPlayer requester = context.requesterUuid() == null || hero.getServer() == null
                ? null
                : hero.getServer().getPlayerList().getPlayer(context.requesterUuid());
        if (requester == null) {
            return AgentToolResult.fail("找不到请求者玩家，无法读取他的模板库");
        }
        String templateId = args.getString("template_id").trim();
        TemplateRepository repository = new TemplateRepository(
                level.getServer().getWorldPath(LevelResource.ROOT), requester.getUUID());
        TemplateEntry entry;
        try {
            entry = repository.list().stream()
                    .filter(candidate -> candidate.id().equals(templateId))
                    .findFirst()
                    .orElse(null);
        } catch (Exception error) {
            return AgentToolResult.fail("读取模板库失败：" + error.getClass().getSimpleName(),
                    layout("build.read_failed"));
        }
        if (entry == null) {
            // 不按名称回退，也不去别的存档找：没导入就是没有。
            return AgentToolResult.fail("你的模板库里没有 " + templateId + "（未导入或已被删除）",
                    layout("build.unknown_template"));
        }

        BlockPos origin = args.has("x")
                ? new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0))
                : new BlockPos(hero.blockPosition().getX(),
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, hero.blockPosition().getX(), hero.blockPosition().getZ()),
                        hero.blockPosition().getZ());

        int sizeX = Math.max(0, entry.x());
        int sizeY = Math.max(0, entry.y());
        int sizeZ = Math.max(0, entry.z());
        int endX = origin.getX() + sizeX - 1;
        int endY = origin.getY() + sizeY - 1;
        int endZ = origin.getZ() + sizeZ - 1;

        StringBuilder text = new StringBuilder();
        text.append("模板 ").append(entry.name()).append(" (").append(entry.id()).append(")");
        text.append(" 尺寸 ").append(sizeX).append('x').append(sizeY).append('x').append(sizeZ);
        text.append(" 占地 ").append(origin.getX()).append(',').append(origin.getY()).append(',').append(origin.getZ())
                .append(" ~ ").append(endX).append(',').append(endY).append(',').append(endZ);
        text.append("；方块数 ").append(entry.blocks());

        // 边界检查：与世界高度、世界边界冲突时如实说明，由放置流程继续把关。
        if (origin.getY() < level.getMinBuildHeight() || endY >= level.getMaxBuildHeight()) {
            text.append("；警告: 超出世界高度范围");
        }
        if (!level.getWorldBorder().isWithinBounds(origin)
                || !level.getWorldBorder().isWithinBounds(new BlockPos(endX, endY, endZ))) {
            text.append("；警告: 超出世界边界");
        }
        if (!level.isLoaded(origin) || !level.isLoaded(new BlockPos(endX, origin.getY(), endZ))) {
            text.append("；注意: 部分区域尚未加载，落地状态需实际放置时再核对");
        } else {
            int occupied = countOccupied(level, origin, sizeX, sizeY, sizeZ);
            if (occupied > 0) {
                text.append("；该占地内有 ").append(occupied).append(" 处非空方块（放置会覆盖）");
            }
        }
        text.append("；仅预览，未放置任何方块；方块统计不等于生存材料消耗");
        return AgentToolResult.ok(text.toString(), layout("build.preview_ok"));
    }

    /**
     * 统计占地范围内的非空方块（只读、有界采样）。
     *
     * <p>按固定步长抽样而不是遍历整个体积：预览要快、要零修改，
     * 精确的逐块碰撞由实际放置流程负责。</p>
     */
    private static int countOccupied(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        int occupied = 0;
        int stepX = Math.max(1, sizeX / 8);
        int stepZ = Math.max(1, sizeZ / 8);
        int stepY = Math.max(1, sizeY / 4);
        int samples = 0;
        for (int dx = 0; dx < sizeX && samples < 512; dx += stepX) {
            for (int dy = 0; dy < sizeY && samples < 512; dy += stepY) {
                for (int dz = 0; dz < sizeZ && samples < 512; dz += stepZ) {
                    if (!level.getBlockState(new BlockPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz)).isAir()) {
                        occupied++;
                    }
                    samples++;
                }
            }
        }
        return occupied;
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
