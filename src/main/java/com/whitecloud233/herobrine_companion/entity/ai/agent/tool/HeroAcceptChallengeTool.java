package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 动作工具：Hero 接受玩家的试炼挑战（原命令层 {@code accept_challenge} 迁移而来）。
 *
 * <p>委托 {@link HeroChallengeManager#startChallenge} 完成跨维度拉人 / 传送到试炼擂台 /
 * 进入挑战状态。请求者玩家从 {@link AgentToolContext#requesterUuid()} 解析，
 * fallback 到 Hero 主人；两者皆不可得则拒绝。</p>
 *
 * <p><b>权限模型</b>（P3）：把玩家拉入试炼擂台是大副作用动作，
 * {@code requiresConfirmation=true} —— 需经确认屏玩家点头后才执行。</p>
 */
public final class HeroAcceptChallengeTool implements AgentTool {

    public static final String ID = "hero_accept_challenge";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Herobrine 接受玩家的试炼挑战，将玩家拉入试炼擂台（英雄自身动作）。";
    }

    @Override
    public String category() {
        return "action";
    }

    @Override
    public boolean requiresConfirmation() {
        // P3：把玩家拉入试炼擂台属于大副作用动作，须玩家确认。
        return true;
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
            return AgentToolResult.ok("已在试炼中，无需重复接受",
                    Component.translatable("message.herobrine_companion.tool.accept_challenge.already_active"));
        }
        ServerPlayer player = resolveRequester(context);
        if (player == null) {
            return AgentToolResult.fail("找不到请求者玩家，无法接受挑战",
                    Component.translatable("message.herobrine_companion.tool.accept_challenge.no_requester"));
        }
        HeroChallengeManager.startChallenge(hero, player, 1);
        return AgentToolResult.ok("试炼已接受，玩家已被拉入试炼擂台",
                Component.translatable("message.herobrine_companion.tool.accept_challenge.accepted"));
    }

    private static ServerPlayer resolveRequester(AgentToolContext context) {
        var hero = context.hero();
        UUID requesterUuid = context.requesterUuid();
        if (requesterUuid != null && hero.level().getPlayerByUUID(requesterUuid) instanceof ServerPlayer requester) {
            return requester;
        }
        if (hero.getOwnerPlayer() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }
}
