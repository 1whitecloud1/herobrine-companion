package com.whitecloud233.modid.herobrine_companion.client.jvm.impl;

import com.whitecloud233.modid.herobrine_companion.client.jvm.JvmCodeAction;
import com.whitecloud233.modid.herobrine_companion.client.jvm.JvmCodeExecutor;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

/**
 * 唯一的 Minecraft 渲染线程执行入口（单一职责：调度执行并吞掉所有异常）。
 * 代码在此线程上运行，安全访问客户端字段；改世界状态需由代码自己跳到集成服务器线程。
 */
public class ClientThreadJvmCodeExecutor implements JvmCodeExecutor {
    @Override
    public CompletableFuture<JvmCodeOutcome> execute(JvmCodeAction action) {
        CompletableFuture<JvmCodeOutcome> future = new CompletableFuture<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            future.complete(JvmCodeOutcome.failure("Minecraft client unavailable."));
            return future;
        }
        minecraft.tell(() -> {
            try {
                String detail = action.run();
                future.complete(JvmCodeOutcome.success(detail));
            } catch (Throwable error) {
                future.complete(JvmCodeOutcome.failure(String.valueOf(error)));
            }
        });
        return future;
    }
}
