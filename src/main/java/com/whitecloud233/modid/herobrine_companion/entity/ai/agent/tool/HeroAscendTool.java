package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 动作工具：让 Hero 升空漂浮（原命令层 {@code hero_fly_up} 迁移而来）。
 *
 * <p>纯实体状态操作，不改变世界 / 玩家任何数据，与旧 {@code makeHeroFlyUp}
 * 逻辑一致：挑战态拒绝 → 退出战斗态 → 悬浮 + 无重力 → 停导航 → 上移并清动量。</p>
 *
 * <p><b>权限模型</b>：英雄以自己身体行动，属命令层既有 permission-bypass 范畴
 * （连作弊都不需要开启），因此 {@code requiresConfirmation=false}。</p>
 */
public final class HeroAscendTool implements AgentTool {

    public static final String ID = "hero_ascend";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 升空漂浮起来（英雄自身动作，玩家施法之外的独立能力）。";
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
            return AgentToolResult.fail("试炼进行中，不能升空",
                    Component.translatable("message.herobrine_companion.tool.ascend.challenge_active"));
        }
        hero.setBattleModeActiveFrom("HeroAscendTool.execute", false);
        hero.setFloating(true);
        hero.setNoGravity(true);
        hero.getNavigation().stop();

        double targetY = Math.min(hero.getY() + 6.0D, hero.level().getMaxBuildHeight() - 2.0D);
        hero.teleportTo(hero.getX(), targetY, hero.getZ());
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hero.fallDistance = 0.0F;
        return AgentToolResult.ok("已升空漂浮",
                Component.translatable("message.herobrine_companion.tool.ascend.success"));
    }
}
