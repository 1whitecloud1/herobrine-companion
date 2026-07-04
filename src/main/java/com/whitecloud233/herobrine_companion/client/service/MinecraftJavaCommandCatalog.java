package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class MinecraftJavaCommandCatalog {
    private static final String INDEX_RESOURCE_PATH = "assets/herobrine_companion/ai/minecraft_java_command_index.json";
    private static final String CATALOG_RESOURCE_PATH = "assets/herobrine_companion/ai/minecraft_java_command_catalog.json";
    private static final int MAX_SYNTAX_LINES_PER_COMMAND = 2;
    private static final int MAX_PARAMETER_NAMES_PER_COMMAND = 5;
    private static final int MAX_REFERENCE_CHARS = 16000;

    private MinecraftJavaCommandCatalog() {}

    static String compactToolReference() {
        return Holder.REFERENCE;
    }

    private static String loadReference() {
        try {
            JsonObject catalog = loadJsonResource(INDEX_RESOURCE_PATH);
            if (catalog == null) {
                catalog = loadJsonResource(CATALOG_RESOURCE_PATH);
            }
            if (catalog == null) {
                return " Wiki Java command index resource is missing; rely on the typed schema and Java validator.";
            }
            JsonArray commands = catalog.getAsJsonArray("commands");
            if (commands == null || commands.isEmpty()) {
                return " Wiki Java command index resource is empty; rely on the typed schema and Java validator.";
            }

            StringBuilder builder = new StringBuilder(8192);
            builder.append(" Clean Java command index from zh.minecraft.wiki. ")
                    .append("Use these command forms to choose the action enum and typed fields; do not emit raw slash commands: ");
            for (JsonElement element : commands) {
                if (!element.isJsonObject()) {
                    continue;
                }
                appendCommandReference(builder, element.getAsJsonObject());
                if (builder.length() >= MAX_REFERENCE_CHARS) {
                    builder.append(" Catalog truncated; the resource JSON contains the full scraped command list.");
                    break;
                }
            }
            return builder.toString();
        } catch (Exception ignored) {
            return " Wiki Java command index could not be loaded; rely on the typed schema and Java validator.";
        }
    }

    private static JsonObject loadJsonResource(String path) {
        try (InputStream input = MinecraftJavaCommandCatalog.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendCommandReference(StringBuilder builder, JsonObject command) {
        String name = getString(command, "name");
        if (name.isEmpty()) {
            return;
        }

        builder.append(name);
        JsonArray aliases = command.getAsJsonArray("aliases");
        if (aliases != null && !aliases.isEmpty()) {
            for (JsonElement aliasElement : aliases) {
                if (aliasElement.isJsonPrimitive()) {
                    String alias = aliasElement.getAsString().trim();
                    if (!alias.isEmpty()) {
                        builder.append("/").append(alias);
                    }
                }
            }
        }
        String aliasOf = getString(command, "alias_of");
        if (!aliasOf.isEmpty()) {
            builder.append(" alias-of ").append(aliasOf);
        }
        builder.append(": ");

        JsonArray syntax = getSyntaxArray(command);
        int added = 0;
        if (syntax != null) {
            for (JsonElement syntaxElement : syntax) {
                if (!syntaxElement.isJsonPrimitive()) {
                    continue;
                }
                String line = shortenSyntaxLine(syntaxElement.getAsString());
                if (line.isEmpty()) {
                    continue;
                }
                if (added > 0) {
                    builder.append(" | ");
                }
                builder.append(line);
                added++;
                if (added >= MAX_SYNTAX_LINES_PER_COMMAND) {
                    break;
                }
            }
        }
        if (added == 0) {
            builder.append("see typed schema");
        } else if (syntax != null && syntax.size() > added) {
            builder.append(" | ...");
        }
        appendParameterNames(builder, command);

        String status = getString(command, "status").toLowerCase(Locale.ROOT);
        String runtimeNote = getString(command, "minecraft_runtime_note").toLowerCase(Locale.ROOT);
        if (status.contains("may_reject") || runtimeNote.contains("may reject")) {
            builder.append(" [wiki-current; MC 1.21.1 may reject]");
        }
        builder.append("; ");
    }

    private static JsonArray getSyntaxArray(JsonObject command) {
        JsonArray syntax = command.getAsJsonArray("syntax");
        if (syntax != null) {
            return syntax;
        }
        return command.getAsJsonArray("compact_syntax");
    }

    private static void appendParameterNames(StringBuilder builder, JsonObject command) {
        JsonArray parameters = command.getAsJsonArray("parameters");
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        int added = 0;
        for (JsonElement parameterElement : parameters) {
            if (!parameterElement.isJsonPrimitive()) {
                continue;
            }
            String parameter = parameterElement.getAsString().trim();
            if (parameter.isEmpty()) {
                continue;
            }
            if (added == 0) {
                builder.append(" params ");
            } else {
                builder.append(",");
            }
            builder.append(parameter);
            added++;
            if (added >= MAX_PARAMETER_NAMES_PER_COMMAND) {
                break;
            }
        }
        if (added > 0 && parameters.size() > added) {
            builder.append(",...");
        }
    }

    private static String getString(JsonObject object, String propertyName) {
        if (object == null || !object.has(propertyName) || object.get(propertyName).isJsonNull()) {
            return "";
        }
        return object.get(propertyName).getAsString().trim();
    }

    private static String shortenSyntaxLine(String line) {
        String value = line == null ? "" : line.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (value.length() <= 140) {
            return value;
        }
        return value.substring(0, 137) + "...";
    }

    private static final class Holder {
        private static final String REFERENCE = loadReference();
    }
}

