package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 只读定位工具：报告主人当前位置。零副作用。
 *
 * <p>存在意义：{@code hero_summon_to_player} 让 Hero 来找玩家，这个工具反过来回答"主人在哪"，
 * 供 LLM 在"让我到你那里 / 你在哪"这类请求里据实作答，而不是瞎猜坐标。</p>
 *
 * <p><b>必须走 server 的玩家表</b>：hero 与主人经常不在同一维度，
 * {@code hero.level().getPlayerByUUID()} 只在 hero 当前维度里找，跨维度恒返回 null，
 * 会把在线的主人误报成"不在线或不可见"（{@code hero_summon_to_player} 之前就是这么错的）。</p>
 */
public final class HeroLocateOwnerTool implements AgentTool {

    @Override
    public String id() {
        return "hero_locate_owner";
    }

    @Override
    public String description() {
        return "只读查询目前 Hero 主人的所在维度与坐标（若在线且在可探测范围）。";
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
        return List.of();
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        var frame = context.frame();
        var hero = context.hero();
        UUID ownerUuid = frame.ownerUuid();
        if (ownerUuid == null) {
            return AgentToolResult.fail("Herobrine 没有主人",
                    Component.translatable("message.herobrine_companion.tool.locate_owner.no_owner"));
        }

        ServerPlayer owner = hero == null || hero.getServer() == null
                ? null
                : hero.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            return AgentToolResult.fail("主人当前不在线或不可见",
                    Component.translatable("message.herobrine_companion.tool.locate_owner.offline"));
        }

        return AgentToolResult.ok("主人维度=" + owner.level().dimension().location()
                        + " 坐标=" + (int) owner.getX() + "," + (int) owner.getY() + "," + (int) owner.getZ(),
                Component.translatable("message.herobrine_companion.tool.locate_owner.success"));
    }
}
