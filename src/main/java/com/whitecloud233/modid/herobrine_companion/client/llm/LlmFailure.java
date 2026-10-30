package com.whitecloud233.modid.herobrine_companion.client.llm;

public record LlmFailure(Kind kind, int statusCode, String detail) {
    public enum Kind {
        AUTHENTICATION,
        RATE_LIMIT,
        REQUEST_REJECTED,
        PROVIDER,
        INVALID_RESPONSE
    }

    public LlmFailure {
        if (kind == null) throw new IllegalArgumentException("Missing failure kind");
        detail = detail == null ? "" : detail;
    }

    public static LlmFailure http(int statusCode, String detail) {
        String body = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        boolean authentication = statusCode == 401 || statusCode == 403
                || body.contains("invalid api key") || body.contains("incorrect api key")
                || body.contains("invalid_api_key") || body.contains("api key not valid")
                || body.contains("authentication failed") || body.contains("unauthorized");
        Kind kind = authentication ? Kind.AUTHENTICATION : switch (statusCode) {
            case 429 -> Kind.RATE_LIMIT;
            default -> statusCode >= 500 ? Kind.PROVIDER : Kind.REQUEST_REJECTED;
        };
        return new LlmFailure(kind, statusCode, detail);
    }
}
