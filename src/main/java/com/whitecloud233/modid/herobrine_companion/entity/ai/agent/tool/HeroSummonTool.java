package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 动作工具：让 Hero 立即传送到玩家身边（原命令层 {@code summon_hero_to_player} 迁移而来）。
 *
 * <p>委托 {@link HeroSummonItem#performSummonOrTeleport(ServerLevel, Player, Vec3)}
 * 完成跨维度传送 / 复活 / 原地传送的统一逻辑。agent 循环在服务端执行，
 * 无需命令层那种单机/联机分支。请求者玩家从 {@link AgentToolContext#requesterUuid()} 解析，
 * fallback 到 Hero 同行者。</p>
 *
 * <p><b>权限模型</b>：英雄以自己身体行动，沿用命令层 permission-bypass 信任模型，
 * {@code requiresConfirmation=false}。</p>
 */
public final class HeroSummonTool implements AgentTool {

    public static final String ID = "hero_summon_to_player";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 立刻传送到你身边（跨维度/重新召唤由统一逻辑处理）。";
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
        ServerPlayer requester = resolveRequester(context);
        if (requester == null) {
            return AgentToolResult.fail("找不到请求者玩家，无法传送",
                    Component.translatable("message.herobrine_companion.tool.summon.no_requester"));
        }
        boolean success = HeroSummonItem.performSummonOrTeleport(
                requester.serverLevel(), requester, requester.position());
        return success
                ? AgentToolResult.ok("已传送到你身边",
                        Component.translatable("message.herobrine_companion.tool.summon.success"))
                : AgentToolResult.fail("传送失败",
                        Component.translatable("message.herobrine_companion.tool.summon.failed"));
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
