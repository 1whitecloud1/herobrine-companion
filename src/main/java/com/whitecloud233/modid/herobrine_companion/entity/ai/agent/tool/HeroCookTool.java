package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.util.AiItemNaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code hero_cook}（交接 D4）：查询实际可做的配方，并按既有厨具流程开始制作。
 *
 * <p><b>复用而不是重写</b>：所有配方遍历、材料扣除、份数计算都走
 * {@link CookingService} 的既有入口（{@code getCookOptions} / {@code requestCookSelection} /
 * {@code getCookOptionMaxRepeat}）。Agent 不复制一套配方逻辑；
 * UI、手动邀请和 Agent 因此调的是同一个业务方法。</p>
 *
 * <p><b>诚实性</b>：{@code requestCookSelection} 返回 true 只表示"已安排动作"，
 * <b>不是制作完成</b>。回执明确写"已开始，尚未完成"，并把实际生效的份数（可能被限幅）说清楚。</p>
 */
public final class HeroCookTool implements AgentTool {

    public static final String ID = "hero_cook";

    /** 查询时最多返回的配方条数（回执 512 字符限制）。 */
    public static final int MAX_OPTIONS = 6;

    /** 允许交互的厨具距离平方（4 格²）：沿用做菜邀请的近距离语义。 */
    private static final double MAX_STATION_DISTANCE_SQR = 16.0D;

