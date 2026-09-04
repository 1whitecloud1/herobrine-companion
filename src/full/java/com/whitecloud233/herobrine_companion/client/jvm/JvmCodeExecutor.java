package com.whitecloud233.herobrine_companion.client.jvm;

import java.util.concurrent.CompletableFuture;

/**
 * 执行抽象（DIP 接缝）：在正确的线程上执行编译产物并吞掉所有异常。
 * 实现负责封装 Minecraft 渲染线程的细节。
 */
public interface JvmCodeExecutor {
    /**
     * 调度执行一个已编译的契约实例。
     *
     * @param action 编译产物
     * @return 完成时携带执行结果；success=false 时 detail 为异常信息
     */
    CompletableFuture<JvmCodeOutcome> execute(JvmCodeAction action);

    /** 执行结果值类型。 */
    record JvmCodeOutcome(boolean success, String detail) {
        public static JvmCodeOutcome success(String detail) {
            return new JvmCodeOutcome(true, detail == null ? "" : detail);
        }

        public static JvmCodeOutcome failure(String detail) {
            return new JvmCodeOutcome(false, detail == null ? "" : detail);
        }
    }
}
