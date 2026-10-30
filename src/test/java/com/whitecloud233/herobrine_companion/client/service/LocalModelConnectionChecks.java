package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.whitecloud233.herobrine_companion.client.llm.LlmTask;
import net.neoforged.fml.loading.FMLPaths;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalModelConnectionChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path runtime = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "local-model-manual-checks-");
        FMLPaths.loadAbsolutePaths(runtime);
        endpoints();
        modelLists();
        localService(runtime);
        removedLauncher();
        System.out.println("LocalModelConnectionChecks: " + checks + " checks passed (loopback only, no native inference)");
    }

    private static void endpoints() {
        for (String endpoint : List.of(
                "http://127.0.0.1:8090/v1/chat/completions",
                "https://localhost:1234/v1/chat/completions",
                "http://127.0.0.2:11434/v1/chat/completions",
                "http://[::1]:8090/v1/chat/completions",
                "http://[0:0:0:0:0:0:0:1]:8090/v1/chat/completions",
                "http://localhost:8080/compat/v1/chat/completions")) {
            check(LocalModelEndpoint.normalize(endpoint).equals(endpoint), "Valid local endpoint rejected: " + endpoint);
            check(LLMConfig.isLoopbackEndpoint(endpoint), "Local request must bypass the cloud client: " + endpoint);
        }
        check(LocalModelEndpoint.normalize(" HTTP://LOCALHOST:8090/v1/chat/completions ")
                .equals("http://localhost:8090/v1/chat/completions"), "Normalize whitespace and host/scheme case");
        check(LocalModelEndpoint.modelsEndpoint("http://127.0.0.1:1234/compat/v1/chat/completions")
                .equals("http://127.0.0.1:1234/compat/v1/models"), "Keep the configured API prefix and port");
        for (String endpoint : List.of("", "localhost:8090", "http://localhost:8090/v1",
                "https://example.com/v1/chat/completions", "http://192.168.1.10/v1/chat/completions",
                "http://0.0.0.0:8090/v1/chat/completions", "file:///tmp/model.gguf",
                "ftp://127.0.0.1/v1/chat/completions", "http://localhost.evil.invalid/v1/chat/completions",
                "http://user:secret@localhost:8090/v1/chat/completions", "http://localhost@remote.invalid/v1/chat/completions",
                "http://127.0.0.1:0/v1/chat/completions", "http://127.0.0.1:65536/v1/chat/completions",
                "http://127.001.0.1/v1/chat/completions", "http://127.0.0.1/v1/chat/completions?redirect=remote",
                "http://127.0.0.1/v1/chat/completions#fragment", "http://127.0.0.1/../v1/chat/completions",
                "http://127.0.0.1/%2e%2e/v1/chat/completions", "http://127.0.0.1/v1/chat/completions/")) {
            check(LocalModelEndpoint.normalize(endpoint).isEmpty(), "Unsafe/incomplete endpoint accepted: " + endpoint);
        }
        check(LocalModelEndpoint.normalize(null).isEmpty(), "Null endpoint rejected");
        check(LocalModelConnector.isValidApiKey(""), "Local API key is optional");
        check(!LocalModelConnector.isValidApiKey("bad\nkey"), "Reject header injection");
        check(!LocalModelConnector.isValidApiKey("密钥"), "Reject unsupported HTTP header characters");
    }

    private static void modelLists() {
        check(LocalModelConnector.parseModelIds("{\"data\":[{\"id\":\" first \"},{\"id\":\"chosen\"},{\"id\":3}]}")
                .equals(List.of("first", "chosen")), "Parse all usable string model IDs");
        check(LocalModelConnector.parseModelIds("[{\"id\":\"chosen\"}]").equals(List.of("chosen")), "Accept a direct model array");
        check(LocalModelConnector.parseModelIds("{\"data\":[]}").isEmpty(), "Empty lists are not invented models");
        for (String body : List.of("<html>Error</html>", "{}", "null", "{\"data\":{}}", "{\"error\":\"failed\"}")) {
            check(LocalModelConnector.parseModelIds(body) == null, "Reject non-model responses: " + body);
        }
    }

    private static void localService(Path runtime) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger redirected = new AtomicInteger();
        AtomicInteger proxySelections = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>("");
        AtomicReference<String> method = new AtomicReference<>("");
        AtomicReference<Reply> reply = new AtomicReference<>(new Reply(200,
                "{\"data\":[{\"id\":\"other-model\"},{\"id\":\"chosen-model\"}]}"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/compat/v1/chat/completions";
        server.createContext("/compat/v1/models", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            Reply current = reply.get();
            if (current.code() == 302) exchange.getResponseHeaders().set("Location", "/redirected");
            byte[] bytes = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(current.code(), bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.createContext("/redirected", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        ProxySelector original = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                proxySelections.incrementAndGet();
                return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1)));
            }
            @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
        });
        server.start();
        try {
            check(HttpClients.direct(5).followRedirects() == HttpClient.Redirect.NEVER, "Local HTTP must not follow redirects");
            status(endpoint, "chosen-model", "test-key", LocalModelConnector.Status.READY);
            check("Bearer test-key".equals(authorization.get()), "Use the configured local credential");
            check(method.get().equals("GET"), "Testing must only read model metadata");
            status(endpoint, "chosen-model", "", LocalModelConnector.Status.READY);
            check("Bearer sk-local".equals(authorization.get()), "Blank credentials use the local placeholder");
            status(endpoint, "not-listed", "", LocalModelConnector.Status.MODEL_NOT_FOUND);
            check(proxySelections.get() == 0, "Local requests must ignore even the JVM default proxy");

            for (int code : new int[]{401, 403, 503, 404, 302}) {
                reply.set(new Reply(code, "failure"));
                var result = await(LocalModelConnector.probeEndpointAsync(endpoint, "chosen-model", ""));
                check(result.status() == (code == 401 || code == 403 ? LocalModelConnector.Status.AUTHENTICATION
                        : LocalModelConnector.Status.HTTP_ERROR), "HTTP status classification: " + code);
                check(result.statusCode() == code, "Preserve status for user diagnostics");
            }
            check(redirected.get() == 0, "A local endpoint must not redirect the request anywhere else");
            reply.set(new Reply(200, "<html>not a model server</html>"));
            status(endpoint, "chosen-model", "", LocalModelConnector.Status.INVALID_RESPONSE);
            reply.set(new Reply(200, "{\"data\":[]}"));
            status(endpoint, "chosen-model", "", LocalModelConnector.Status.MODEL_NOT_FOUND);

            int before = requests.get();
            status("https://example.com/v1/chat/completions", "chosen-model", "secret", LocalModelConnector.Status.INVALID_ENDPOINT);
            status(endpoint, "", "", LocalModelConnector.Status.MISSING_MODEL);
            status(endpoint, "chosen-model", "bad\nkey", LocalModelConnector.Status.INVALID_KEY);
            check(requests.get() == before, "Invalid settings must not send a request");

            configuration(runtime, endpoint, requests);
            reply.set(new Reply(200, "{\"data\":[{\"id\":\"chosen-model\"}]}"));
            status(endpoint, "chosen-model", "", LocalModelConnector.Status.READY);
            check(proxySelections.get() == 0, "Saving and disabling local settings must not contact a proxy");
        } finally {
            server.stop(0);
            ProxySelector.setDefault(original);
        }
        status(endpoint, "chosen-model", "", LocalModelConnector.Status.UNAVAILABLE);
    }

    private static void configuration(Path runtime, String endpoint, AtomicInteger requests) throws Exception {
        LLMConfig.load();
        LLMConfig.saveProviderSettings(LLMConfig.Provider.CUSTOM, "my-cloud", "cloud-test-key",
                "https://example.invalid/v1/chat/completions", "cloud-model", "Cloud Model");
        var cloud = LLMConfig.getProviderSettings(LLMConfig.Provider.CUSTOM);
        var observation = new LLMConfig.TaskRoute(LLMConfig.Provider.CUSTOM.getId(), "");
        LLMConfig.setTaskRoute(LlmTask.OBSERVE, observation);
        int before = requests.get();
        LocalModelConnector.applyConfig(endpoint, "chosen-model", "private-local-test-key");
        check(requests.get() == before, "Saving settings must not test, download, or start a server");
        check(LLMConfig.isLocalModelConnected(), "Manual local slot works in both build flavors");
        check(LLMConfig.getLocalLlmSettings().isUsable(), "The local slot does not need cloud credentials");
        check(LLMConfig.getLocalLlmSettings().apiKey().equals("private-local-test-key"), "Use the saved local key for chat");
        check(LLMConfig.getProvider() == LLMConfig.Provider.CUSTOM
                && cloud.equals(LLMConfig.getProviderSettings(LLMConfig.Provider.CUSTOM)), "Preserve the active cloud profile");
        for (LlmTask task : List.of(LlmTask.MAIN_CHAT, LlmTask.SCOPED_CHAT, LlmTask.CROSS_SESSION)) {
            var route = LLMConfig.resolveTaskSettings(task);
            check(route.primary().endpoint().equals(endpoint) && route.primary().model().equals("chosen-model"), "Local task routing: " + task);
            check(route.fallback() == null, "Do not introduce a silent cloud fallback: " + task);
        }
        check(LLMConfig.resolveTaskSettings(LlmTask.OBSERVE).primary().model().equals("cloud-model"), "Preserve unrelated task routes");
        Path publicFile = runtime.resolve("config/herobrine_companion/herobrine_companion_ai.json");
        Path secretsFile = runtime.resolve("herobrine_ai_secrets.json");
        String publicConfig = Files.readString(publicFile);
        JsonObject secrets = JsonParser.parseString(Files.readString(secretsFile)).getAsJsonObject();
        check(!publicConfig.contains("private-local-test-key") && !publicConfig.contains("localModelApiKey"), "Never expose the local credential in public config");
        check(secrets.get("localModelApiKey").getAsString().equals("private-local-test-key"), "Persist the key in private secrets");
        LLMConfig.load();
        check(LLMConfig.getLocalModelEndpoint().equals(endpoint) && LLMConfig.getLocalModelId().equals("chosen-model"), "Round-trip endpoint and model");
        check(LLMConfig.getLocalModelApiKey().equals("private-local-test-key") && LLMConfig.isLocalRouteActive(), "Round-trip key and routes");
        secrets.remove("localModelApiKey");
        Files.writeString(secretsFile, secrets.toString(), StandardCharsets.UTF_8);
        LLMConfig.load();
        check(LLMConfig.getLocalLlmSettings().apiKey().equals(LLMConfig.LOCAL_MODEL_KEY), "Old configurations without a local key remain usable");
        check(LLMConfig.getLocalModelId().equals("chosen-model"), "Do not auto-select another model while loading old settings");
        try {
            LLMConfig.setLocalModel("https://example.com/v1/chat/completions", "remote", "secret");
            throw new AssertionError("Remote endpoint accepted for the local slot");
        } catch (IllegalArgumentException expected) {
            check(LLMConfig.getLocalModelEndpoint().equals(endpoint), "Reject invalid settings without damaging the saved local slot");
        }
        LLMConfig.clearLocalModel();
        check(!LLMConfig.isLocalModelConnected() && !LLMConfig.isLocalRouteActive(), "Disable clears local slot and routes");
        check(LLMConfig.getLocalModelApiKey().isEmpty(), "Disable clears the local credential");
        LLMConfig.load();
        check(!LLMConfig.isLocalModelConnected(), "Disabled state survives reload");
        check(requests.get() == before, "Config changes must not send HTTP requests");
        check(!Files.exists(runtime.resolve("herobrine_local")), "No automatic engine/model directory");
    }

    private static void removedLauncher() throws Exception {
        ClassLoader loader = LocalModelConnectionChecks.class.getClassLoader();
        String base = "com/whitecloud233/herobrine_companion/";
        for (String name : List.of("client/service/LocalModelLauncher", "client/service/LocalModelStartupDiagnostics",
                "config/LocalModelDiagnosticsScreen")) {
            check(loader.getResource(base + name + ".class") == null, "Obsolete automatic launcher still compiled: " + name);
        }
        for (String name : List.of("client/service/LocalModelConnector", "client/service/LocalModelEndpoint", "config/LocalModelScreen")) {
            try (var stream = loader.getResourceAsStream(base + name + ".class")) {
                check(stream != null, "Manual local-model component missing: " + name);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                for (String forbidden : List.of("ProcessBuilder", "java/lang/ProcessHandle", "modelscope.cn", "huggingface.co",
                        "ZipInputStream", "downloadSelectedAsync", "java/nio/file/Files")) {
                    check(!bytecode.contains(forbidden), "Manual connection component contains " + forbidden + ": " + name);
                }
            }
        }
    }

    private static void status(String endpoint, String model, String key, LocalModelConnector.Status expected) throws Exception {
        var result = await(LocalModelConnector.probeEndpointAsync(endpoint, model, key));
        check(result.status() == expected, "Expected " + expected + ", got " + result.status());
        check(result.reachable() == (expected == LocalModelConnector.Status.READY), "Do not mark failed checks reachable");
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(12, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private record Reply(int code, String body) { }
}
