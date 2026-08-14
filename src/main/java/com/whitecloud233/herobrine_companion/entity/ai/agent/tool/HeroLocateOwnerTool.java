package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 只读定位工具：报告主人当前位置。零副作用。
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
        if (frame.ownerUuid() == null) {
            return AgentToolResult.fail("Hero 没有主人",
                    Component.translatable("message.herobrine_companion.tool.locate_owner.no_owner"));
        }
        if (hero.level().getPlayerByUUID(frame.ownerUuid()) instanceof ServerPlayer owner) {
            return AgentToolResult.ok("主人维度=" + owner.level().dimension().location()
                    + " 坐标=" + (int) owner.getX() + "," + (int) owner.getY() + "," + (int) owner.getZ(),
                    Component.translatable("message.herobrine_companion.tool.locate_owner.success"));
        }
        return AgentToolResult.fail("主人当前不在线或不可见",
                Component.translatable("message.herobrine_companion.tool.locate_owner.offline"));
    }
}