package com.whitecloud233.modid.herobrine_companion.client.jvm;

import java.util.concurrent.CompletableFuture;

/**
 * 安全版惰性 stub（普通构建默认即安全版，编译进安全版 jar）。
 *
 * <p>JVM 代码注入功能（运行时 javac 编译 + defineClass 加载）在安全版构建中被排除，
 * 本类保证 {@code AIService} / {@code AIPromptAssembler} 等引用方可编译，
 * 运行期恒为"不可用"：isAvailable() 恒 false，requestExecution 恒返回 DISABLED，
 * 绝不编译或执行任何代码。</p>
 */
public class JvmCodeExecutionService {
    private JvmCodeExecutionService() {}

    /** 当前环境是否具备编译能力：安全版恒为 false，LLM 不会看到该工具。 */
    public static boolean isAvailable() {
        return false;
    }

    /** 安全版恒返回 DISABLED，绝不编译或执行任何代码。 */
    public static CompletableFuture<JvmCodeResult> requestExecution(String methodBody) {
        return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.DISABLED, "disabled_in_this_build"));
    }
}
