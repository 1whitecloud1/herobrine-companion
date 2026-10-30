package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModePolicy;
import com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModeService;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 动作工具：切换 Hero 的陪伴模式（原命令层 {@code toggle_companion_follow} 迁移而来）。
 *
 * <p>{@code mode=follow} 进入跟随（沿用 {@code ToggleCompanionPacket} 的信任门槛：信任≥50
 * 才愿随行；开启且未结伴时绑定请求者/同行者）；{@code mode=standby} 退出跟随。</p>
 */
public final class HeroSetModeTool implements AgentTool {

    public static final String ID = "hero_set_mode";

    private static final String MODE_FOLLOW = "follow";
    private static final String MODE_STANDBY = "standby";
    private static final Set<String> ALLOWED_MODES = Set.of(MODE_FOLLOW, MODE_STANDBY);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 切换陪伴模式：follow=随行同行者，standby=退出跟随并恢复自主活动（英雄自身动作）。";
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
        return List.of(AgentToolParameter.required(
                "mode", "string", "follow=进入跟随模式，standby=退出跟随/待命"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String mode = args.getString("mode").trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_MODES.contains(mode)) {
            return AgentToolResult.fail("mode 只允许: " + ALLOWED_MODES,
                    Component.translatable("message.herobrine_companion.tool.set_mode.invalid", ALLOWED_MODES.toString()));
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        AgentToolResult validation = validate(args);
        if (validation.failed()) return validation;

        var hero = context.hero();
        boolean desiredMode = MODE_FOLLOW.equals(args.getString("mode").trim().toLowerCase(java.util.Locale.ROOT));
        // 内部自主工具沿用主人身份；外部请求者存在时必须原样鉴权，不能回退成主人。
        UUID requester = context.requesterUuid() != null ? context.requesterUuid() : hero.getOwnerUUID();
        CompanionModePolicy.Result result = CompanionModeService.changeMode(requester, hero, desiredMode);
        if (!result.successful()) {
            Component display = result == CompanionModePolicy.Result.NOT_OWNER
                    ? hero.createNotYourHeroMessage() : Component.translatable(result.messageKey());
            return AgentToolResult.fail("陪伴模式切换失败: " + result.name(), display);
        }
        return AgentToolResult.ok(desiredMode ? "已切换为跟随" : "已退出跟随",
                Component.translatable(desiredMode
                        ? "message.herobrine_companion.tool.set_mode.follow"
                        : "message.herobrine_companion.tool.set_mode.standby"));
    }
}
