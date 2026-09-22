package com.whitecloud233.herobrine_companion.client.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native engine failures can happen before the engine writes a single log line. */
final class LocalModelStartupDiagnostics {
    private static final Pattern WINDOWS_LAUNCH_ERROR = Pattern.compile("error\\s*=\\s*(\\d+)\\b");

    enum Kind {
        VC_RUNTIME("vc_runtime"), DEPENDENCY("dependency"), CUDA_LIBRARY("cuda_library"),
        CUDA_DRIVER("cuda_driver"), CUDA_UNSUPPORTED("cuda_unsupported"), CUDA_INIT("cuda_init"),
        GPU_MEMORY("gpu_memory"), MEMORY("memory"), CPU_UNSUPPORTED("cpu_unsupported"),
        ENGINE_INCOMPATIBLE("engine_incompatible"), ENGINE_MISSING("engine_missing"),
        ACCESS_BLOCKED("access_blocked"), DLL_INIT("dll_init"), PORT_BUSY("port_busy"),
        MODEL("model"), TIMEOUT("timeout"), NO_OUTPUT("no_output"), LAUNCH("launch"), EXITED("exited");

        final String key;

        Kind(String key) {
            this.key = "gui.herobrine_companion.local_model.startup_" + key;
        }
    }

    private LocalModelStartupDiagnostics() {
    }

    static Kind classify(Integer exitCode, String logTail, String launchError, boolean timedOut, boolean gpuMode) {
        String error = lower(launchError);
        String text = lower(logTail) + "\n" + error;
        int launchCode = -1;
        Matcher matcher = WINDOWS_LAUNCH_ERROR.matcher(error);
        if (matcher.find()) {
            try {
                launchCode = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Keep unknown errors unknown.
            }
        }
        if (launchCode == 5 || launchCode == 225 || launchCode == 226
                || containsAny(text, "access is denied", "accessdeniedexception", "permission denied", "拒绝访问")) {
            return Kind.ACCESS_BLOCKED;
        }
        if (isExit(exitCode, 0xC000007B) || launchCode == 193 || launchCode == 216) {
            return Kind.ENGINE_INCOMPATIBLE;
        }
        if (isExit(exitCode, 0xC000001D) || text.contains("illegal instruction")) {
            return Kind.CPU_UNSUPPORTED;
        }
        if (isExit(exitCode, 0xC0000017) || isExit(exitCode, 0xC000009A) || isExit(exitCode, 0xC000012D)
                || launchCode == 8 || launchCode == 14 || launchCode == 1455) {
            return Kind.MEMORY;
        }
        Kind namedLibraryFailure = null;
        for (String line : text.split("\\R")) {
            // A successful "loaded ggml-cuda.dll" line must not turn an unrelated model error into a DLL error.
            if (containsAny(line, "not found", "missing", "cannot load", "could not load", "failed to load",
                    "unable to load", "找不到", "无法加载")) {
                if (containsAny(line, "vcruntime140", "msvcp140", "api-ms-win-crt")) return Kind.VC_RUNTIME;
                if (containsAny(line, "cublas", "cudart", "ggml-cuda.dll")) return Kind.CUDA_LIBRARY;
                if (containsAny(line, ".dll", "shared library", "shared libraries")) namedLibraryFailure = Kind.DEPENDENCY;
            }
        }
        if (isExit(exitCode, 0xC0000135) || launchCode == 126) {
            // STATUS_DLL_NOT_FOUND does not identify which DLL is missing. Do not call it VC++ unconditionally.
            return Kind.DEPENDENCY;
        }
        if (containsAny(text, "cuda driver version is insufficient", "cudaerrorinsufficientdriver",
                "cuda_error_insufficient_driver", "unsupported display driver", "driver too old",
                "cuda_error_system_driver_mismatch", "system driver mismatch")) {
            return Kind.CUDA_DRIVER;
        }
        if (containsAny(text, "no kernel image", "invalid device function", "unsupported gpu architecture")) {
            return Kind.CUDA_UNSUPPORTED;
        }
        for (String line : text.split("\\R")) {
            if (containsAny(line, "out of memory", "not enough memory", "failed to allocate", "cannot allocate memory",
                    "bad_alloc", "paging file is too small", "页面文件太小", "cuda error: 2")) {
                return gpuMode && containsAny(line, "cuda", "gpu", "vram", "device memory")
                        ? Kind.GPU_MEMORY : Kind.MEMORY;
            }
        }
        if (containsAny(text, "address already in use", "failed to bind", "bind failed", "binding failed",
                "only one usage of each socket address", "10048")) {
            return Kind.PORT_BUSY;
        }
        if (namedLibraryFailure != null) {
            return namedLibraryFailure;
        }
        if (containsAny(text, "failed to load model", "error loading model", "invalid gguf", "invalid magic",
                "unsupported model architecture", "failed to load vocabulary", "failed to read model")) {
            return Kind.MODEL;
        }
        if (launchCode == 2 || launchCode == 3) {
            return Kind.ENGINE_MISSING;
        }
        if (text.contains("failed to initialize cuda")) {
            return Kind.CUDA_INIT;
        }
        if (isExit(exitCode, 0xC0000142)) {
            return Kind.DLL_INIT;
        }
        if (timedOut) {
            return lower(logTail).isBlank() ? Kind.NO_OUTPUT : Kind.TIMEOUT;
        }
        return error.isBlank() ? Kind.EXITED : Kind.LAUNCH;
    }

    static String formatExitCode(int code) {
        return code + " (" + String.format(Locale.ROOT, "0x%08X", code) + ")";
    }

    /** Bound both memory use and disk reads even when a reused server has a large log. */
    static String readLogTail(Path path, int maxBytes) {
        if (path == null || maxBytes <= 0) {
            return "";
        }
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            long size = channel.size();
            int length = (int) Math.min(size, maxBytes);
            channel.position(size - length);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // Only read the bounded tail, never the entire log.
            }
            buffer.flip();
            return StandardCharsets.UTF_8.decode(buffer).toString();
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }

    private static boolean isExit(Integer actual, int expected) {
        return actual != null && actual == expected;
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }
}
