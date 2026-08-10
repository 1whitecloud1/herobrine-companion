package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;

import java.util.UUID;

/**
 * 一次待处理的工具调用请求（不可变）。由外部（对话 / M4 的 LLM / 调试入口）注入 Agent，
 * 经工具路由进入执行。
 *
 * @param toolId        目标工具 id（注册表白名单内）
 * @param args          参数
 * @param requesterUuid 请求者玩家 UUID（用于归属与后续确认）
 * @param playerApproved 是否已获该玩家确认（M2 恒为 false；M5 接确认 UI）
 * @param requestId     客户端生成的关联 ID（用于把执行结果经 {@code AgentToolResultPacket} 送回）
 */
public record AgentToolRequest(String toolId, AgentToolArgs args, UUID requesterUuid, boolean playerApproved, UUID requestId) {

    public AgentToolRequest {
        args = args == null ? AgentToolArgs.empty() : args;
    }
}