package com.whitecloud233.modid.herobrine_companion.client.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Local service addresses are validated without resolving arbitrary hostnames. */
public final class LocalModelEndpoint {
    private static final String CHAT_SUFFIX = "/chat/completions";

    private LocalModelEndpoint() {
    }

    public static boolean isLoopback(String endpoint) {
        try {
            URI uri = URI.create(endpoint == null ? "" : endpoint.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]")) {
                return true;
            }
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4 || !octets[0].equals("127")) return false;
            for (String octet : octets) {
                if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) return false;
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Requires the complete chat URL; no remote hosts, credentials, or query parameters. */
    public static String normalize(String endpoint) {
        if (!isLoopback(endpoint)) return "";
        try {
            URI uri = URI.create(endpoint.trim());
            String path = uri.getRawPath();
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || path == null || !path.endsWith(CHAT_SUFFIX) || path.contains("%")
                    || path.contains("/../") || path.contains("/./")
                    || !uri.normalize().getRawPath().equals(path)) {
                return "";
            }
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null,
                    uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), path, null, null).toString();
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            return "";
        }
    }

    public static String modelsEndpoint(String endpoint) {
        String normalized = normalize(endpoint);
        if (normalized.isEmpty()) return "";
        return normalized.substring(0, normalized.length() - CHAT_SUFFIX.length()) + "/models";
    }
}
