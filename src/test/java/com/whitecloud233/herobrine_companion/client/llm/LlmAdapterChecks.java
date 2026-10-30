package com.whitecloud233.herobrine_companion.client.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.format.AnthropicAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.GeminiAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.OpenAiChatAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.OpenAiResponsesAdapter;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class LlmAdapterChecks {
    private static int checks;

    public static void main(String[] args) {
        OpenAiChatAdapter chat = new OpenAiChatAdapter();
        AnthropicAdapter anthropic = new AnthropicAdapter();
        GeminiAdapter gemini = new GeminiAdapter();
        OpenAiResponsesAdapter responses = new OpenAiResponsesAdapter();

        result(chat.parseResponse(json("""
                {"choices":[{"finish_reason":"stop","message":{"content":"hello","reasoning_content":"notes"}}]}
                """)), "hello", "notes", null, LlmFinishReason.COMPLETE);
        result(anthropic.parseResponse(json("""
                {"content":[{"type":"thinking","thinking":"notes"},{"type":"text","text":"hello"}],"stop_reason":"end_turn"}
                """)), "hello", "notes", null, LlmFinishReason.COMPLETE);
        result(gemini.parseResponse(json("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"notes"},{"text":"hello"}]}}]}
                """)), "hello", "notes", null, LlmFinishReason.COMPLETE);
        result(responses.parseResponse(json("""
                {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"hello"}]}]}
                """)), "hello", "", null, LlmFinishReason.COMPLETE);

        tool(chat.parseResponse(json("""
                {"choices":[{"finish_reason":"tool_calls","message":{"tool_calls":[{"function":{"name":"inspect","arguments":"{\\"query\\":\\"stone\\"}"}}]}}]}
                """)));
        tool(anthropic.parseResponse(json("""
                {"content":[{"type":"tool_use","name":"inspect","input":{"query":"stone"}}],"stop_reason":"tool_use"}
                """)));
        tool(gemini.parseResponse(json("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"functionCall":{"name":"inspect","args":{"query":"stone"}}}]}}]}
                """)));
        tool(responses.parseResponse(json("""
                {"status":"completed","output":[{"type":"function_call","name":"inspect","arguments":"{\\"query\\":\\"stone\\"}"}]}
                """)));

        check(chat.parseResponse(json("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{}}]}")).truncated(), "Chat token limit missing");
        check(anthropic.parseResponse(json("{\"content\":[],\"stop_reason\":\"max_tokens\"}")).truncated(), "Anthropic token limit missing");
        check(gemini.parseResponse(json("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")).truncated(), "Gemini token limit without content missing");
        check(responses.parseResponse(json("{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}")).truncated(), "Responses token limit missing");
        check(gemini.parseResponse(json("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).finishReason() == LlmFinishReason.CONTENT_FILTERED, "Gemini blocked prompt missing");
        check(responses.parseResponse(json("{\"status\":\"failed\"}")).failure() != null, "Responses failed status is not success");

        String chatStream = sse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"notes\"}}]}")
                + sse("{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}")
                + sse("{malformed}")
                + sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"inspect\",\"arguments\":\"{\\\"query\\\":\"}}]}}]}")
                + sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"stone\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}")
                + sse("[DONE]");
        LlmResponse chatResult = stream(chat, chatStream);
        result(chatResult, "hello", "notes", "inspect", LlmFinishReason.TOOL_CALL);
        tool(chatResult);

        String anthropicStream = event("content_block_delta", "{\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"notes\"}}")
                + event("content_block_delta", "{\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}")
                + event("content_block_start", "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"name\":\"inspect\",\"input\":{}}}")
                + event("content_block_delta", "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"query\\\":\"}}")
                + event("content_block_delta", "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"stone\\\"}\"}}")
                + event("content_block_stop", "{\"index\":1}")
                + event("message_delta", "{\"delta\":{\"stop_reason\":\"tool_use\"}}");
        LlmResponse anthropicResult = stream(anthropic, anthropicStream);
        result(anthropicResult, "hello", "notes", "inspect", LlmFinishReason.TOOL_CALL);
        tool(anthropicResult);
        result(stream(anthropic, event("message_delta", "{\"delta\":{\"stop_reason\":\"max_tokens\"}}")),
                "", "", null, LlmFinishReason.TOKEN_LIMIT);

        String geminiStream = sse("{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"notes\"},{\"text\":\"hello\"}]}}]}")
                + sse("{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"inspect\",\"args\":{\"query\":\"stone\"}}}]},\"finishReason\":\"STOP\"}]}");
        LlmResponse geminiResult = stream(gemini, geminiStream);
        result(geminiResult, "hello", "notes", "inspect", LlmFinishReason.COMPLETE);
        tool(geminiResult);
        result(stream(gemini, sse("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")), "", "", null, LlmFinishReason.TOKEN_LIMIT);

        String responseStream = event("response.reasoning_summary_text.delta", "{\"delta\":\"notes\"}")
                + event("response.output_text.delta", "{\"delta\":\"hello\"}")
                + event("response.output_item.added", "{\"output_index\":1,\"item\":{\"id\":\"call_1\",\"type\":\"function_call\",\"name\":\"inspect\",\"arguments\":\"\"}}")
                + event("response.function_call_arguments.delta", "{\"item_id\":\"call_1\",\"delta\":\"{\\\"query\\\":\"}")
                + event("response.function_call_arguments.delta", "{\"item_id\":\"call_1\",\"delta\":\"\\\"stone\\\"}\"}")
                + event("response.completed", "{\"response\":{\"status\":\"completed\"}}");
        LlmResponse responseResult = stream(responses, responseStream);
        result(responseResult, "hello", "notes", "inspect", LlmFinishReason.COMPLETE);
        tool(responseResult);
        result(stream(responses, event("response.incomplete", "{\"response\":{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}")),
                "", "", null, LlmFinishReason.TOKEN_LIMIT);

        for (LlmFormatAdapter adapter : List.of(chat, anthropic, gemini, responses)) {
            check(adapter.parseResponse(null).failure().kind() == LlmFailure.Kind.INVALID_RESPONSE, "Null response must be structured failure");
            check(adapter.parseResponse(json("{\"error\":{\"message\":\"failed\"}}")).failure() != null, "Provider error JSON is not success");
            check(!stream(adapter, sse("{\"type\":\"error\",\"error\":{\"message\":\"overloaded\"}}")).successful(), "HTTP 200 SSE error is not success");
            for (int status : new int[]{400, 401, 403, 429, 500}) {
                MemoryClient client = new MemoryClient(status, "test error");
                LlmResponse failure = adapter.readStreaming(client, request(), null, LoggerFactory.getLogger("LlmAdapterChecks"));
                check(failure.failure().statusCode() == status, "HTTP status must survive adaptation");
                check(client.body.closed, "Error response stream must be closed");
                LlmFailure.Kind expected = status == 401 || status == 403 ? LlmFailure.Kind.AUTHENTICATION
                        : status == 429 ? LlmFailure.Kind.RATE_LIMIT : status >= 500 ? LlmFailure.Kind.PROVIDER : LlmFailure.Kind.REQUEST_REJECTED;
                check(failure.failure().kind() == expected, "HTTP error classification changed");
            }
        }
        check(LlmFailure.http(400, "INVALID_API_KEY").kind() == LlmFailure.Kind.AUTHENTICATION, "Legacy key-error body detection changed");
        check(!LlmResponse.streamed("", "", "inspect", "{", LlmFinishReason.TOKEN_LIMIT).successful(), "Malformed tool args must not be executed as empty object");
        check(responses.extractToolInvocation(json("{\"output\":[{\"type\":\"function_call\",\"name\":\"inspect\",\"arguments\":\"{\"}]}")) == null,
                "Malformed Responses tool JSON must not become an empty invocation");
        String interleaved = event("response.output_item.added", "{\"output_index\":0,\"item\":{\"id\":\"a\",\"type\":\"function_call\",\"name\":\"inspect\",\"arguments\":\"\"}}")
                + event("response.function_call_arguments.delta", "{\"item_id\":\"a\",\"delta\":\"{\\\"query\\\":\"}")
                + event("response.output_item.added", "{\"output_index\":1,\"item\":{\"id\":\"b\",\"type\":\"function_call\",\"name\":\"other\",\"arguments\":\"\"}}")
                + event("response.function_call_arguments.delta", "{\"item_id\":\"b\",\"delta\":\"{\\\"query\\\":\\\"wrong\\\"}\"}")
                + event("response.function_call_arguments.delta", "{\"item_id\":\"a\",\"delta\":\"\\\"stone\\\"}\"}")
                + event("response.completed", "{\"response\":{\"status\":\"completed\"}}");
        tool(stream(responses, interleaved));
        tool(stream(gemini, sse("{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"inspect\",\"args\":{\"query\":\"stone\"}}},{\"functionCall\":{\"name\":\"other\",\"args\":{\"query\":\"wrong\"}}}]},\"finishReason\":\"STOP\"}]}")));
        check(!LlmResponse.streamed("", "", "inspect", null, LlmFinishReason.TOKEN_LIMIT).successful(), "Missing truncated arguments must not execute an empty invocation");
        check(LlmResponse.providerFailure("invalid_api_key").failure().kind() == LlmFailure.Kind.AUTHENTICATION, "SSE authentication errors must preserve classification");
        defensiveCopies();
        verifyOneShotDiagnostics(chat);
        System.out.println("LlmAdapterChecks: " + checks + " checks passed");
    }

    private static void verifyOneShotDiagnostics(LlmFormatAdapter adapter) {
        String raw = " {\n\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"hello\"}}]}\n";
        LlmOneShot.Exchange success = LlmOneShot.parseExchange(200, raw, adapter);
        check(success.statusCode() == 200 && raw.equals(success.rawBody()), "Successful HTTP metadata was discarded");
        check(success.result().successful() && success.result().text().equals("hello"), "Response parsing changed");

        for (int status : new int[]{400, 401, 429, 500}) {
            String error = "{\"error\":{\"message\":\"diagnostic-" + status + "\"}}";
            LlmOneShot.Exchange failure = LlmOneShot.parseExchange(status, error, adapter);
            check(failure.statusCode() == status && error.equals(failure.rawBody()), "Failed HTTP metadata was discarded");
            check(!failure.result().successful() && failure.result().failure().detail().equals(error),
                    "Supplier error details must survive for debug logging");
        }

        for (String body : new String[]{"{not-json", "{\"error\":{\"message\":\"overloaded\"}}"}) {
            LlmOneShot.Exchange failure = LlmOneShot.parseExchange(200, body, adapter);
            check(failure.statusCode() == 200 && body.equals(failure.rawBody()), "Invalid 200 response lost its raw body");
            check(!failure.result().successful() && !failure.result().failure().detail().isBlank(),
                    "Invalid 200 response must retain an explanation");
        }
    }

    private static void defensiveCopies() {
        JsonObject source = json("{\"nested\":{\"value\":1}}");
        LlmToolInvocation invocation = new LlmToolInvocation("inspect", source);
        LlmToolSpec spec = new LlmToolSpec("inspect", "description", source);
        source.getAsJsonObject("nested").addProperty("value", 2);
        invocation.arguments().getAsJsonObject("nested").addProperty("value", 3);
        spec.inputSchema().getAsJsonObject("nested").addProperty("value", 3);
        check(invocation.arguments().getAsJsonObject("nested").get("value").getAsInt() == 1, "Tool invocation aliases JSON");
        check(spec.inputSchema().getAsJsonObject("nested").get("value").getAsInt() == 1, "Tool schema aliases JSON");
        List<LlmChatMessage> messages = new ArrayList<>(List.of(new LlmChatMessage("user", "hello")));
        List<LlmToolSpec> tools = new ArrayList<>(List.of(spec));
        LlmChatPayload payload = new LlmChatPayload("system", messages, tools, 0.5, 0.9, 100, true, true);
        messages.clear();
        tools.clear();
        check(payload.messages().size() == 1 && payload.tools().size() == 1, "Request payload aliases list");
        try { payload.messages().clear(); throw new AssertionError("Mutable request list"); } catch (UnsupportedOperationException expected) { checks++; }
    }

    private static LlmResponse stream(LlmFormatAdapter adapter, String data) {
        MemoryClient client = new MemoryClient(200, data);
        List<String> partials = new ArrayList<>();
        LlmResponse response = adapter.readStreaming(client, request(), partials::add, LoggerFactory.getLogger("LlmAdapterChecks"));
        check(client.body.closed, "Successful response stream must be closed");
        check(partials.stream().noneMatch(text -> text.contains("notes")), "Reasoning must not leak into partial dialogue");
        return response;
    }

    private static void result(LlmResponse response, String text, String reasoning, String tool, LlmFinishReason reason) {
        check(response.successful(), "Unexpected response failure: " + response.failure());
        check(response.text().equals(text), "Text mismatch: " + response.text());
        check(response.reasoning().equals(reasoning), "Reasoning mismatch: " + response.reasoning());
        check(tool == null ? response.tool() == null : response.tool() != null && tool.equals(response.tool().name()), "Tool name shifted");
        check(response.finishReason() == reason, "Finish reason mismatch: " + response.finishReason());
    }

    private static void tool(LlmResponse response) {
        check(response.successful() && response.tool() != null && response.tool().name().equals("inspect"), "Tool invocation missing");
        check(response.tool().arguments().has("query") && response.tool().arguments().get("query").getAsString().equals("stone"), "Tool arguments lost or shifted");
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static String sse(String data) { return "data: " + data + "\n\n"; }
    private static String event(String event, String data) { return "event: " + event + "\n" + sse(data); }
    private static HttpRequest request() { return HttpRequest.newBuilder(URI.create("https://test.invalid/api")).build(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private static final class MemoryBody extends ByteArrayInputStream {
        boolean closed;
        MemoryBody(String text) { super(text.getBytes(StandardCharsets.UTF_8)); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    /** 返回内存中的 SSE，不打开端口、不调用外部服务，也不读取用户配置。 */
    private static final class MemoryClient extends HttpClient {
        private final int status;
        private final MemoryBody body;
        MemoryClient(int status, String data) { this.status = status; this.body = new MemoryBody(data); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return (HttpResponse<T>) new MemoryResponse(status, request, body);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> push) { throw new UnsupportedOperationException(); }
    }

    private record MemoryResponse(int statusCode, HttpRequest request, InputStream body) implements HttpResponse<InputStream> {
        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
