package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import java.util.function.Supplier;

/**
 * 指令传感器：把外部注入的挂起工具请求翻译成一条 {@link AgentSenseKind#DIRECTIVE} 观测，供分类器路由。
 *
 * <p>通过 {@link Supplier} 读取请求（依赖倒置），不直接持有 HeroAgent 具体引用，可测、可替换。</p>
 */
public final class AgentDirectiveSensor implements AgentSensor {

    private final Supplier<AgentToolRequest> pendingRequest;

    public AgentDirectiveSensor(Supplier<AgentToolRequest> pendingRequest) {
        this.pendingRequest = pendingRequest;
    }

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        AgentToolRequest request = pendingRequest.get();
        if (request == null || request.toolId() == null || request.toolId().isBlank()) {
            return;
        }
        sink.add(AgentObservation.directive(
                source(),
                frame.gameTime(),
                "待处理工具请求: " + request.toolId() + " 参数=" + request.args(),
                request.toolId()));
    }

    @Override
    public String source() {
        return "directive";
    }
}