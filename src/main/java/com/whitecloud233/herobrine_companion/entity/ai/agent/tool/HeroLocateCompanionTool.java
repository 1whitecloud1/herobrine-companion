package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 只读定位工具：报告当前与 Herobrine 同行的玩家位置。零副作用。
 *
 * <p>Herobrine 是俯瞰世界的存在，不会把任何人当作主人——那位玩家只是他
 * 主动选择同行 / 守护的对象，因此这里一律称"同行者"。</p>
 */
public final class HeroLocateCompanionTool implements AgentTool {

    @Override
    public String id() {
        return "hero_locate_companion";
    }

    @Override
    public String description() {
        return "只读查询当前与 Herobrine 同行的玩家所在维度与坐标（若在线且在可探测范围）。";
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
        if (frame.ownerUuid() == null) {
            return AgentToolResult.fail("Herobrine 未与任何人同行",
                    Component.translatable("message.herobrine_companion.tool.locate_companion.no_companion"));
        }
        if (hero.level().getPlayerByUUID(frame.ownerUuid()) instanceof ServerPlayer companion) {
            return AgentToolResult.ok("同行者维度=" + companion.level().dimension().location()
                    + " 坐标=" + (int) companion.getX() + "," + (int) companion.getY() + "," + (int) companion.getZ(),
                    Component.translatable("message.herobrine_companion.tool.locate_companion.success"));
        }
        return AgentToolResult.fail("同行者当前不在线或不可见",
                Component.translatable("message.herobrine_companion.tool.locate_companion.offline"));
    }
}
