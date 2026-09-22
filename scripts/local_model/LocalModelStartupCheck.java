package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.whitecloud233.herobrine_companion.BuildFlags;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.whitecloud233.herobrine_companion.client.service.LocalModelStartupDiagnostics.Kind.*;

/** No engine/model downloads, no real game configuration, and no native inference process. */
public final class LocalModelStartupCheck {
    private static int checks;
    private static volatile int healthCode = 503;
    private static volatile String healthBody = "{\"status\":\"loading model\"}";
    private static volatile String modelsBody = "{\"data\":[{\"id\":\"herobrine\"}]}";
    private static volatile CountDownLatch entered;
    private static volatile CountDownLatch release;
    private static final AtomicInteger modelRequests = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        check(!BuildFlags.CF_SAFE, "Run with -Pfull=true so the real launcher is tested");
        Path runtime = Path.of("build/local-model-validation/runtime", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(runtime.resolve("bin"));
        FMLPaths.loadAbsolutePaths(runtime);
        set(LLMConfig.class, "loaded", true);
        set(LocalModelLauncher.class, "customRootLoaded", true);
        set(LocalModelLauncher.class, "customRootDir", runtime);
        set(LocalModelLauncher.class, "modelChoiceLoaded", true);
        set(LocalModelLauncher.class, "modelChoiceKey", "qwen-3b-tuned");
        set(LocalModelLauncher.class, "gpuAvailableProbed", true);
        set(LocalModelLauncher.class, "gpuAvailable", false);
        classifyFailures();
        responseValidation();
        boundedLogReads(runtime);
        translations();
        healthLifecycle();
        launchFailure(runtime);
        String report = "{\"checks\":" + checks + ",\"result\":\"passed\",\"realModelInference\":false}";
        Files.writeString(Path.of("build/local-model-validation/validation.json"), report, StandardCharsets.UTF_8);
        System.out.println("LOCAL_MODEL_STARTUP_OK: " + checks + " checks; native errors, HTTP readiness, cancellation, CPU retry");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void kind(LocalModelStartupDiagnostics.Kind expected, Integer exit, String log,
                             String error, boolean timeout, boolean gpu) {
        var actual = LocalModelStartupDiagnostics.classify(exit, log, error, timeout, gpu);
        check(actual == expected, "Expected " + expected + ", got " + actual + " for " + exit + ": " + log + error);
    }

    private static void classifyFailures() {
        kind(DEPENDENCY, 0xC0000135, "", "", false, true);
        kind(VC_RUNTIME, 0xC0000135, "VCRUNTIME140_1.dll was not found", "", false, false);
        kind(VC_RUNTIME, null, "", "MSVCP140.dll is missing", false, false);
        kind(CUDA_LIBRARY, 1, "could not load cublasLt64_13.dll", "", false, true);
        kind(DEPENDENCY, null, "", "CreateProcess error=126, The specified module could not be found", false, true);
        kind(ENGINE_INCOMPATIBLE, 0xC000007B, "", "", false, false);
        kind(ENGINE_INCOMPATIBLE, null, "", "CreateProcess error=193, not a valid Win32 application", false, false);
        kind(CPU_UNSUPPORTED, 0xC000001D, "", "", false, false);
        kind(MEMORY, 0xC0000017, "", "", false, false);
        kind(MEMORY, 0xC000012D, "", "", false, true);
        kind(MEMORY, null, "", "CreateProcess error=1455, paging file is too small", false, true);
        kind(CUDA_DRIVER, 1, "ggml_cuda_init: failed to initialize CUDA: CUDA driver version is insufficient for CUDA runtime version", "", false, true);
        kind(CUDA_UNSUPPORTED, 1, "CUDA error: no kernel image is available for execution on the device", "", false, true);
        kind(GPU_MEMORY, 1, "ggml_cuda_init: found 1 CUDA devices\nCUDA error: out of memory\nfailed to load model", "", false, true);
        kind(GPU_MEMORY, 1, "failed to allocate CUDA0 buffer", "", false, true);
        kind(MEMORY, 1, "loaded ggml-cuda.dll\nstd::bad_alloc\nfailed to load model", "", false, true);
        kind(PORT_BUSY, 1, "failed to bind server socket: address already in use", "", false, false);
        kind(ACCESS_BLOCKED, null, "", "CreateProcess error=5, Access is denied", false, false);
        kind(ACCESS_BLOCKED, null, "", "CreateProcess error=225, virus or potentially unwanted software", false, true);
        kind(ENGINE_MISSING, null, "", "CreateProcess error=2, The system cannot find the file specified", false, false);
        kind(DLL_INIT, 0xC0000142, "", "", false, false);
        kind(MODEL, 1, "loaded ggml-cuda.dll\nggml_cuda_init: found 1 CUDA devices\nfailed to load model: invalid magic", "", false, true);
        kind(CUDA_INIT, 1, "ggml_cuda_init: failed to initialize CUDA: (null)", "", false, true);
        kind(EXITED, 0xC0000005, "loaded ggml-cuda.dll\nggml_cuda_init: found 1 CUDA devices", "", false, true);
        kind(TIMEOUT, null, "llama_model_loader: loading tensors", "", true, true);
        kind(NO_OUTPUT, null, "", "", true, false);
        kind(LAUNCH, null, "", "IOException: unknown launch error", false, false);
        check(LocalModelStartupDiagnostics.formatExitCode(0xC0000135).equals("-1073741515 (0xC0000135)"),
                "Windows signed exit codes must also show searchable hexadecimal status");
    }

    private static void responseValidation() {
        check(LocalModelConnector.isReadyHealthResponse(200, "{\"status\":\"ok\"}"), "A ready health response is accepted");
        for (String body : new String[]{"{\"status\":\"loading model\"}", "{\"status\":\"error\"}", "{}", "[]", "null",
                "{\"status\":true}", "{\"status\":0}", "<html>some other service</html>", "", null}) {
            check(!LocalModelConnector.isReadyHealthResponse(200, body), "HTTP 200 alone is not readiness: " + body);
        }
        check(!LocalModelConnector.isReadyHealthResponse(503, "{\"status\":\"ok\"}"), "An HTTP error cannot be healthy");
        check("test-model".equals(LocalModelConnector.parseFirstModelId("{\"data\":[{\"id\":\" test-model \"}]}")),
                "A real model identifier is preserved");
        for (String body : new String[]{"{\"data\":[]}", "{}", "{\"data\":[{\"id\":true}]}",
                "{\"data\":[{\"id\":\" \"}]}", "not json", "null", null}) {
            check(LocalModelConnector.parseFirstModelId(body) == null, "Do not invent a model for invalid/empty lists");
        }
    }

    private static void boundedLogReads(Path runtime) throws Exception {
        Path log = runtime.resolve("large.log");
        byte[] tail = "last CUDA error: out of memory\n".getBytes(StandardCharsets.UTF_8);
        try (SeekableByteChannel channel = Files.newByteChannel(log, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(16 * 1024 * 1024);
            channel.write(ByteBuffer.wrap(tail));
        }
        check(LocalModelStartupDiagnostics.readLogTail(log, tail.length).equals(new String(tail, StandardCharsets.UTF_8)),
                "Read only the end of a large log");
        check(LocalModelStartupDiagnostics.readLogTail(log, 0).isEmpty(), "An empty budget reads nothing");
        check(LocalModelStartupDiagnostics.readLogTail(runtime.resolve("absent.log"), 1024).isEmpty(), "Missing log is allowed");
    }

    private static void translations() throws Exception {
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            JsonObject language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/herobrine_companion/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var kind : LocalModelStartupDiagnostics.Kind.values()) {
                check(language.has(kind.key) && !language.get(kind.key).getAsString().isBlank(),
                        "Missing " + locale + " diagnostic: " + kind.key);
            }
        }
    }

