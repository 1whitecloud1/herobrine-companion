package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一工具注册表：Agent 调用任何工具的<b>唯一入口</b>（对应设计文档 4.4）。
 *
 * <p>把"白名单查找 + 参数校验 + 玩家确认 + 审计留痕"收敛到一个 choke point，
 * 任何工具只要注册进来就自动获得这套安全栅栏，避免各工具各自 ad-hoc 实现。</p>
 *
 * <p><b>执行顺序（不可跳过）</b>：</p>
 * <ol>
 *   <li>白名单：未注册的 id 直接拒绝（{@code unknown_tool}）。</li>
 *   <li>校验：{@link AgentTool#validate} 不通过则拒绝并留痕。</li>
 *   <li>确认：{@link AgentTool#requiresConfirmation()} 且未获玩家确认 → 拒绝（{@code confirmation_required}）。</li>
 *   <li>执行：{@link AgentTool#execute}。</li>
 *   <li>审计：以上每一步都写入 {@link AgentToolAudit}。</li>
 * </ol>
 */
public final class AgentToolRegistry {

    /** 默认工具目录：客户端 LLM 与注册表共享的同一份"能做什么"清单。 */
    public static List<AgentToolDescriptor> defaultCatalog() {
        List<AgentToolDescriptor> descriptors = new ArrayList<>(8);
        descriptors.add(new HeroInspectTool().descriptor());
        descriptors.add(new HeroLocateCompanionTool().descriptor());
        descriptors.add(new HeroAscendTool().descriptor());
        descriptors.add(new HeroDescendTool().descriptor());
        descriptors.add(new HeroAcceptChallengeTool().descriptor());
        descriptors.add(new HeroSetModeTool().descriptor());
        descriptors.add(new HeroSummonTool().descriptor());
        descriptors.add(new HeroUseSkillTool().descriptor());
        return descriptors;
    }

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final AgentToolAudit audit;

    public AgentToolRegistry(AgentToolAudit audit) {
        this.audit = audit == null ? new AgentToolAudit() : audit;
    }

    /** 注册一个工具；重复 id 视为配置错误，抛异常（早失败优于静默覆盖）。 */
    public void register(AgentTool tool) {
        if (tool == null || tool.id() == null || tool.id().isBlank()) {
            throw new IllegalArgumentException("Tool id must be non-blank");
        }
        if (tools.putIfAbsent(tool.id(), tool) != null) {
            throw new IllegalArgumentException("Duplicate tool id: " + tool.id());
        }
    }

    /** 按白名单查找；未注册返回 null。 */
    public AgentTool lookup(String toolId) {
        return toolId == null ? null : tools.get(toolId);
    }

    /** 已注册工具目录（按注册顺序）。 */
    public List<AgentToolDescriptor> catalog() {
        List<AgentToolDescriptor> descriptors = new ArrayList<>(tools.size());
        for (AgentTool tool : tools.values()) {
            descriptors.add(tool.descriptor());
        }
        return descriptors;
    }

    public AgentToolAudit audit() {
        return audit;
    }

    /**
     * 校验并执行一次工具调用（统一入口）。
     *
     * @param toolId   工具 id
     * @param context  执行上下文（只读）
     * @param args     参数
     * @param confirmed 是否已获玩家确认（M2 无确认 UI，恒为 false → 需确认的工具被安全拒绝）
     * @return 结构化结果；任何拒绝都返回失败并留痕
     */
    public AgentToolResult invoke(String toolId, AgentToolContext context, AgentToolArgs args, boolean confirmed) {
        long gameTime = context != null && context.frame() != null
                ? context.frame().gameTime() : 0L;
        String argsSummary = args == null ? "{}" : args.toString();

        AgentTool tool = lookup(toolId);
        if (tool == null) {
            audit.record(new AgentToolAudit.Entry(gameTime, toolId, argsSummary, "unknown_tool", "rejected"));
            return AgentToolResult.fail("未知工具: " + toolId,
                    Component.translatable("message.herobrine_companion.tool.error.unknown", toolId));
        }

        AgentToolArgs safeArgs = args == null ? AgentToolArgs.empty() : args;

        AgentToolResult validation = tool.validate(safeArgs);
        if (!validation.ok()) {
            audit.record(new AgentToolAudit.Entry(gameTime, toolId, argsSummary, "validation", "rejected:" + validation.message()));
            return validation;
        }

        // 确认：需要确认且未获玩家确认且确认功能开启（P3）→ 返回 confirmation_required，
        // 由执行器入待确认队列并发审批包；Config 关闭时视为已确认（逃生门，回到无 UI 时代）。
        if (tool.requiresConfirmation() && !confirmed && Config.agentToolConfirmation) {
            audit.record(new AgentToolAudit.Entry(gameTime, toolId, argsSummary, "confirmation", "pending"));
            // 确认屏文案不复用 LLM 用 description()：改走玩家友好的本地化动作名。
            return AgentToolResult.confirmationRequired(
                    "需要玩家确认: " + tool.id(),
                    Component.translatable("gui.herobrine_companion.agent_confirm.action",
                            Component.translatable("tool.herobrine_companion." + tool.id())));
        }

        AgentToolResult result;
        try {
            result = tool.execute(context, safeArgs);
        } catch (RuntimeException error) {
            result = AgentToolResult.fail("执行异常: " + error.getClass().getSimpleName(),
                    Component.translatable("message.herobrine_companion.tool.error.execute",
                            error.getClass().getSimpleName()));
        }
        audit.record(new AgentToolAudit.Entry(gameTime, toolId, argsSummary, "execute", result.ok() ? "ok" : "failed:" + result.message()));
        return result;
    }
}