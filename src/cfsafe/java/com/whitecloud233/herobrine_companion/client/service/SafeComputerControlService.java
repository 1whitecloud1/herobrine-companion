package com.whitecloud233.herobrine_companion.client.service;

import java.util.concurrent.CompletableFuture;

/**
 * 安全版惰性 stub（普通构建默认即安全版，编译进安全版 jar）。
 *
 * <p>本机电脑控制（cmd 执行）功能在安全版构建中被排除，本类与正式版同 FQCN、
 * 同包可见性，保证 {@code AIService} / {@code AIPromptAssembler} 等引用方可编译，
 * 运行期行为恒为"不可用"：不包含任何 ProcessBuilder / cmd.exe 调用。</p>
 */
final class SafeComputerControlService {
    private SafeComputerControlService() {}

    static boolean isSupportedHost() {
        return false;
    }

    static CompletableFuture<Result> requestExecution(AIComputerControlSupport.ComputerAction action) {
        return CompletableFuture.completedFuture(Result.of(Status.DISABLED, "disabled_in_this_build"));
    }

    enum Status {
        SUCCESS,
        CANCELLED,
        FAILED,
        DISABLED,
        UNSUPPORTED,
        BUSY
    }

    record Result(Status status, String detail) {
        static Result of(Status status, String detail) {
            return new Result(status, detail == null ? "" : detail);
        }
    }
}
