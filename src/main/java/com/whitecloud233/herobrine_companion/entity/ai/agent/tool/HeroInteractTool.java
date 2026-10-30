package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code hero_interact}（交接 E2）：对<b>有明确交互语义</b>的目标执行一次交互。
 *
 * <p><b>白名单</b>：门 / 活板门 / 栅栏门 / 按钮 / 拉杆。不接受任意方法名、任意命令，
 * 也不把"调用了 use"当作成功——交互后重新读取世界状态，用真实结果回答。</p>
 *
 * <p><b>三种合法结果</b>：已经处于目标状态（无需改变）、操作后达到目标状态、条件不满足而失败。
 * 回执据实说明是哪一种。</p>
 */
public final class HeroInteractTool implements AgentTool {

    public static final String ID = "hero_interact";

    /** 允许交互的距离平方（4 格²）。 */
    private static final double MAX_DISTANCE_SQR = 16.0D;

    /** 支持的操作。 */
    public static final String OP_TOGGLE = "toggle";
    public static final String OP_OPEN = "open";
    public static final String OP_CLOSE = "close";
    public static final String OP_PRESS = "press";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "对门、活板门、栅栏门、按钮或拉杆执行一次明确交互（开关/按下）。交互后读取真实世界状态；已处于目标状态或条件不满足都会如实说明。";
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
                AgentToolParameter.required("operation", "string", "toggle=切换开合，open/close=明确开或关，press=按下按钮"),
                AgentToolParameter.required("x", "number", "目标方块 X 坐标"),
                AgentToolParameter.required("y", "number", "目标方块 Y 坐标"),
                AgentToolParameter.required("z", "number", "目标方块 Z 坐标"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String operation = args.getString("operation").trim().toLowerCase(java.util.Locale.ROOT);
        if (!List.of(OP_TOGGLE, OP_OPEN, OP_CLOSE, OP_PRESS).contains(operation)) {
            return AgentToolResult.denied("operation 只允许 toggle / open / close / press",
                    layout("interact.invalid_operation"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (!args.has("x") || !args.has("y") || !args.has("z")) {
            return AgentToolResult.denied("需要目标方块坐标 x / y / z",
                    layout("interact.missing_pos"), AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法交互");
        }
        BlockPos pos = new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0));
        String operation = args.getString("operation").trim().toLowerCase(java.util.Locale.ROOT);

        if (!level.isLoaded(pos)) {
            return AgentToolResult.fail("那个位置的区块还没有加载，我看不到它", layout("interact.chunk_unloaded"));
        }
        if (hero.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_DISTANCE_SQR) {
            return AgentToolResult.fail("那个方块离我太远了（超过 4 格）", layout("interact.too_far"));
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!isSupported(block)) {
            // 白名单之外一律拒绝，不做"随便 use 一下"的冒险。
            return AgentToolResult.fail("我只对门、活板门、栅栏门、按钮和拉杆做交互，这个方块不在范围内",
                    layout("interact.unsupported"));
        }

        Boolean before = readOpenState(state);
        if (alreadySatisfied(operation, before)) {
            return AgentToolResult.ok("它已经处于你要的状态（当前 " + describe(state) + "），我没有再动它",
                    layout("interact.already"));
        }

        InteractionResult result = interact(level, pos, state, operation);
        // 交互后重新读取真实状态：不以"方法被调用"作为成功依据。
        BlockState after = level.getBlockState(pos);
        Boolean now = readOpenState(after);

        if (result == InteractionResult.FAIL) {
            return AgentToolResult.fail("这次交互被拒绝了（当前 " + describe(after) + "）", layout("interact.failed"));
        }
        if (now != null && before != null && now.equals(before)) {
            return AgentToolResult.fail("交互没有改变它的状态（当前仍是 " + describe(after) + "）",
                    layout("interact.no_change"));
        }
        return AgentToolResult.ok("交互完成（现在 " + describe(after) + "）", layout("interact.success"));
    }

    /** 只支持有明确开合/按压语义的方块。 */
    private static boolean isSupported(Block block) {
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock;
    }

    /** 读取有明确语义的开合 / 通电状态；按钮 / 拉杆用 powered。 */
    private static Boolean readOpenState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return state.getValue(BlockStateProperties.POWERED);
        }
        return null;
    }

    private static boolean alreadySatisfied(String operation, Boolean open) {
        if (open == null) {
            return false;
        }
        return switch (operation) {
            case OP_OPEN -> open;
            case OP_CLOSE -> !open;
            case OP_PRESS -> false;
            default -> false;
        };
    }

    /**
     * 执行一次交互。
     *
     * <p><b>1.21.1 适配</b>：方块交互入口从 {@code use(...)} 拆成了
     * {@code useItemOn(...)} 与 {@code useWithoutItem(...)}，后者在各类里是
     * {@code protected}，外部工具无法直接调用。因此这里对门类走其公开的
     * {@link DoorBlock#setOpen}，其余有明确开合语义的方块直接按目标状态改写
     * BlockState（这正是这些方块 own 的语义），并<b>在调用后重新读取世界状态</b>
     * 判定结果——不以"方法被调用"作为成功依据。</p>
     */
    private static InteractionResult interact(ServerLevel level, BlockPos pos, BlockState state, String operation) {
        Block block = state.getBlock();
        boolean hasOpen = state.hasProperty(BlockStateProperties.OPEN);
        if (block instanceof DoorBlock door) {
            if (!hasOpen) {
                return InteractionResult.FAIL;
            }
            boolean current = state.getValue(BlockStateProperties.OPEN);
            boolean target = resolveTarget(operation, current);
            if (target == current) {
                return InteractionResult.CONSUME;
            }
            door.setOpen(null, level, state, pos, target);
            return InteractionResult.CONSUME;
        }
        if (block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            if (!hasOpen) {
                return InteractionResult.FAIL;
            }
            boolean current = state.getValue(BlockStateProperties.OPEN);
            boolean target = resolveTarget(operation, current);
            if (target == current) {
                return InteractionResult.CONSUME;
            }
            // 栅栏门/活板门没有公开的开关方法：按其自身的 OPEN 语义改写状态，
            // 由随后的状态复查确认实际效果。
            BlockState updated = state.setValue(BlockStateProperties.OPEN, target);
            level.setBlockAndUpdate(pos, updated);
            return InteractionResult.CONSUME;
        }
        if (block instanceof ButtonBlock || block instanceof LeverBlock) {
            if (!state.hasProperty(BlockStateProperties.POWERED)) {
                return InteractionResult.FAIL;
            }
            boolean current = state.getValue(BlockStateProperties.POWERED);
            boolean target = block instanceof LeverBlock ? !current : true;
            if (target == current) {
                return InteractionResult.CONSUME;
            }
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.POWERED, target));
            if (block instanceof ButtonBlock && block instanceof net.minecraft.world.level.block.DirectionalBlock) {
                // 按钮到时会由方块自身的调度自动弹回；这里只负责按下。
                level.scheduleTick(pos, block, 20);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    /** open / close 明确指定；toggle 在门类上表示取反。 */
    private static boolean resolveTarget(String operation, boolean current) {
        return switch (operation) {
            case OP_OPEN -> true;
            case OP_CLOSE -> false;
            default -> !current;
        };
    }

    private static String describe(BlockState state) {
        StringBuilder builder = new StringBuilder(String.valueOf(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            builder.append(" open=").append(state.getValue(BlockStateProperties.OPEN));
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            builder.append(" powered=").append(state.getValue(BlockStateProperties.POWERED));
        }
        return builder.toString();
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
