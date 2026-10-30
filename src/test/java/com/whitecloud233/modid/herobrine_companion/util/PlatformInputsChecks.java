package com.whitecloud233.modid.herobrine_companion.util;

import com.google.gson.*;
import com.whitecloud233.modid.herobrine_companion.client.tts.TtsOptions;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMContextWindow;
import java.lang.reflect.Method;

/** Boundary contracts which a raw GsonHelper/Mth replacement would silently change. */
public final class PlatformInputsChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        JsonObject data = JsonParser.parseString("{\"nil\":null,\"obj\":{},\"array\":[\"x\"],\"multi\":[1,2],\"n\":42,\"b\":true,\"s\":\"中文\"}").getAsJsonObject();
        for (String key : new String[]{"missing", "nil", "obj", "array", "multi"}) {
            check("lenient fallback: " + key, "fallback".equals(JsonInputs.string(data, key, "fallback")));
        }
        check("null input", "fallback".equals(JsonInputs.string(null, "s", "fallback")));
        check("numeric primitive", "42".equals(JsonInputs.string(data, "n", "")));
        check("boolean primitive", "true".equals(JsonInputs.string(data, "b", "")));
        check("string primitive", "中文".equals(JsonInputs.string(data, "s", "")));
        check("optional null", "fallback".equals(JsonInputs.optionalString(data, "nil", "fallback")));
        check("optional missing", "fallback".equals(JsonInputs.optionalString(data, "missing", "fallback")));
        check("optional singleton array retains old Gson conversion", "x".equals(JsonInputs.optionalString(data, "array", "")));
        for (String key : new String[]{"obj", "multi"}) {
            boolean rejected = false;
            try { JsonInputs.optionalString(data, key, "fallback"); } catch (RuntimeException expected) { rejected = true; }
            check("optional malformed remains visible: " + key, rejected);
        }
        String text = "引号\" 反斜杠\\\r\n\t\b\f\u0000\u001f";
        String json = "\"" + JsonInputs.escapeStringContent(text) + "\"";
        check("all JSON controls round trip", text.equals(JsonParser.parseString(json).getAsString()));
        check("null JSON text becomes empty", "".equals(JsonInputs.escapeStringContent(null)));
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            check("nonfinite uses fallback", NumberInputs.finite(value, 0, 1, .9) == .9);
        }
        check("finite lower clamp", NumberInputs.finite(-.5, 0, 1, .9) == 0);
        check("finite upper clamp", NumberInputs.finite(2, 0, 1, .9) == 1);
        check("zero int uses fallback", NumberInputs.positive(0, 20, 600, 200) == 200);
        check("negative int uses fallback", NumberInputs.positive(-1, 20, 600, 200) == 200);
        check("positive low int clamps", NumberInputs.positive(1, 20, 600, 200) == 20);
        check("positive high int clamps", NumberInputs.positive(601, 20, 600, 200) == 600);
        TtsOptions options = new TtsOptions(null, Double.NaN, Double.POSITIVE_INFINITY, 0, true, null, null, null);
        check("TTS invalid volume fallback", options.volume() == .9);
        check("TTS invalid speed fallback", options.speed() == 1);
        check("TTS unset maxChars fallback", options.maxChars() == 200);
        check("TTS positive maxChars clamps", new TtsOptions(null, .5, 1, 1, true, null, null, null).maxChars() == 20);
        probePayloads();
        System.out.println("PlatformInputsChecks: " + checks + " checks passed");
    }
    private static void probePayloads() throws Exception {
        Method build = LLMContextWindow.class.getDeclaredMethod("overflowProbePayload", LlmSettings.class);
        build.setAccessible(true);
        String model = "  model\"\\\t\r\n\u0001end  ";
        for (var format : new LLMConfig.EndpointFormat[]{LLMConfig.EndpointFormat.OPENAI_CHAT,
                LLMConfig.EndpointFormat.OPENAI_RESPONSES, LLMConfig.EndpointFormat.ANTHROPIC}) {
            LlmSettings settings = new LlmSettings("http://localhost", "", model, null, format);
            JsonObject json = JsonParser.parseString((String) build.invoke(null, settings)).getAsJsonObject();
            check("probe model controls round trip: " + format, model.trim().equals(json.get("model").getAsString()));
            boolean responses = format == LLMConfig.EndpointFormat.OPENAI_RESPONSES;
            String tokenField = responses ? "max_output_tokens" : "max_tokens";
            check("probe token limit retained: " + format, json.get(tokenField).getAsInt() == 100_000_000);
            check("probe payload shape retained: " + format, responses
                    ? "ping".equals(json.get("input").getAsString()) && !json.has("messages")
                    : "user".equals(json.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString()));
            check("probe streaming policy retained: " + format, format == LLMConfig.EndpointFormat.ANTHROPIC
                    ? !json.has("stream") : !json.get("stream").getAsBoolean());
        }
        check("blank model still skips probe", build.invoke(null,
                new LlmSettings("http://localhost", "", "", null, LLMConfig.EndpointFormat.OPENAI_CHAT)) == null);
    }

    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
