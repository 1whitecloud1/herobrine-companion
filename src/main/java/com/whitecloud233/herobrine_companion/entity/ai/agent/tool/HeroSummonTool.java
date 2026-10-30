package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
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
 * fallback 到 Hero 主人。</p>
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

    /**
     * 解析发起请求的玩家。
     *
     * <p><b>必须走 server 的玩家表，不能用 {@code hero.level().getPlayerByUUID()}</b>：
     * 玩家说「你过来」时，多半正是 hero 与他不在同一维度（或不在同一已加载区块）。
     * {@code level().getPlayerByUUID()} 与 {@code HeroEntity#getOwnerPlayer()} 都只在
     * <b>hero 当前所在维度</b>里找玩家，跨维度恒返回 null——两个分支一起落空，
     * 工具就回一句"找不到请求者玩家，无法传送"，跨维度传送永远走不到。</p>
     */
    private static ServerPlayer resolveRequester(AgentToolContext context) {
        var hero = context.hero();
        if (hero == null || hero.getServer() == null) {
            return null;
        }
        UUID requesterUuid = context.requesterUuid();
        if (requesterUuid != null) {
            ServerPlayer requester = hero.getServer().getPlayerList().getPlayer(requesterUuid);
            if (requester != null) {
                return requester;
            }
        }
        UUID ownerUuid = hero.getOwnerUUID();
        return ownerUuid == null ? null : hero.getServer().getPlayerList().getPlayer(ownerUuid);
    }
}