    /** 动作："query" 只读查询 / "start" 开始制作。 */
    public static final String ACTION_QUERY = "query";
    public static final String ACTION_START = "start";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "查询某个厨具当前实际能做的配方（含最大份数），或按配方 ID 与份数开始制作。只报告真实可执行数量；开始不等于完成。";
    }

    @Override
    public String category() {
        return "action";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(
                AgentToolParameter.required("action", "string", "query=查询可用配方，start=开始制作"),
                AgentToolParameter.required("x", "number", "厨具 X 坐标"),
                AgentToolParameter.required("y", "number", "厨具 Y 坐标"),
                AgentToolParameter.required("z", "number", "厨具 Z 坐标"),
                AgentToolParameter.optional("recipe_id", "string", "start 时的配方 ID（必须来自 query 返回的真实 ID）"),
                AgentToolParameter.optional("count", "number", "start 时想要的份数（会被实际最大份数限幅）"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String action = args.getString("action").trim().toLowerCase(java.util.Locale.ROOT);
        if (!ACTION_QUERY.equals(action) && !ACTION_START.equals(action)) {
            return AgentToolResult.denied("action 只允许 query 或 start",
                    layout("cook.invalid_action"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (!args.has("x") || !args.has("y") || !args.has("z")) {
            return AgentToolResult.denied("需要厨具坐标 x / y / z",
                    layout("cook.missing_pos"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (ACTION_START.equals(action) && !args.has("recipe_id")) {
            return AgentToolResult.denied("start 需要 recipe_id（请先用 action=query 查询真实配方 ID）",
                    layout("cook.missing_recipe"), AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法做菜");
        }
        BlockPos pos = new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0));
        if (!level.isLoaded(pos)) {
            return AgentToolResult.fail("那个厨具所在的区块没有加载，我过不去也看不到",
                    layout("cook.chunk_unloaded"));
        }
        if (hero.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_STATION_DISTANCE_SQR) {
            return AgentToolResult.fail("那个厨具离我太远了，需要先走过去", layout("cook.too_far"));
        }
        if (!CookingService.isCookwareStation(level, pos)) {
            // 区分"不是厨具"与"后端不可用"：前者是目标错误，后者是兼容模组缺失。
            return AgentToolResult.fail("那个方块不是可用的厨具（可能没有安装对应厨具模组）",
                    layout("cook.not_station"));
        }
        String action = args.getString("action").trim().toLowerCase(java.util.Locale.ROOT);
        return ACTION_QUERY.equals(action) ? query(hero, pos) : start(hero, level, pos, args);
    }

    /** 只读查询：返回真实 recipeId、结果、最大份数与缺失材料（由后端实际计算）。 */
    private AgentToolResult query(HeroEntity hero, BlockPos pos) {
        List<CookOption> options = CookingService.getCookOptions(hero, pos);
        if (options.isEmpty()) {
            return AgentToolResult.ok("这个厨具现在没有可做的配方（可能缺材料，或没有安装厨具后端）",
                    layout("cook.none"));
        }
        StringBuilder text = new StringBuilder("可做配方 ").append(options.size()).append(" 条");
        int shown = 0;
        for (CookOption option : options) {
            if (shown >= MAX_OPTIONS) {
                break;
            }
            text.append("；").append(option.recipeId())
                    .append(" -> ").append(describeResult(option))
                    .append("，最多 ").append(option.maxRepeatCount()).append(" 份");
            shown++;
        }
        if (options.size() > shown) {
            text.append("；另有 ").append(options.size() - shown).append(" 条未显示");
        }
        return AgentToolResult.ok(text.toString(), layout("cook.query_ok"));
    }

    /** 开始制作：校验配方真实存在、份数被限幅，并如实说明"已开始"不等于"已完成"。 */
    private AgentToolResult start(HeroEntity hero, ServerLevel level, BlockPos pos, AgentToolArgs args) {
        ServerPlayer requester = resolveRequester(hero, args);
        if (requester == null) {
            return AgentToolResult.fail("找不到请求者玩家，无法开始制作");
        }
        ResourceLocation recipeId;
        try {
            recipeId = new ResourceLocation(args.getString("recipe_id").trim());
        } catch (RuntimeException error) {
            return AgentToolResult.denied("recipe_id 不是合法的资源 ID",
                    layout("cook.invalid_recipe"), AgentToolResult.Cause.INVALID_ARGS);
        }
        List<CookOption> options = CookingService.getCookOptions(hero, pos);
        if (options.stream().noneMatch(option -> recipeId.equals(option.recipeId()))) {
            // 不能仅用名称虚构配方：只接受查询返回过的真实 ID。
            return AgentToolResult.fail("这个厨具做不了 " + recipeId + "（请先用 query 查询真实配方）",
                    layout("cook.unknown_recipe"));
        }
        int wanted = Math.max(CookingRules.MIN_COOK_REPEAT_COUNT, args.getInt("count", 1));
        int maxRepeat = CookingService.getCookOptionMaxRepeat(hero, pos, recipeId);
        int actual = Math.min(CookingRules.clampRepeatCount(wanted), maxRepeat);
        if (actual <= 0) {
            return AgentToolResult.fail("这个配方现在一份也做不了（材料不足）", layout("cook.no_material"));
        }
        boolean scheduled = CookingService.requestCookSelection(hero, requester, pos, recipeId, actual);
        if (!scheduled) {
            return AgentToolResult.fail("制作没有真正开始（可能是权限、距离或材料在提交时已变化）",
                    layout("cook.not_started"));
        }
        boolean clamped = actual < wanted;
        String text = "已安排制作 " + recipeId + "，实际份数 " + actual
                + (clamped ? "（请求 " + wanted + " 份，已按材料/上限限幅）" : "")
                + "；这只是已开始，尚未完成。";
        return AgentToolResult.ok(text, clamped ? layout("cook.started_clamped") : layout("cook.started"));
    }

    private static ServerPlayer resolveRequester(HeroEntity hero, AgentToolArgs args) {
        if (hero.getServer() == null) {
            return null;
        }
        String requesterUuid = args.getString("requester_uuid");
        if (!requesterUuid.isBlank()) {
            try {
                ServerPlayer player = hero.getServer().getPlayerList()
                        .getPlayer(java.util.UUID.fromString(requesterUuid.trim()));
                if (player != null) {
                    return player;
                }
            } catch (RuntimeException ignored) {
                // 落到 owner 兜底
            }
        }
        java.util.UUID owner = hero.getOwnerUUID();
        return owner == null ? null : hero.getServer().getPlayerList().getPlayer(owner);
    }

    private static String describeResult(CookOption option) {
        String name = AiItemNaming.describe(option.result());
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(option.result().getItem()));
        return (name == null || name.isBlank() ? id : name) + " x" + option.result().getCount();
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
