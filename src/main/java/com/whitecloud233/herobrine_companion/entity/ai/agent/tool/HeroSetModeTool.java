package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 动作工具：切换 Hero 的陪伴模式（原命令层 {@code toggle_companion_follow} 迁移而来）。
 *
 * <p>{@code mode=follow} 进入跟随（沿用 {@code ToggleCompanionPacket} 的信任门槛：信任≥50
 * 才愿跟随；开启且无主时绑定请求者/主人）；{@code mode=standby} 退出跟随。</p>
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
        return "让 Herobrine 切换陪伴模式：follow=跟随主人，standby=原地待命（英雄自身动作）。";
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
                    Component.translatable("message.herobrine_companion.tool.set_mode.invalid", ALLOWED_MODES));
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        var hero = context.hero();
        String mode = args.getString("mode").trim().toLowerCase(java.util.Locale.ROOT);

        if (MODE_STANDBY.equals(mode)) {
            hero.setCompanionMode(false);
            return AgentToolResult.ok("已切换为待命",
                    Component.translatable("message.herobrine_companion.tool.set_mode.standby"));
        }

        // follow
        if (hero.getTrustLevel() < 50) {
            return AgentToolResult.fail("信任不足 50，暂时不愿跟随",
                    Component.translatable("message.herobrine_companion.tool.set_mode.trust_low"));
        }
        ServerPlayer player = resolvePlayer(context);
        if (!hero.isCompanionMode() && hero.getOwnerUUID() == null) {
            if (player == null) {
                return AgentToolResult.fail("无法确认主人，无法进入跟随模式",
                        Component.translatable("message.herobrine_companion.tool.set_mode.no_owner"));
            }
            hero.setOwnerUUID(player.getUUID());
        }
        hero.setCompanionMode(true);
        return AgentToolResult.ok("已切换为跟随",
                Component.translatable("message.herobrine_companion.tool.set_mode.follow"));
    }

    private static ServerPlayer resolvePlayer(AgentToolContext context) {
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