    private static void healthLifecycle() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", LocalModelLauncher.DEFAULT_PORT), 0);
        var executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "local-model-regression-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/health", exchange -> {
            int code = healthCode;
            String body = healthBody;
            CountDownLatch seen = entered;
            CountDownLatch gate = release;
            if (seen != null) seen.countDown();
            if (gate != null) {
                try { gate.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            respond(exchange, code, body);
        });
        server.createContext("/v1/models", exchange -> {
            modelRequests.incrementAndGet();
            respond(exchange, 200, modelsBody);
        });
        server.start();
        try {
            FakeProcess process = new FakeProcess();
            set(LocalModelLauncher.class, "serverProcess", process);
            set(LocalModelLauncher.class, "serverRunning", false);
            set(LocalModelLauncher.class, "sessionClosed", false);
            check(!LocalModelLauncher.isServerRunningCached(), "A live process without health is not ready");
            check(!await(LocalModelLauncher.probeServerRunningAsync()), "Loading process is not ready (503)");
            healthCode = 200;
            check(!await(LocalModelLauncher.probeServerRunningAsync()), "Loading process is not ready (200)");
            check(!await(LocalModelConnector.probeSingle("http://127.0.0.1:8090")).reachable(), "Discovery rejects a loading llama server");
            check(modelRequests.get() == 0, "Discovery does not trust the model list while health is loading");
            healthBody = "{\"status\":\"ok\"}";
            check(await(LocalModelLauncher.probeServerRunningAsync()), "Live process with health OK is ready");
            check(LocalModelLauncher.isServerRunningCached(), "Successful health is cached");
            modelsBody = "{\"data\":[]}";
            check(!await(LocalModelConnector.probeSingle("http://127.0.0.1:8090")).reachable(), "Empty model list is not connected");
            modelsBody = "<html>unrelated web server</html>";
            check(!await(LocalModelConnector.probeSingle("http://127.0.0.1:8090")).reachable(), "HTML is not a model server");
            modelsBody = "{\"data\":[{\"id\":\"test-model\"}]}";
            var connected = await(LocalModelConnector.probeEndpointAsync(LocalModelLauncher.DEFAULT_ENDPOINT));
            check(connected.reachable() && connected.modelId().equals("test-model"), "Configured endpoint resolves the actual model");
            check(!await(LocalModelConnector.probeEndpointAsync("bad://host/v1/chat/completions")).reachable(),
                    "Malformed endpoint fails without an unhandled exception");
            process.alive = false;
            check(!LocalModelLauncher.isServerRunningCached(), "A crashed owned process clears previously true cache");
            check(!await(LocalModelLauncher.probeServerRunningAsync()), "A dead owned process cannot reuse another response");

            // A delayed response from before Stop must never turn the stopped state back on.
            process = new FakeProcess();
            set(LocalModelLauncher.class, "serverProcess", process);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            var pending = LocalModelLauncher.probeServerRunningAsync();
            check(entered.await(3, TimeUnit.SECONDS), "Health request was issued even though the process is alive");
            LocalModelLauncher.stopOwnedServer();
            release.countDown();
            check(!await(pending) && !LocalModelLauncher.isServerRunningCached(), "Stale health cannot revive a stopped process");
            check(!process.alive, "Stop terminates the owned process");
            entered = null;
            release = null;

            // Also guard the no-owned-process case, where identity alone cannot detect a stop.
            set(LocalModelLauncher.class, "sessionClosed", false);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            var externalProbe = LocalModelLauncher.probeServerRunningAsync();
            check(entered.await(3, TimeUnit.SECONDS), "External health request started");
            LocalModelLauncher.stopOwnedServer();
            release.countDown();
            check(!await(externalProbe) && !LocalModelLauncher.isServerRunningCached(), "Stop also invalidates pending external health");
            entered = null;
            release = null;

            process = new FakeProcess();
            set(LocalModelLauncher.class, "serverProcess", process);
            set(LocalModelLauncher.class, "sessionClosed", false);
            healthBody = "{\"status\":\"loading model\"}";
            entered = new CountDownLatch(1);
            var loading = waitHealth(process, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            check(entered.await(3, TimeUnit.SECONDS), "Startup poll started");
            entered = null;
            healthBody = "{\"status\":\"ok\"}";
            check(await(loading), "Loading transitions to ready using health, without replacing the process");
            long before = System.nanoTime();
            check(!await(waitHealth(process, before - 1)), "An expired wall-clock deadline fails immediately");
            check(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1), "Deadline does not depend on attempt count");

            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            var cancelled = waitHealth(process, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            check(entered.await(3, TimeUnit.SECONDS), "Cancellation test has an in-flight health request");
            LocalModelLauncher.stopOwnedServer();
            release.countDown();
            check(!await(cancelled), "A stopped startup cannot later report ready");
        } finally {
            if (release != null) release.countDown();
            entered = null;
            release = null;
            LocalModelLauncher.stopOwnedServer();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void launchFailure(Path runtime) throws Exception {
        set(LocalModelLauncher.class, "sessionClosed", false);
        set(LocalModelLauncher.class, "gpuEngine", true);
        Method method = LocalModelLauncher.class.getDeclaredMethod("startServerAsync", Consumer.class);
        method.setAccessible(true);
        Object result = await((CompletableFuture<?>) method.invoke(null, (Object) null));
        Method ready = result.getClass().getDeclaredMethod("ready");
        ready.setAccessible(true);
        check(Boolean.FALSE.equals(ready.invoke(result)), "Missing native executable is a diagnosed failure, not an exceptional future");
        String diagnostic = LocalModelLauncher.lastStartupDiagnostic();
        check(diagnostic.contains("Initial GPU startup diagnostic:"),
                "CPU retry keeps the original GPU launch failure available");
        check(diagnostic.contains("CPU startup failed:") && diagnostic.contains("CUDA startup failed:"),
                "The translated report identifies both attempts");
        check(diagnostic.contains("The engine or its directory was not found"),
                "A Windows launch failure is shown with actionable translated text");
        check(diagnostic.contains(runtime.resolve("bin/llama-server.exe").toString()),
                "The full diagnostic includes the actual engine path");
        check(diagnostic.contains("CreateProcess error=2"), "The native exception is retained for support");
        check(!LocalModelLauncher.isServerRunningCached(), "Both failed attempts leave no ready cache");
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Boolean> waitHealth(Process process, long deadline) throws Exception {
        Method method = LocalModelLauncher.class.getDeclaredMethod("waitForHealthInternal", Process.class, Consumer.class, long.class);
        method.setAccessible(true);
        return (CompletableFuture<Boolean>) method.invoke(null, process, null, deadline);
    }

    private static void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(12, TimeUnit.SECONDS);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        exchange.close();
    }

    private static final class FakeProcess extends Process {
        volatile boolean alive = true;
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { alive = false; return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { alive = false; }
        @Override public Process destroyForcibly() { alive = false; return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
