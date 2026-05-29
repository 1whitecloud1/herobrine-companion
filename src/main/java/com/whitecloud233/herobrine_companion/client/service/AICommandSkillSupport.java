package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.whitecloud233.herobrine_companion.network.HeroAIActionPacket;

import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AICommandSkillSupport {
    static final String TOOL_MANIFEST_DIVINE_POWER = "manifest_divine_power";
    static final String TOOL_MINECRAFT_COMMAND_SKILL = "minecraft_command_skill";
    private static final int MAX_SKILL_GIVE_COUNT = 64;
    private static final int MAX_SKILL_SUMMON_DISTANCE = 20;

    private AICommandSkillSupport() {}

    static boolean isSupportedToolName(String toolName) {
        return TOOL_MANIFEST_DIVINE_POWER.equals(toolName) || TOOL_MINECRAFT_COMMAND_SKILL.equals(toolName);
    }

    static JsonObject createOpenAiMinecraftCommandSkillTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", TOOL_MINECRAFT_COMMAND_SKILL);
        function.addProperty("description", "Preferred structured Minecraft 1.20.1 command skill. Use this for common world actions instead of writing raw /commands. The game will validate the action and convert parameters into a safe command/action code.");
        function.add("parameters", createMinecraftCommandSkillInputSchema());
        tool.add("function", function);
        return tool;
    }

    static JsonObject createOpenAiManifestDivinePowerTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", TOOL_MANIFEST_DIVINE_POWER);
        function.addProperty("description", buildDivineSpellbookDescription());
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        addStringProperty(properties, "command", "The raw command/action code to execute, without '/'. Use only when minecraft_command_skill cannot express the request.");
        addStringProperty(properties, "dialogue", "Your dialogue while casting this power.");
        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("command");
        required.add("dialogue");
        parameters.add("required", required);
        function.add("parameters", parameters);
        tool.add("function", function);
        return tool;
    }

    static JsonObject createAnthropicMinecraftCommandSkillTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", TOOL_MINECRAFT_COMMAND_SKILL);
        tool.addProperty("description", "Preferred structured Minecraft 1.20.1 command skill. Use this for common world actions instead of writing raw /commands. The game will validate the action and convert parameters into a safe command/action code.");
        tool.add("input_schema", createMinecraftCommandSkillInputSchema());
        return tool;
    }

    static JsonObject createAnthropicManifestDivinePowerTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", TOOL_MANIFEST_DIVINE_POWER);
        tool.addProperty("description", buildDivineSpellbookDescription());
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        addStringProperty(properties, "command", "The raw command/action code to execute, without '/'. Use only when minecraft_command_skill cannot express the request.");
        addStringProperty(properties, "dialogue", "Your dialogue while casting this power.");
        inputSchema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("command");
        required.add("dialogue");
        inputSchema.add("required", required);
        tool.add("input_schema", inputSchema);
        return tool;
    }

    static String buildMinecraftSkillCommand(JsonObject args, String actionSummonToPlayer, String actionTeleportToHero, String actionToggleCompanion, String actionMassiveLightning) {
        String action = getOptionalString(args, "action", "").trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "summon_hero_to_player" -> actionSummonToPlayer;
            case "teleport_player_to_hero" -> actionTeleportToHero;
            case "toggle_companion_follow" -> actionToggleCompanion;
            case "massive_lightning" -> actionMassiveLightning;
            case "discard_nearby_entities" -> HeroAIActionPacket.ACTION_DISCARD_ENTITIES;
            case "discard_nearby_world" -> HeroAIActionPacket.ACTION_DISCARD;
            case "accept_challenge" -> HeroAIActionPacket.ACTION_CHALLENGE_ACCEPT;
            case "hero_fly_up" -> HeroAIActionPacket.ACTION_HERO_FLY_UP;
            case "hero_land" -> HeroAIActionPacket.ACTION_HERO_LAND;
            case "kill_player" -> com.whitecloud233.herobrine_companion.network.HeroPunishmentPacket.ACTION_KILL_PLAYER;
            case "kick_player" -> com.whitecloud233.herobrine_companion.network.HeroPunishmentPacket.ACTION_KICK_PLAYER;
            case "set_time" -> buildSetTimeCommand(args);
            case "set_weather" -> buildSetWeatherCommand(args);
            case "set_gamemode" -> buildSetGamemodeCommand(args);
            case "give_item" -> buildGiveItemCommand(args);
            case "summon_entity_nearby" -> buildSummonEntityCommand(args);
            case "locate_structure" -> buildLocateCommand(args, "structure_id", "structure");
            case "locate_biome" -> buildLocateCommand(args, "biome_id", "biome");
            case "place_template" -> buildPlaceTemplateCommand(args);
            case "teleport_to_dimension" -> buildTeleportDimensionCommand(args);
            default -> null;
        };
    }

    private static JsonObject createMinecraftCommandSkillInputSchema() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        actionProp.addProperty("description", "Intent to perform. Prefer the closest enum instead of inventing command text.");
        JsonArray actions = new JsonArray();
        for (String action : List.of(
                "summon_hero_to_player", "teleport_player_to_hero", "toggle_companion_follow",
                "massive_lightning", "discard_nearby_entities", "discard_nearby_world",
                "accept_challenge", "hero_fly_up", "hero_land",
                "kill_player", "kick_player",
                "set_time", "set_weather", "set_gamemode", "give_item", "summon_entity_nearby",
                "locate_structure", "locate_biome", "place_template", "teleport_to_dimension")) {
            actions.add(action);
        }
        actionProp.add("enum", actions);
        properties.add("action", actionProp);

        addStringProperty(properties, "dialogue", "Herobrine dialogue to show after the skill succeeds.");
        addStringProperty(properties, "item_id", "For give_item. Resource id like minecraft:diamond or diamond. Count is clamped to 1-64.");
        addStringProperty(properties, "entity_id", "For summon_entity_nearby. Resource id like minecraft:zombie or zombie.");
        addStringProperty(properties, "structure_id", "For locate_structure. Resource id like minecraft:village_plains or village_plains.");
        addStringProperty(properties, "biome_id", "For locate_biome. Resource id like minecraft:cherry_grove or cherry_grove.");
        addStringProperty(properties, "template_id", "For place_template. Resource id of a configured structure template.");
        addStringProperty(properties, "dimension_id", "For teleport_to_dimension. Resource id like minecraft:the_nether or minecraft:overworld.");

        JsonObject countProp = new JsonObject();
        countProp.addProperty("type", "integer");
        countProp.addProperty("description", "For give_item. Clamped to 1-64.");
        properties.add("count", countProp);

        JsonObject distanceProp = new JsonObject();
        distanceProp.addProperty("type", "integer");
        distanceProp.addProperty("description", "For summon_entity_nearby. Forward distance, clamped to 1-20.");
        properties.add("distance", distanceProp);

        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "integer");
        yProp.addProperty("description", "For teleport_to_dimension. Destination Y level, clamped to world-like range.");
        properties.add("y", yProp);

        addEnumProperty(properties, "time", "For set_time.", "day", "noon", "night", "midnight");
        addEnumProperty(properties, "weather", "For set_weather.", "clear", "rain", "thunder");
        addEnumProperty(properties, "gamemode", "For set_gamemode on the requesting player only.", "survival", "creative", "adventure", "spectator");

        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("action");
        required.add("dialogue");
        parameters.add("required", required);
        return parameters;
    }

    private static String buildDivineSpellbookDescription() {
        return "Low-level fallback only. Alter Minecraft 1.20.1 underlying code by generating vanilla commands or action codes (NO '/' prefix). Prefer minecraft_command_skill for: teleport/follow, lightning, time/weather, gamemode, give item, summon entity, locate, template placing, dimension teleport, challenge/fly/land, entity/world discard, kill/kick. " +
                "If forced to use this fallback: [Follow/Summon/Teleport] use 'action:summon_to_player', 'action:teleport_to_hero', or 'action:toggle_companion'. " +
                "[Punishment] use 'action:massive_lightning', 'action:punishment_kill_player', or 'action:punishment_kick_player' only when explicitly justified. " +
                "[Entity Annihilation] use '" + HeroAIActionPacket.ACTION_DISCARD_ENTITIES + "' for creature-only erasure. " +
                "[World Erasure] use '" + HeroAIActionPacket.ACTION_DISCARD + "' only for explicit terrain/world deletion. " +
                "[Dialogue-to-Effect Sync] use '" + HeroAIActionPacket.ACTION_CHALLENGE_ACCEPT + "', '" + HeroAIActionPacket.ACTION_HERO_FLY_UP + "', or '" + HeroAIActionPacket.ACTION_HERO_LAND + "' when your spoken line claims that visible action happened.";
    }

    private static void addStringProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        properties.add(name, property);
    }

    private static void addEnumProperty(JsonObject properties, String name, String description, String... values) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        JsonArray enumValues = new JsonArray();
        for (String value : values) {
            enumValues.add(value);
        }
        property.add("enum", enumValues);
        properties.add(name, property);
    }

    private static String buildSetTimeCommand(JsonObject args) {
        String value = getOptionalString(args, "time", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("day", "noon", "night", "midnight").contains(value)) {
            return null;
        }
        return "time set " + value;
    }

    private static String buildSetWeatherCommand(JsonObject args) {
        String value = getOptionalString(args, "weather", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("clear", "rain", "thunder").contains(value)) {
            return null;
        }
        return "weather " + value;
    }

    private static String buildSetGamemodeCommand(JsonObject args) {
        String value = getOptionalString(args, "gamemode", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("survival", "creative", "adventure", "spectator").contains(value)) {
            return null;
        }
        return "gamemode " + value + " @s";
    }

    private static String buildGiveItemCommand(JsonObject args) {
        String itemId = normalizeResourceId(getOptionalString(args, "item_id", ""));
        if (itemId == null) {
            return null;
        }
        int count = clamp(getOptionalInt(args, "count", 1), 1, MAX_SKILL_GIVE_COUNT);
        return "give @s " + itemId + " " + count;
    }

    private static String buildSummonEntityCommand(JsonObject args) {
        String entityId = normalizeResourceId(getOptionalString(args, "entity_id", ""));
        if (entityId == null) {
            return null;
        }
        int distance = clamp(getOptionalInt(args, "distance", 5), 1, MAX_SKILL_SUMMON_DISTANCE);
        return "summon " + entityId + " ^ ^ ^" + distance;
    }

    private static String buildLocateCommand(JsonObject args, String fieldName, String locateType) {
        String id = normalizeResourceId(getOptionalString(args, fieldName, ""));
        return id == null ? null : "locate " + locateType + " " + id;
    }

    private static String buildPlaceTemplateCommand(JsonObject args) {
        String templateId = normalizeResourceId(getOptionalString(args, "template_id", ""));
        return templateId == null ? null : "place template " + templateId + " ~5 ~ ~";
    }

    private static String buildTeleportDimensionCommand(JsonObject args) {
        String dimensionId = normalizeResourceId(getOptionalString(args, "dimension_id", ""));
        if (dimensionId == null) {
            return null;
        }
        int y = clamp(getOptionalInt(args, "y", 100), -64, 320);
        return "execute in " + dimensionId + " run tp @s ~ " + y + " ~";
    }

    private static String normalizeResourceId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String value = rawId.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+") ? value : null;
    }

    private static String getOptionalString(JsonObject object, String propertyName, String fallback) {
        if (object == null || !object.has(propertyName)) {
            return fallback;
        }
        JsonElement element = object.get(propertyName);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static int getOptionalInt(JsonObject object, String propertyName, int fallback) {
        try {
            if (object == null || !object.has(propertyName) || object.get(propertyName).isJsonNull()) {
                return fallback;
            }
            return object.get(propertyName).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
