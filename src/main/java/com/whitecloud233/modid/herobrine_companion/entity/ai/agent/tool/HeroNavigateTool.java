package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.NavigateTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.TaskGoals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * {@code hero_navigate}（交接 D2）：真正"走过去"，而不是传送。
 *
 * <p>与既有 {@code hero_summon_to_player} 的分工写清楚，互相当替身是不允许的：</p>
 * <ul>
 *   <li>{@code hero_summon_to_player}：瞬间传送（含跨维度重新召唤）；</li>
 *   <li>{@code hero_navigate}：<b>步行寻路</b>，经任务队列 + GoalSelector 的 MOVE/LOOK 控制权执行，
 *       走不过去就如实说"不可达"，绝不用传送假装走到了。</li>
 * </ul>
 *
 * <p>参数保持扁平：{@code target_type}（owner / position / entity）、{@code dimension}、
 * {@code entity_uuid}、{@code x}/{@code y}/{@code z}、{@code stop_distance}。</p>
 */
public final class HeroNavigateTool implements AgentTool {

    public static final String ID = "hero_navigate";

    /** 步行任务允许的最大距离平方（32 格² = 1024）：再远的"走过去"应明确拒绝，避免无意义长任务。 */
    private static final double MAX_WALK_DISTANCE_SQR = 1024.0D;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 步行前往一个位置、某个实体、一个已记住的地点，或返回你身边（不是传送）。走不过去会如实报告不可达。";
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
                AgentToolParameter.required("target_type", "string", "目标类型：owner=返回玩家身边，position=前往坐标，entity=前往实体，place=前往已记住的地点"),
                AgentToolParameter.optional("dimension", "string", "目标维度（可省略，默认当前维度）"),
                AgentToolParameter.optional("entity_uuid", "string", "target_type=entity 时的实体 UUID"),
                AgentToolParameter.optional("place_name", "string", "target_type=place 时 hero_remember 保存的地点名称"),
                AgentToolParameter.optional("x", "number", "target_type=position 时的 X 坐标"),
                AgentToolParameter.optional("y", "number", "target_type=position 时的 Y 坐标"),
                AgentToolParameter.optional("z", "number", "target_type=position 时的 Z 坐标"),
                AgentToolParameter.optional("stop_distance", "number", "到达判定距离（格），默认 2.5"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String targetType = args.getString("target_type").trim().toLowerCase(java.util.Locale.ROOT);
        if (!targetType.equals("owner") && !targetType.equals("position")
                && !targetType.equals("entity") && !targetType.equals("place")) {
            return AgentToolResult.denied("target_type 只允许 owner / position / entity / place",
                    Component.translatable("message.herobrine_companion.tool.navigate.invalid_target"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        if (targetType.equals("position")
                && (!args.has("x") || !args.has("y") || !args.has("z"))) {
            return AgentToolResult.denied("target_type=position 需要 x / y / z 三个坐标",
                    Component.translatable("message.herobrine_companion.tool.navigate.missing_pos"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        if (targetType.equals("entity") && !isUuid(args.getString("entity_uuid"))) {
            return AgentToolResult.denied("target_type=entity 需要合法的 entity_uuid",
                    Component.translatable("message.herobrine_companion.tool.navigate.missing_entity"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        if (targetType.equals("place") && !args.has("place_name")) {
            return AgentToolResult.denied("target_type=place 需要 place_name（先用 hero_remember query 确认地点名）",
                    layout("navigate.missing_place"), AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法前往");
        }
        String targetType = args.getString("target_type").trim().toLowerCase(java.util.Locale.ROOT);
        double stopDistance = clamp(args.getDouble("stop_distance", 2.5D), 1.0D, 8.0D);

        if (targetType.equals("owner")) {
            return navigateToOwner(hero, level, args, stopDistance);
        }
        if (targetType.equals("entity")) {
            return navigateToEntity(hero, level, args);
        }
        if (targetType.equals("place")) {
            return navigateToPlace(hero, level, args);
        }
        return navigateToPosition(hero, level, args, stopDistance);
    }

    /**
     * 前往一个已记住的地点（交接 E3 的"实际消费者"）。
     *
     * <p>地点必须先在 {@code hero_remember} 里由玩家明确保存；不认识的名字直接拒绝，
     * 不会猜一个相似坐标。</p>
     */
    private AgentToolResult navigateToPlace(HeroEntity hero, ServerLevel level, AgentToolArgs args) {
        String name = args.getString("place_name").trim();
        var found = hero.getHeroAgent().memory().landmarks().find(name);
        if (found.isEmpty()) {
            return AgentToolResult.fail("我没有记住叫 " + name + " 的地点",
                    Component.translatable("message.herobrine_companion.tool.navigate.unknown_place", name));
        }
        var landmark = found.get();
        if (!landmark.dimension().equals(level.dimension().location().toString())) {
            return AgentToolResult.fail("记住的地点 " + name + " 在别的维度（" + landmark.dimension() + "），步行无法前往",
                    layout("navigate.cross_dimension"));
        }
        BlockPos target = new BlockPos(landmark.x(), landmark.y(), landmark.z());
        if (!level.hasChunkAt(target)) {
            return AgentToolResult.fail("地点 " + name + " 所在的区块还没有加载，我看不到那里",
                    layout("navigate.chunk_unloaded"));
        }
        return enqueue(hero, TaskGoals.goTo(target, TaskGoals.newGoalId("navigate")),
                "前往地点 " + name);
    }

    private AgentToolResult navigateToOwner(HeroEntity hero, ServerLevel level, AgentToolArgs args, double stopDistance) {
        UUID ownerUuid = hero.getOwnerUUID();
        if (ownerUuid == null) {
            return AgentToolResult.fail("我还没有同行者，无法返回");
        }
        ServerPlayer owner = hero.getServer() == null ? null : hero.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            // 目标消失 / 不在线：明确区分，不说成"到达"。
            return AgentToolResult.fail("你的同行者不在线，无法返回");
        }
        if (owner.level() != level) {
            // 跨维度：步行任务不自行穿越，交给调用方决定（回执要说明原因）。
            return AgentToolResult.fail("你和我不在同一维度，步行无法跨维度返回",
                    Component.translatable("message.herobrine_companion.tool.navigate.cross_dimension"));
        }
        if (hero.distanceToSqr(owner) <= stopDistance * stopDistance) {
            return AgentToolResult.ok("我已经在你身边了", layout("navigate.already_there"));
        }
        return enqueue(hero, TaskGoals.returnToOwner(TaskGoals.newGoalId("navigate")), "返回你身边");
    }

    private AgentToolResult navigateToEntity(HeroEntity hero, ServerLevel level, AgentToolArgs args) {
        UUID entityUuid = UUID.fromString(args.getString("entity_uuid").trim());
        var entity = level.getEntity(entityUuid);
        if (entity == null || !entity.isAlive()) {
            return AgentToolResult.fail("目标实体已不存在");
        }
        return enqueue(hero, TaskGoals.follow(entityUuid, TaskGoals.newGoalId("navigate")),
                "前往实体 " + entityUuid);
    }

    private AgentToolResult navigateToPosition(HeroEntity hero, ServerLevel level, AgentToolArgs args, double stopDistance) {
        if (!dimensionMatches(level, args.getString("dimension"))) {
            return AgentToolResult.fail("目标位置不在我当前所在的维度，步行无法跨维度前往",
                    Component.translatable("message.herobrine_companion.tool.navigate.cross_dimension"));
        }
        int x = args.getInt("x", Integer.MIN_VALUE);
        int y = args.getInt("y", Integer.MIN_VALUE);
        int z = args.getInt("z", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return AgentToolResult.denied("坐标必须是整数", Component.translatable(
                    "message.herobrine_companion.tool.navigate.missing_pos"), AgentToolResult.Cause.INVALID_ARGS);
        }
        BlockPos target = new BlockPos(x, y, z);
        if (hero.blockPosition().distSqr(target) > MAX_WALK_DISTANCE_SQR) {
            return AgentToolResult.fail("目标太远了（超过 32 格），步行过去不现实；请改用召唤或传送",
                    layout("navigate.too_far"));
        }
        if (!level.hasChunkAt(target)) {
            // 未加载不等于不存在：如实说明，不假装已经走到。
            return AgentToolResult.fail("目标所在的区块还没有加载，我看不到那里",
                    layout("navigate.chunk_unloaded"));
        }
        if (hero.blockPosition().distSqr(target) <= stopDistance * stopDistance) {
            return AgentToolResult.ok("我已经在那里了", layout("navigate.already_there"));
        }
        List<PlannedTask> tasks = List.of(NavigateTask.toPos(target, 1.0D, TaskGoals.newGoalId("navigate")));
        return enqueue(hero, tasks, "前往 " + x + "," + y + "," + z);
    }

    private AgentToolResult enqueue(HeroEntity hero, List<PlannedTask> tasks, String what) {
        if (tasks.isEmpty()) {
            return AgentToolResult.fail("没有可执行的导航目标");
        }
        HeroTaskQueue queue = hero.getHeroAgent().taskQueue();
        if (!queue.enqueueAll(tasks)) {
            // 入队失败要收尾，避免残留半截目标占住移动控制权。
            queue.cancelGoal(new com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.AgentTaskContext(hero),
                    tasks.get(0).goalId());
            return AgentToolResult.fail("任务队列已满（" + HeroTaskQueue.CAPACITY + "），这次没有出发",
                    layout("navigate.queue_full"));
        }
        queue.resume();
        return AgentToolResult.ok("导航任务已提交（" + what + "），到达后我会继续手上的事", layout("navigate.started"));
    }

    private static boolean dimensionMatches(ServerLevel level, String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return true;
        }
        ResourceKey<Level> key = level.dimension();
        return key.location().toString().equals(dimension.trim());
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value == null ? "" : value.trim());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
