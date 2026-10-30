package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.HeroLandmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code hero_remember}（交接 E3）：保存 / 查询 / 修改 / 遗忘<b>命名地点与区域偏好</b>。
 *
 * <p><b>只有玩家明确要求才写入</b>：工具本身不做"看到什么就记什么"；
 * 附近的临时实体、天气不会被无差别保存成长期事实。</p>
 *
 * <p><b>区域必须有范围</b>：单个坐标不能自动代表整栋房子，所以 {@code radius} 是
 * "这里是我家"这类请求的必需信息；未给半径时按单点保存，并在回执里说明它不是区域。</p>
 *
 * <p><b>有实际消费者</b>：{@code hero_navigate} 支持 {@code target_type=place} 按名称前往；
 * {@link HeroLandmarks#isInsideArea} 供区域保护逻辑判断"此地是否属于玩家"。</p>
 */
public final class HeroRememberTool implements AgentTool {

    public static final String ID = "hero_remember";

    public static final String OP_SAVE = "save";
    public static final String OP_QUERY = "query";
    public static final String OP_FORGET = "forget";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "记住、查询或遗忘玩家明确说出的地点与区域偏好（例如“这里是我家”）。地点带维度与坐标，区域另带半径。";
    }

    @Override
    public String category() {
        return "memory";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(
                AgentToolParameter.required("operation", "string", "save=保存或覆盖，query=查询，forget=遗忘"),
                AgentToolParameter.optional("name", "string", "地点名称（save/forget 必填，query 省略则列出全部）"),
                AgentToolParameter.optional("dimension", "string", "维度（省略表示当前维度）"),
                AgentToolParameter.optional("x", "number", "X 坐标（省略表示 Hero 当前位置）"),
                AgentToolParameter.optional("y", "number", "Y 坐标"),
                AgentToolParameter.optional("z", "number", "Z 坐标"),
                AgentToolParameter.optional("radius", "number", "区域半径（格）；省略表示只记单点，不代表整栋建筑"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String operation = args.getString("operation").trim().toLowerCase(java.util.Locale.ROOT);
        if (!List.of(OP_SAVE, OP_QUERY, OP_FORGET).contains(operation)) {
            return AgentToolResult.denied("operation 只允许 save / query / forget",
                    layout("remember.invalid_operation"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (!OP_QUERY.equals(operation) && !args.has("name")) {
            return AgentToolResult.denied(operation + " 需要 name",
                    layout("remember.missing_name"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (args.has("radius")) {
            int radius = args.getInt("radius", 0);
            if (radius <= 0 || radius > HeroLandmarks.MAX_RADIUS) {
                return AgentToolResult.denied("radius 必须在 1~" + HeroLandmarks.MAX_RADIUS + " 之间",
                        layout("remember.invalid_radius"), AgentToolResult.Cause.INVALID_ARGS);
            }
        }
        boolean any = args.has("x") || args.has("y") || args.has("z");
        boolean all = args.has("x") && args.has("y") && args.has("z");
        if (any && !all) {
            return AgentToolResult.denied("x / y / z 需要同时给出（或全省略表示当前位置）",
                    layout("remember.missing_pos"), AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || hero.level() == null) {
            return AgentToolResult.fail("我暂时不在，无法记住地点");
        }
        if (hero.getOwnerUUID() == null) {
            return AgentToolResult.fail("我还没有同行者，不知道这些偏好该记给谁");
        }
        HeroLandmarks landmarks = hero.getHeroAgent().memory().landmarks();
        String operation = args.getString("operation").trim().toLowerCase(java.util.Locale.ROOT);
        return switch (operation) {
            case OP_QUERY -> query(landmarks, args.getString("name"));
            case OP_FORGET -> forget(hero, landmarks, args.getString("name"));
            default -> save(hero, landmarks, args);
        };
    }

    private AgentToolResult save(HeroEntity hero, HeroLandmarks landmarks, AgentToolArgs args) {
        String name = args.getString("name").trim();
        if (name.isEmpty()) {
            return AgentToolResult.denied("地点名称不能为空", layout("remember.missing_name"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        String dimension = args.getString("dimension").trim();
        if (dimension.isEmpty()) {
            dimension = hero.level().dimension().location().toString();
        }
        BlockPos pos = args.has("x")
                ? new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0))
                : hero.blockPosition();
        int radius = args.has("radius") ? Math.min(HeroLandmarks.MAX_RADIUS, args.getInt("radius", 0)) : 0;

        boolean replaced = landmarks.find(name).isPresent();
        boolean saved = landmarks.save(new HeroLandmarks.Landmark(
                name, dimension, pos.getX(), pos.getY(), pos.getZ(), radius, "player"));
        if (!saved) {
            return AgentToolResult.fail("我记得的地点已经满了（" + HeroLandmarks.MAX_LANDMARKS + " 个），这次没有保存",
                    layout("remember.full"));
        }
        hero.getHeroAgent().markMemoryDirty();
        String text = (replaced ? "已更新地点 " : "已记住地点 ") + name
                + " @" + dimension + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + (radius > 0 ? "（半径 " + radius + "）" : "（仅单点，不代表整片区域）");
        return AgentToolResult.ok(text, replaced ? layout("remember.updated") : layout("remember.saved"));
    }

    private AgentToolResult query(HeroLandmarks landmarks, String name) {
        if (landmarks.isEmpty()) {
            return AgentToolResult.ok("我还没有记住任何地点", layout("remember.empty"));
        }
        if (name != null && !name.isBlank()) {
            Optional<HeroLandmarks.Landmark> found = landmarks.find(name);
            return found.map(landmark -> AgentToolResult.ok(landmark.line(), layout("remember.found")))
                    .orElseGet(() -> AgentToolResult.fail("我没有记住叫 " + name + " 的地点",
                            layout("remember.not_found")));
        }
        StringBuilder text = new StringBuilder("已记住 ").append(landmarks.size()).append(" 个地点");
        for (HeroLandmarks.Landmark landmark : landmarks.all()) {
            text.append("；").append(landmark.line());
        }
        return AgentToolResult.ok(text.toString(), layout("remember.listed"));
    }

    private AgentToolResult forget(HeroEntity hero, HeroLandmarks landmarks, String name) {
        boolean removed = landmarks.remove(name);
        if (!removed) {
            return AgentToolResult.fail("我没有记住叫 " + name + " 的地点", layout("remember.not_found"));
        }
        hero.getHeroAgent().markMemoryDirty();
        return AgentToolResult.ok("已遗忘地点 " + name, layout("remember.forgotten"));
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
