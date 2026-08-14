package com.whitecloud233.herobrine_companion.client.jvm;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.gui.JvmCodeConfirmScreen;
import com.whitecloud233.herobrine_companion.client.jvm.impl.ClientThreadJvmCodeExecutor;
import com.whitecloud233.herobrine_companion.client.jvm.impl.InMemoryJvmCodeCompiler;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * jvm_code_skill 编排层（单一职责：确认 → 编译 → 执行 的工作流）。
 * 依赖倒置：只依赖 {@link JvmCodeCompiler} / {@link JvmCodeExecutor} 接口，
 * 不接触 javac、渲染线程、UI 绘制的任何实现细节。
 */
public class JvmCodeExecutionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean CONFIRMATION_PENDING = new AtomicBoolean(false);
    private static final ExecutorService COMPILE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Herobrine-Jvm-Code");
        thread.setDaemon(true);
        return thread;
    });

    // DIP：换编译器实现或换执行线程策略，只需替换这两个常量。
    private static final JvmCodeCompiler COMPILER = new InMemoryJvmCodeCompiler();
    private static final JvmCodeExecutor EXECUTOR = new ClientThreadJvmCodeExecutor();

    private static final int MAX_SOURCE_LENGTH = 12_000;

    private JvmCodeExecutionService() {}

    /** 当前环境是否具备编译能力（JDK）。AIService 据此决定是否向 LLM 广告该工具。 */
    public static boolean isAvailable() {
        return COMPILER.isAvailable();
    }

    /**
     * 请求执行一段 LLM 生成的方法体。
     * 只碰游戏 API 且不涉及关闭游戏的白名单代码免确认自动执行；
     * 引用文件/网络/系统命令/反射等（DANGEROUS），或会关闭/退出游戏、关闭世界
     * （Minecraft.stop / Window.close / halt / stopServer 等，LIFECYCLE）的代码，一律弹确认框并说明后果。
     *
     * @param methodBody run() 的方法体
     * @return 取消 / 编译失败 / 运行失败 / 成功等统一结果
     */
    public static CompletableFuture<JvmCodeResult> requestExecution(String methodBody) {
        if (!LLMConfig.isJvmCodeSkillEnabled()) {
            return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.DISABLED, "disabled"));
        }
        if (!COMPILER.isAvailable()) {
            return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.UNSUPPORTED, "unsupported_host"));
        }
        if (methodBody == null || methodBody.isBlank()) {
            return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.FAILED, "empty_source"));
        }
        if (methodBody.length() > MAX_SOURCE_LENGTH) {
            return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.FAILED, "source_too_long"));
        }
        if (!CONFIRMATION_PENDING.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(JvmCodeResult.of(JvmCodeStatus.BUSY, "confirmation_pending"));
        }

        CompletableFuture<JvmCodeResult> resultFuture = new CompletableFuture<>();
        resultFuture.whenComplete((result, error) -> CONFIRMATION_PENDING.set(false));

        JvmCodeClassification classification = JvmCodeSafetyScanner.scan(methodBody);
        if (classification == JvmCodeClassification.SAFE) {
            // 白名单放行：只碰游戏 API 且不涉及关闭游戏的代码免确认直接执行
            LOGGER.info("Auto-approved safe jvm_code_skill body (whitelisted); no confirmation shown");
            runApproved(methodBody, resultFuture);
            return resultFuture;
        }

        // 涉及关闭/退出游戏（LIFECYCLE）或主机级危险操作（DANGEROUS）：一律弹确认框。
        // 关闭游戏操作即使"只碰游戏 API"，普通玩家也看不出后果，绝不放行。
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> openConfirmation(minecraft, methodBody, classification, resultFuture));
        return resultFuture;
    }

    private static void openConfirmation(Minecraft minecraft, String methodBody, JvmCodeClassification classification,
                                         CompletableFuture<JvmCodeResult> resultFuture) {
        if (minecraft.player == null || !LLMConfig.isJvmCodeSkillEnabled()) {
            resultFuture.complete(JvmCodeResult.of(JvmCodeStatus.DISABLED, "client_unavailable"));
            return;
        }

        Screen previousScreen = minecraft.screen;
        minecraft.setScreen(new JvmCodeConfirmScreen(previousScreen, methodBody, classification, approved -> {
            if (!approved) {
                LOGGER.info("JVM code execution was cancelled by the player");
                resultFuture.complete(JvmCodeResult.of(JvmCodeStatus.CANCELLED, "player_cancelled"));
                return;
            }
            runApproved(methodBody, resultFuture);
        }));
    }

    /** 编译并执行（确认通过或白名单放行后共用）。 */
    private static void runApproved(String methodBody, CompletableFuture<JvmCodeResult> resultFuture) {
        CompletableFuture.supplyAsync(() -> compileAndRun(methodBody), COMPILE_EXECUTOR)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOGGER.error("JVM code execution failed", error);
                        resultFuture.complete(JvmCodeResult.of(JvmCodeStatus.FAILED, "execution_exception"));
                    } else {
                        resultFuture.complete(result);
                    }
                });
    }

    private static JvmCodeResult compileAndRun(String methodBody) {
        JvmCodeCompiler.JvmCodeCompileResult compiled = COMPILER.compile(methodBody);
        if (!compiled.ok()) {
            return JvmCodeResult.of(JvmCodeStatus.FAILED, "compile_error:" + compiled.error());
        }
        return EXECUTOR.execute(compiled.action())
                .thenApply(outcome -> outcome.success()
                        ? JvmCodeResult.of(JvmCodeStatus.SUCCESS, outcome.detail())
                        : JvmCodeResult.of(JvmCodeStatus.FAILED, "runtime_error:" + outcome.detail()))
                .join();
    }
}
