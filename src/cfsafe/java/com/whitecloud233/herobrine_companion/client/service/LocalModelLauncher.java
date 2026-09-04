package com.whitecloud233.herobrine_companion.client.service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 安全版惰性 stub（仅安全构建编译）。
 *
 * <p>本地模型功能（运行时下载 llama.cpp 推理引擎、GGUF 模型并启动 llama-server 进程）在
 * 安全版构建中被排除——与电脑 CMD、JVM 代码注入、联网查找同组。本类与完整版同 FQCN、
 * 同包可见性，保证 {@code LocalModelScreen} / {@code ClientModSetup} / {@code ApiKeyInputScreen}
 * 等引用方可编译：目录恒为空、下载/启动恒失败、探测恒为"不可用"。
 *
 * <p><b>不包含任何 URL / HttpClient / ProcessBuilder / 文件下载代码</b>，安全版 jar 内不含
 * 任何可被静态扫描标记的"下载并执行远程文件"字节码。完整版（-Pfull=true）才编译
 * src/full/java 下的完整实现。</p>
 */
public final class LocalModelLauncher {
    public static final String SERVER_EXE = "llama-server.exe";
    public static final int DEFAULT_PORT = 8090;
    public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:" + DEFAULT_PORT + "/v1/chat/completions";
    public static final int LOCAL_CTX = 16384;

    /** 安全版不可用提示（页面状态行 / 聊天提示展示）。 */
    private static final String UNAVAILABLE_MSG = "本地模型（下载推理引擎并启动进程）不在当前安全版构建中，请使用 -Pfull=true 完整版。";

    public record CatalogEntry(
            String key,
            String displayName,
            String family,
            long approxBytes,
            long minVramMb,
            long minSystemRamMb,
            String repoNs,
            String repoName,
            List<TierFile> files
    ) {
    }

    public record ModelTier(String key, String displayName, List<TierFile> files) {
        public TierFile firstFile() {
            return files.isEmpty() ? null : files.get(0);
        }
    }

    public record TierFile(String fileName, List<String> urls) {
    }

    public record DownloadProgress(long downloaded, long total, String label) {
        public double fraction() {
            return total > 0 ? Math.min(1.0, (double) downloaded / total) : 0.0;
        }
    }

    public record PrepareResult(boolean ready, String endpoint, String modelId, String message) {
    }

    private LocalModelLauncher() {
    }

    /**
     * 目录仅含一个占位条目（无任何下载地址）：若页面在安全版被误打开，渲染与滚动逻辑不会
     * 因空目录崩溃，且该条目不可下载、不可启动。
     */
    public static List<CatalogEntry> catalog() {
        return List.of(new CatalogEntry("local-unavailable", "本地模型（安全版不可用）", "",
                0L, 0L, 0L, "", "", List.of()));
    }

    public static CatalogEntry catalogEntry(String key) {
        return null;
    }

    public static String modelChoiceKey() {
        return "";
    }

    public static void setModelChoice(String key) {
    }

    public static CatalogEntry autoModelEntry() {
        return null;
    }

    public static CatalogEntry recommendedEntry() {
        return null;
    }

    public static ModelTier selectedModelTier() {
        return null;
    }

    public static long gpuVramMb() {
        return 0L;
    }

    public static String gpuDowngradeReason() {
        return "";
    }

    public static String gpuName() {
        return "";
    }

    public static boolean supportsGpu() {
        return false;
    }

    public static Path rootDir() {
        return Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }

    public static void setCustomRootDir(Path dir) {
    }

    public static boolean hasCustomRootDir() {
        return false;
    }

    public static boolean needsDownload() {
        return false;
    }

    public static Path modelsDir() {
        return rootDir().resolve("models");
    }

    public static Path binDir() {
        return rootDir().resolve("bin");
    }

    public static Path modelPath() {
        return modelsDir().resolve("local-model.gguf");
    }

    public static Path tierFilePath(TierFile tierFile) {
        return modelsDir().resolve(tierFile.fileName());
    }

    public static Path serverPath() {
        return binDir().resolve(SERVER_EXE);
    }

    public static boolean serverPresent() {
        return false;
    }

    public static boolean serverGpuPresent() {
        return false;
    }

    public static boolean modelPresent() {
        return false;
    }

    public static boolean entryFilesPresent(CatalogEntry entry) {
        return false;
    }

    public static CompletableFuture<PrepareResult> prepareAsync(Consumer<DownloadProgress> progress) {
        return CompletableFuture.completedFuture(
                new PrepareResult(false, DEFAULT_ENDPOINT, "", UNAVAILABLE_MSG));
    }

    public static CompletableFuture<Path> downloadSelectedAsync(Consumer<DownloadProgress> progress) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(UNAVAILABLE_MSG));
    }

    public static void shutdownForSession() {
    }

    public static void stopOwnedServer() {
    }

    public static boolean isServerRunningCached() {
        return false;
    }

    public static CompletableFuture<Boolean> probeServerRunningAsync() {
        return CompletableFuture.completedFuture(false);
    }

    public static CompletableFuture<Void> stopServerAndReclaimPortAsync() {
        return CompletableFuture.completedFuture(null);
    }

    public static String rootMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? UNAVAILABLE_MSG
                : throwable.getMessage();
    }
}