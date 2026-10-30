package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.AiItemNaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code block_query}（交接 D3）：查询指定方块、容器与加工状态。<b>严格只读</b>。
 *
 * <p><b>边界</b>：</p>
 * <ul>
 *   <li>不打开玩家 GUI、不推进机器、不取出物品；</li>
 *   <li>"未加载 / 没有容器 / 不可访问"分别返回不同原因，不把"未查询"说成"没有"；</li>
 *   <li>容器清单分页，每页有上限，并标注省略数量——{@code AgentToolResultPacket} 只有 512 字符，
 *       不能把大容器整个塞进字符串。</li>
 * </ul>
 *
 * <p>附近实体请直接使用本轮注入的客户端环境数据，本工具不提供"扫描附近一切"。</p>
 */
public final class BlockQueryTool implements AgentTool {

    public static final String ID = "block_query";

    /** 单页最多返回的槽位条数。 */
    public static final int PAGE_SIZE = 8;

    /** 允许查询的最大距离平方（6 格² = 36）：沿用现有交互距离量级，不做无限远查询。 */
    private static final double MAX_QUERY_DISTANCE_SQR = 36.0D;

    /** 单个槽位的描述长度上限（拼接进 512 字符的回执里）。 */
    private static final int SLOT_TEXT_LIMIT = 48;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "只读查询指定方块或你正看着的方块：注册 ID、状态，以及（支持时）容器槽位/数量。不打开 GUI，不取物，不推进机器。";
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
                AgentToolParameter.optional("x", "number", "方块 X 坐标；与 y/z 同时省略表示玩家当前指向的方块"),
                AgentToolParameter.optional("y", "number", "方块 Y 坐标"),
                AgentToolParameter.optional("z", "number", "方块 Z 坐标"),
                AgentToolParameter.optional("page", "number", "容器清单页码，从 0 开始，默认 0"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        boolean any = args.has("x") || args.has("y") || args.has("z");
        boolean all = args.has("x") && args.has("y") && args.has("z");
        if (any && !all) {
            return AgentToolResult.denied("x / y / z 必须同时给出，或全部省略（表示你正看着的方块）",
                    layout("block_query.missing_pos"), AgentToolResult.Cause.INVALID_ARGS);
        }
        int page = args.getInt("page", 0);
        if (page < 0) {
            return AgentToolResult.denied("page 不能为负数", layout("block_query.invalid_page"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我看不到这个世界，无法查询方块");
        }
        ServerPlayer requester = context.requesterUuid() == null || hero.getServer() == null
                ? null
                : hero.getServer().getPlayerList().getPlayer(context.requesterUuid());

        BlockPos pos = resolveTarget(hero, requester, args);
        if (pos == null) {
            return AgentToolResult.fail("你没有明确指向任何方块，请直接给出坐标",
                    layout("block_query.no_target"));
        }
        // 目标引用在执行时再次验证，不能用过期的"这个箱子"改/读别的方块。
        if (!level.isLoaded(pos)) {
            return AgentToolResult.fail("那个位置的区块还没有加载，我看不到它（这不代表那里是空的）",
                    layout("block_query.chunk_unloaded"));
        }
        double distanceSqr = hero.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distanceSqr > MAX_QUERY_DISTANCE_SQR) {
            return AgentToolResult.fail("那个方块离我太远了（超过 6 格）", layout("block_query.too_far"));
        }

        BlockState state = level.getBlockState(pos);
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        StringBuilder text = new StringBuilder();
        text.append("方块 ").append(blockId)
                .append(" @ ").append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
        text.append(" 状态=").append(describeState(state));

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            text.append("；无方块实体（不是容器）");
            return AgentToolResult.ok(text.toString(), layout("block_query.success"));
        }
        text.append("；方块实体=").append(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
        if (!(blockEntity instanceof Container container)) {
            // 有方块实体但没有容器接口：区分于"未加载"和"不可访问"。
            text.append("；没有容器接口");
            return AgentToolResult.ok(text.toString(), layout("block_query.success"));
        }

        int page = Math.max(0, args.getInt("page", 0));
        int size = container.getContainerSize();
        int from = Math.min(page * PAGE_SIZE, size);
        int to = Math.min(from + PAGE_SIZE, size);
        text.append("；容器槽位=").append(size);
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = container.getItem(slot);
            text.append("；[#").append(slot).append("] ");
            text.append(stack.isEmpty() ? "空" : describeStack(stack));
        }
        if (to < size) {
            text.append("；本页 ").append(from).append('-').append(to - 1)
                    .append("，其余 ").append(size - to).append(" 个槽位未显示（用 page=").append(page + 1).append(" 继续）");
        }
        return AgentToolResult.ok(text.toString(), layout("block_query.success"));
    }

    /**
     * 解析查询目标：显式坐标优先，否则取玩家当前指向的方块。
     *
     * <p>指向模糊时返回 null，由调用方要求澄清，而不是随便挑一个附近的方块。</p>
     */
    private static BlockPos resolveTarget(HeroEntity hero, ServerPlayer requester, AgentToolArgs args) {
        if (args.has("x") && args.has("y") && args.has("z")) {
            return new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0));
        }
        if (requester == null) {
            return null;
        }
        HitResult hit = requester.pick(6.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    /** 只输出与判断有关的必要状态，不倾倒完整 BlockState toString。 */
    private static String describeState(BlockState state) {
        List<String> parts = new ArrayList<>();
        for (var property : state.getProperties()) {
            if (property.getName().equals("waterlogged") || property.getName().equals("powered")
                    || property.getName().equals("lit") || property.getName().equals("open")
                    || property.getName().equals("facing") || property.getName().equals("half")) {
                parts.add(property.getName() + "=" + state.getValue(property));
            }
        }
        return parts.isEmpty() ? "-" : String.join(",", parts);
    }

    /** 物品描述保留身份（id + 数量 + 名称），长度受限，避免撑爆 512 字符回执。 */
    private static String describeStack(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String text = key + " x" + stack.getCount();
        if (stack.hasTag()) {
            // 只说明"有额外数据"，不倾倒原始 NBT（可能极长且含私人内容）。
            text += "(带数据)";
        }
        String name = AiItemNaming.describe(stack);
        if (name != null && !name.isBlank()) {
            text += " " + name;
        }
        return text.length() <= SLOT_TEXT_LIMIT ? text : text.substring(0, SLOT_TEXT_LIMIT) + "…";
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
