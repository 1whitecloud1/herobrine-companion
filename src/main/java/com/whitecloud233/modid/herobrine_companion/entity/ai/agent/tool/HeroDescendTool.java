package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 动作工具：让 Hero 从漂浮状态落回地面（原命令层 {@code hero_land} 迁移而来）。
 *
 * <p>与 {@link HeroAscendTool} 配对，纯实体状态操作：挑战态拒绝 → 取消悬浮 / 重力 → 停导航。</p>
 */
public final class HeroDescendTool implements AgentTool {

    public static final String ID = "hero_descend";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 从空中落回地面（英雄自身动作）。";
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
        return List.of();
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        var hero = context.hero();
        if (hero.isChallengeActiveState()) {
            return AgentToolResult.fail("试炼进行中，不能降落",
                    Component.translatable("message.herobrine_companion.tool.descend.challenge_active"));
        }
        hero.setFloating(false);
        hero.setNoGravity(false);
        hero.getNavigation().stop();
        hero.fallDistance = 0.0F;
        return AgentToolResult.ok("已落回地面",
                Component.translatable("message.herobrine_companion.tool.descend.success"));
    }
}
