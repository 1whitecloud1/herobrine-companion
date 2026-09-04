package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolSpec;
import com.whitecloud233.herobrine_companion.network.HeroAIActionPacket;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AICommandSkillSupport {
    static final String TOOL_MANIFEST_DIVINE_POWER = "manifest_divine_power";
    static final String TOOL_MINECRAFT_COMMAND_SKILL = "minecraft_command_skill";

    /** 提供给 LLM 工具清单（provider 无关）；由 AIService/AIPromptAssembler 注入，适配器负责包装成各供应商格式。 */
    static List<LlmToolSpec> toolSpecs() {
        return List.of(
                new LlmToolSpec(TOOL_MINECRAFT_COMMAND_SKILL, buildMinecraftCommandSkillDescription(), createMinecraftCommandSkillInputSchema()),
                new LlmToolSpec(TOOL_MANIFEST_DIVINE_POWER, buildDivineSpellbookDescription(), createManifestDivinePowerInputSchema())
        );
    }

    private static JsonObject createManifestDivinePowerInputSchema() {
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
        return parameters;
    }

    private static final int MAX_SKILL_GIVE_COUNT = 64;
    private static final int MAX_SKILL_SUMMON_DISTANCE = 20;
    private static final int MAX_SELECTOR_RADIUS = 64;
    private static final int MAX_BLOCK_OFFSET = 16;
    private static final int MAX_FILL_VOLUME = 4096;
    private static final int MAX_TELEPORT_OFFSET = 1024;
    private static final int MAX_EFFECT_SECONDS = 3600;
    private static final int MAX_EFFECT_AMPLIFIER = 10;
    private static final int MAX_EXPERIENCE_AMOUNT = 10000;
    private static final int MAX_PARTICLE_COUNT = 256;
    private static final int MAX_CLEAR_COUNT = 2304;
    private static final int MAX_WORLD_BORDER_SIZE = 60000000;
    private static final int MAX_IDLE_TIMEOUT_MINUTES = 35791;
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static final List<String> ACTIONS = List.of(
            "clear", "clone", "damage", "deop", "difficulty", "effect", "enchant", "execute",
            "experience", "xp", "fill", "function", "gamemode", "gamerule", "give", "help",
            "kick", "kill", "list", "locate", "loot", "me", "op", "particle", "place",
            "playsound", "recipe", "reload", "ride", "say", "schedule", "scoreboard",
            "setblock", "setworldspawn", "spawnpoint", "spreadplayers", "stop", "stopsound",
            "summon", "tag", "msg", "tell", "w", "tellraw", "time", "title", "teleport",
            "tp", "transfer", "weather", "whitelist",
            "teleport_player_to_hero",
            "hero_summon_to_player",
            "massive_lightning", "discard_nearby_entities", "discard_nearby_world",
            "kill_player", "kick_player",
            "set_time", "set_weather", "set_difficulty", "set_gamemode", "set_gamerule",
            "default_gamemode", "seed", "set_idle_timeout",
            "give_item", "clear_inventory", "enchant_held_item", "experience",
            "advancement_grant", "advancement_revoke",
            "attribute_get", "attribute_set_base",
            "summon_entity_nearby", "teleport_to_coordinates", "teleport_to_dimension",
            "spectate_player",
            "set_spawnpoint", "set_world_spawn",
            "set_block_nearby", "fill_nearby_region", "fill_biome_nearby_region",
            "effect_give", "effect_clear",
            "particle", "play_sound",
            "locate_structure", "locate_biome", "place_template",
            "force_load_add", "force_load_remove", "force_load_query",
            "item_replace_held", "bossbar_add", "bossbar_remove", "bossbar_set_value",
            "bossbar_set_max", "bossbar_set_visible", "data_get_entity", "data_get_block",
            "data_get_storage", "data_merge_entity", "data_remove_entity",
            "datapack_list", "datapack_enable", "datapack_disable",
            "ban_player", "ban_ip", "banlist", "pardon_player", "pardon_ip",
            "debug_start", "debug_stop", "debug_function", "jfr_start", "jfr_stop",
            "perf_start", "perf_stop", "publish", "unpublish", "save_all", "save_off", "save_on",
            "return_value", "random_roll", "random_reset", "rotate_self", "swing_hand",
            "stopwatch_start", "stopwatch_stop", "stopwatch_reset", "test_run", "tick_query",
            "tick_rate", "tick_freeze", "tick_unfreeze", "tick_step", "tick_sprint",
            "team_add", "team_remove", "team_join", "team_leave",
            "team_message", "version", "waypoint_list", "dialog_clear", "fetch_profile",
            "trigger_objective", "worldborder_get", "worldborder_center", "worldborder_set",
            "worldborder_add", "worldborder_damage_buffer", "worldborder_warning_time",
            "worldborder_warning_distance"
    );

    private static final Map<String, String> GAME_RULES = Map.ofEntries(
            Map.entry("keepinventory", "keepInventory"),
            Map.entry("keep_inventory", "keepInventory"),
            Map.entry("dodaylightcycle", "doDaylightCycle"),
            Map.entry("do_daylight_cycle", "doDaylightCycle"),
            Map.entry("doweathercycle", "doWeatherCycle"),
            Map.entry("do_weather_cycle", "doWeatherCycle"),
            Map.entry("domobspawning", "doMobSpawning"),
            Map.entry("do_mob_spawning", "doMobSpawning"),
            Map.entry("mobgriefing", "mobGriefing"),
            Map.entry("mob_griefing", "mobGriefing"),
            Map.entry("dofiretick", "doFireTick"),
            Map.entry("do_fire_tick", "doFireTick"),
            Map.entry("doimmediaterespawn", "doImmediateRespawn"),
            Map.entry("do_immediate_respawn", "doImmediateRespawn")
    );

    private AICommandSkillSupport() {}

    static boolean isSupportedToolName(String toolName) {
        return TOOL_MANIFEST_DIVINE_POWER.equals(toolName) || TOOL_MINECRAFT_COMMAND_SKILL.equals(toolName);
    }

    static String buildMinecraftSkillCommand(JsonObject args, String actionTeleportToHero, String actionMassiveLightning, String actionSummonHeroToPlayer) {
        String action = getOptionalString(args, "action", "").trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "clear" -> buildClearInventoryCommand(args);
            case "clone" -> buildCloneCommand(args);
            case "damage" -> buildDamageCommand(args);
            case "deop" -> buildDeopCommand(args);
            case "difficulty" -> buildSetDifficultyCommand(args);
            case "effect" -> buildEffectCommand(args);
            case "enchant" -> buildEnchantHeldItemCommand(args);
            case "execute" -> buildExecuteCommand(args);
            case "experience", "xp" -> buildExperienceCommand(args);
            case "fill" -> buildFillNearbyRegionCommand(args);
            case "function" -> buildFunctionCommand(args);
            case "gamemode" -> buildSetGamemodeCommand(args);
            case "gamerule" -> buildSetGameruleCommand(args);
            case "give" -> buildGiveItemCommand(args);
            case "help" -> buildHelpCommand(args);
            case "kick" -> buildKickCommand(args);
            case "kill" -> buildKillCommand(args);
            case "list" -> getOptionalBoolean(args, "include_uuids", false) ? "list uuids" : "list";
            case "locate" -> buildLocateGeneralCommand(args);
            case "loot" -> buildLootCommand(args);
            case "me" -> buildMeCommand(args);
            case "op" -> buildOpCommand(args);
            case "particle" -> buildParticleCommand(args);
            case "place" -> buildPlaceCommand(args);
            case "playsound" -> buildPlaySoundCommand(args);
            case "recipe" -> buildRecipeCommand(args);
            case "reload" -> "reload";
            case "ride" -> buildRideCommand(args);
            case "say" -> buildSayCommand(args);
            case "schedule" -> buildScheduleCommand(args);
            case "scoreboard" -> buildScoreboardCommand(args);
            case "setblock" -> buildSetBlockNearbyCommand(args);
            case "setworldspawn" -> buildSetWorldSpawnCommand(args);
            case "spawnpoint" -> buildSetSpawnpointCommand(args);
            case "spreadplayers" -> buildSpreadPlayersCommand(args);
            case "stop" -> "stop";
            case "stopsound" -> buildStopSoundCommand(args);
            case "summon" -> buildSummonEntityCommand(args);
            case "tag" -> buildTagCommand(args);
            case "msg", "tell", "w" -> buildPrivateMessageCommand(args);
            case "tellraw" -> buildTellRawCommand(args);
            case "time" -> buildSetTimeCommand(args);
            case "title" -> buildTitleCommand(args);
            case "teleport", "tp" -> buildTeleportCoordinatesCommand(args);
            case "transfer" -> buildTransferCommand(args);
            case "weather" -> buildSetWeatherCommand(args);
            case "whitelist" -> buildWhitelistCommand(args);
            case "teleport_player_to_hero" -> actionTeleportToHero;
            case "hero_summon_to_player" -> actionSummonHeroToPlayer;
            case "massive_lightning" -> actionMassiveLightning;
            case "discard_nearby_entities" -> HeroAIActionPacket.ACTION_DISCARD_ENTITIES;
            case "discard_nearby_world" -> HeroAIActionPacket.ACTION_DISCARD;
            case "kill_player" -> com.whitecloud233.herobrine_companion.network.HeroPunishmentPacket.ACTION_KILL_PLAYER;
            case "kick_player" -> com.whitecloud233.herobrine_companion.network.HeroPunishmentPacket.ACTION_KICK_PLAYER;
            case "set_time" -> buildSetTimeCommand(args);
            case "set_weather" -> buildSetWeatherCommand(args);
            case "set_difficulty" -> buildSetDifficultyCommand(args);
            case "set_gamemode" -> buildSetGamemodeCommand(args);
            case "set_gamerule" -> buildSetGameruleCommand(args);
            case "default_gamemode" -> buildDefaultGamemodeCommand(args);
            case "seed" -> "seed";
            case "set_idle_timeout" -> buildSetIdleTimeoutCommand(args);
            case "give_item" -> buildGiveItemCommand(args);
            case "clear_inventory" -> buildClearInventoryCommand(args);
            case "enchant_held_item" -> buildEnchantHeldItemCommand(args);
            case "advancement_grant" -> buildAdvancementCommand(args, "grant");
            case "advancement_revoke" -> buildAdvancementCommand(args, "revoke");
            case "attribute_get" -> buildAttributeGetCommand(args);
            case "attribute_set_base" -> buildAttributeSetBaseCommand(args);
            case "summon_entity_nearby" -> buildSummonEntityCommand(args);
            case "teleport_to_coordinates" -> buildTeleportCoordinatesCommand(args);
            case "spectate_player" -> buildSpectateCommand(args);
            case "set_spawnpoint" -> buildSetSpawnpointCommand(args);
            case "set_world_spawn" -> buildSetWorldSpawnCommand(args);
            case "set_block_nearby" -> buildSetBlockNearbyCommand(args);
            case "fill_nearby_region" -> buildFillNearbyRegionCommand(args);
            case "fill_biome_nearby_region" -> buildFillBiomeNearbyRegionCommand(args);
            case "effect_give" -> buildEffectGiveCommand(args);
            case "effect_clear" -> buildEffectClearCommand(args);
            case "play_sound" -> buildPlaySoundCommand(args);
            case "locate_structure" -> buildLocateCommand(args, "structure_id", "structure");
            case "locate_biome" -> buildLocateCommand(args, "biome_id", "biome");
            case "place_template" -> buildPlaceTemplateCommand(args);
            case "force_load_add" -> buildForceLoadCommand(args, "add");
            case "force_load_remove" -> buildForceLoadCommand(args, "remove");
            case "force_load_query" -> "forceload query";
            case "item_replace_held" -> buildItemReplaceHeldCommand(args);
            case "bossbar_add" -> buildBossbarAddCommand(args);
            case "bossbar_remove" -> buildBossbarRemoveCommand(args);
            case "bossbar_set_value" -> buildBossbarSetIntCommand(args, "value");
            case "bossbar_set_max" -> buildBossbarSetIntCommand(args, "max");
            case "bossbar_set_visible" -> buildBossbarSetVisibleCommand(args);
            case "data_get_entity" -> buildDataGetEntityCommand(args);
            case "data_get_block" -> buildDataGetBlockCommand(args);
            case "data_get_storage" -> buildDataGetStorageCommand(args);
            case "data_merge_entity" -> buildDataMergeEntityCommand(args);
            case "data_remove_entity" -> buildDataRemoveEntityCommand(args);
            case "datapack_list" -> "datapack list";
            case "datapack_enable" -> buildDatapackEnableCommand(args);
            case "datapack_disable" -> buildDatapackDisableCommand(args);
            case "ban_player" -> buildBanPlayerCommand(args);
            case "ban_ip" -> buildBanIpCommand(args);
            case "banlist" -> buildBanlistCommand(args);
            case "pardon_player" -> buildPardonPlayerCommand(args);
            case "pardon_ip" -> buildPardonIpCommand(args);
            case "debug_start" -> "debug start";
            case "debug_stop" -> "debug stop";
            case "debug_function" -> buildDebugFunctionCommand(args);
            case "jfr_start" -> "jfr start";
            case "jfr_stop" -> "jfr stop";
            case "perf_start" -> "perf start";
            case "perf_stop" -> "perf stop";
            case "publish" -> buildPublishCommand(args);
            case "unpublish" -> "unpublish";
            case "save_all" -> getOptionalBoolean(args, "flush", false) ? "save-all flush" : "save-all";
            case "save_off" -> "save-off";
            case "save_on" -> "save-on";
            case "return_value" -> "return " + clamp(getOptionalInt(args, "value", 0), Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "random_roll" -> buildRandomRollCommand(args);
            case "random_reset" -> buildRandomResetCommand(args);
            case "rotate_self" -> buildRotateCommand(args);
            case "swing_hand" -> buildSwingCommand(args);
            case "stopwatch_start" -> buildStopwatchCommand(args, "start");
            case "stopwatch_stop" -> buildStopwatchCommand(args, "stop");
            case "stopwatch_reset" -> buildStopwatchCommand(args, "reset");
            case "test_run" -> buildTestCommand(args);
            case "tick_query" -> "tick query";
            case "tick_rate" -> buildTickRateCommand(args);
            case "tick_freeze" -> "tick freeze";
            case "tick_unfreeze" -> "tick unfreeze";
            case "tick_step" -> buildTickTimedCommand(args, "step");
            case "tick_sprint" -> buildTickTimedCommand(args, "sprint");
            case "team_add" -> buildTeamAddCommand(args);
            case "team_remove" -> buildTeamRemoveCommand(args);
            case "team_join" -> buildTeamJoinCommand(args);
            case "team_leave" -> buildTeamLeaveCommand(args);
            case "team_message" -> buildTeamMessageCommand(args);
            case "version" -> "version";
            case "waypoint_list" -> buildWaypointListCommand(args);
            case "dialog_clear" -> buildDialogClearCommand(args);
            case "fetch_profile" -> buildFetchProfileCommand(args);
            case "trigger_objective" -> buildTriggerCommand(args);
            case "worldborder_get" -> "worldborder get";
            case "worldborder_center" -> buildWorldBorderCenterCommand(args);
            case "worldborder_set" -> buildWorldBorderSizeCommand(args, "set");
            case "worldborder_add" -> buildWorldBorderSizeCommand(args, "add");
            case "worldborder_damage_buffer" -> buildWorldBorderDamageBufferCommand(args);
            case "worldborder_warning_time" -> buildWorldBorderWarningTimeCommand(args);
            case "worldborder_warning_distance" -> buildWorldBorderWarningDistanceCommand(args);
            case "teleport_to_dimension" -> buildTeleportDimensionCommand(args);
            default -> null;
        };
    }

    private static String buildMinecraftCommandSkillDescription() {
        return "MCP-style structured Minecraft Java Edition command catalog for Herobrine. Pick one action enum and fill parameter slots; never invent raw /commands for covered actions. "
                + "Project runtime is Minecraft Java 1.21.1, so newer wiki commands may be documented here but can still be rejected by the 1.21.1 dispatcher. "
                + "The Java side validates ids, selector presets, simple text, coordinates, and numeric ranges before generating the command. "
                + "Item/entity/block/effect/enchantment/particle/sound ids are checked against the live registries: vanilla ids (minecraft:x or bare x) and installed-mod ids (modid:x) both work; hallucinated or uninstalled mod ids are rejected. "
                + "If unsure of an exact id — especially modded content — call the read-only 'registry_lookup' tool first to resolve exact ids. "
                + "Use aliases only when the requested command name is an alias; otherwise prefer the canonical action. "
                + MinecraftJavaCommandCatalog.compactToolReference();
    }

    private static JsonObject createMinecraftCommandSkillInputSchema() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        actionProp.addProperty("description", "Intent to perform. Choose the closest enum; do not write command text.");
        JsonArray actions = new JsonArray();
        for (String action : ACTIONS) {
            actions.add(action);
        }
        actionProp.add("enum", actions);
        properties.add("action", actionProp);

        addStringProperty(properties, "dialogue", "Herobrine dialogue to show after the skill succeeds.");
        addEnumProperty(properties, "target", "Safe selector preset. Defaults to self. Entity presets require a radius and are only accepted for entity-safe actions.",
                "self", "nearest_player", "all_players", "nearby_entities", "nearby_non_player_entities", "nearest_entity", "nearby_items");
        addEnumProperty(properties, "coordinate_mode", "For teleport_to_coordinates. absolute uses x/y/z, relative uses offset_x/offset_y/offset_z, local_forward uses distance with ^ ^ ^distance.",
                "absolute", "relative", "local_forward");

        addStringProperty(properties, "item_id", "For give_item or clear_inventory. Registered item id: minecraft:diamond, diamond, or modid:item_id for installed mods (e.g. efn:yamato); current-client localized display names may be resolved.");
        addStringProperty(properties, "entity_id", "For summon_entity_nearby. Registered entity id: minecraft:zombie, zombie, or modid:entity_id for installed mods; current-client localized display names may be resolved.");
        addStringProperty(properties, "block_id", "For set_block_nearby or fill_nearby_region. Registered block id: minecraft:stone, stone, or modid:block_id for installed mods; current-client localized display names may be resolved; block states/NBT are not accepted.");
        addStringProperty(properties, "effect_id", "For effect_give/effect_clear. Registered effect id: minecraft:speed, speed, or modid:effect_id for installed mods; current-client localized display names may be resolved.");
        addStringProperty(properties, "enchantment_id", "For enchant_held_item. Registered enchantment id: minecraft:sharpness, sharpness, or modid:enchantment_id for installed mods; current-client localized display names may be resolved.");
        addStringProperty(properties, "particle_id", "For particle. Vanilla particle id like minecraft:flame; checked against the runtime registry.");
        addStringProperty(properties, "sound_id", "For play_sound. Vanilla sound id like minecraft:entity.lightning_bolt.thunder; checked against the runtime registry.");
        addStringProperty(properties, "damage_type", "For damage. Vanilla damage type id.");
        addStringProperty(properties, "structure_id", "For locate_structure. Registered structure id: minecraft:village_plains, village_plains, or modid:structure_id for installed mods; checked against the loaded world's structure registry.");
        addStringProperty(properties, "biome_id", "For locate_biome. Vanilla biome id like minecraft:cherry_grove or cherry_grove.");
        addStringProperty(properties, "poi_id", "For locate poi. Vanilla point-of-interest id.");
        addStringProperty(properties, "feature_id", "For place feature. Vanilla configured feature id.");
        addStringProperty(properties, "template_id", "For place_template. Vanilla/configured structure template id.");
        addStringProperty(properties, "dimension_id", "For teleport_to_dimension or execute in. Registered dimension id like minecraft:the_nether, the_nether, or modid:dimension_id for installed mods; checked against the connected server's dimension list.");
        addStringProperty(properties, "advancement_id", "For advancement grant/revoke. Vanilla advancement id, or omit when advancement_mode=everything.");
        addStringProperty(properties, "attribute_id", "For attribute commands. Vanilla attribute id like minecraft:generic.max_health; checked against the runtime registry.");
        addStringProperty(properties, "bossbar_id", "For bossbar commands. Resource id, preferably minecraft:herobrine_*.");
        addStringProperty(properties, "display_name", "For bossbar/team visible text. Plain text; converted to JSON text safely.");
        addStringProperty(properties, "player_name", "For ban/pardon/fetchprofile. Minecraft profile name, 1-16 chars.");
        addStringProperty(properties, "ip", "For ban-ip/pardon-ip. IPv4 address.");
        addStringProperty(properties, "reason", "For ban/ban-ip. Plain reason text.");
        addStringProperty(properties, "team_name", "For team commands. Simple team id.");
        addStringProperty(properties, "message", "For teammsg/tm.");
        addStringProperty(properties, "objective", "For trigger command. Scoreboard objective name.");
        addStringProperty(properties, "nbt_path", "For data get. Safe NBT path such as Health or Pos[0].");
        addStringProperty(properties, "nbt_value", "For data merge. Safe SNBT compound such as {Silent:1b}; avoid complex nested user text.");
        addStringProperty(properties, "storage_id", "For data get storage. Resource id of command storage.");
        addStringProperty(properties, "datapack_name", "For datapack enable/disable. Pack id/name.");
        addStringProperty(properties, "existing_datapack_name", "For datapack enable before/after another pack.");
        addStringProperty(properties, "function_id", "For debug function. Vanilla function id.");
        addStringProperty(properties, "profile_id", "For fetchprofile. Player name or UUID.");
        addStringProperty(properties, "test_id", "For test run. Test id/path passed to the command.");
        addStringProperty(properties, "waypoint_name", "For waypoint command.");
        addStringProperty(properties, "name", "For stopwatch or other named wiki commands. Simple safe identifier.");
        addStringProperty(properties, "random_sequence_id", "For random reset. Resource id of a random sequence.");
        addStringProperty(properties, "command_name", "For help. Java command name without slash.");
        addStringProperty(properties, "run_command", "For execute. Embedded Java command without slash; validated and limited.");
        addStringProperty(properties, "loot_table_id", "For loot. Vanilla loot table id.");
        addStringProperty(properties, "recipe_id", "For recipe. Vanilla recipe id, or omit when recipe_mode=everything.");
        addStringProperty(properties, "criteria", "For scoreboard objectives add. Criterion such as dummy.");
        addStringProperty(properties, "display_slot", "For scoreboard objectives setdisplay.");
        addStringProperty(properties, "score_holder", "For scoreboard players commands. Player name or *.");
        addStringProperty(properties, "score_holder2", "For scoreboard players operation source score holder.");
        addStringProperty(properties, "objective2", "For scoreboard players operation source objective.");
        addStringProperty(properties, "time_spec", "For schedule. Java time such as 20t, 10s, or 1d.");
        addStringProperty(properties, "host", "For transfer. Server host.");
        addStringProperty(properties, "json_text", "For tellraw/title JSON text component. Plain text is converted when JSON is not supplied.");
        addStringProperty(properties, "tag_name", "For tag add/remove/list. Simple tag id.");

        addIntegerProperty(properties, "count", "For give_item, clear_inventory, or particle. Clamped by action.");
        addIntegerProperty(properties, "distance", "For summon_entity_nearby or local_forward teleport. Clamped to a safe range.");
        addIntegerProperty(properties, "radius", "For nearby selector presets. Clamped to 1-64.");
        addIntegerProperty(properties, "chunk_x", "For forceload. Chunk/block-column X coordinate, relative-safe when omitted.");
        addIntegerProperty(properties, "chunk_z", "For forceload. Chunk/block-column Z coordinate, relative-safe when omitted.");
        addIntegerProperty(properties, "to_chunk_x", "For forceload second column.");
        addIntegerProperty(properties, "to_chunk_z", "For forceload second column.");
        addIntegerProperty(properties, "x", "Absolute X coordinate for coordinate_mode=absolute.");
        addIntegerProperty(properties, "y", "Absolute Y coordinate or destination Y level. Clamped to -64..320 where applicable.");
        addIntegerProperty(properties, "z", "Absolute Z coordinate for coordinate_mode=absolute.");
        addIntegerProperty(properties, "port", "For publish. Clamped to TCP port range.");
        addIntegerProperty(properties, "minutes", "For setidletimeout.");
        addIntegerProperty(properties, "seconds", "For timed admin commands such as worldborder/tick.");
        addIntegerProperty(properties, "value", "Generic integer value for bossbar/worldborder/trigger/return/random.");
        addIntegerProperty(properties, "min", "For random roll/reset minimum value.");
        addIntegerProperty(properties, "max", "For random roll/reset maximum value.");
        addIntegerProperty(properties, "yaw", "For rotate_self.");
        addIntegerProperty(properties, "pitch_degrees", "For rotate_self.");
        addIntegerProperty(properties, "size", "For worldborder size.");
        addIntegerProperty(properties, "warning_distance", "For worldborder warning distance.");
        addIntegerProperty(properties, "damage_amount", "For damage command.");
        addIntegerProperty(properties, "spread_distance", "For spreadplayers.");
        addIntegerProperty(properties, "max_range", "For spreadplayers.");
        addIntegerProperty(properties, "offset_x", "Relative X offset from command source. Used for relative teleport, setblock, spawn, particle, sound.");
        addIntegerProperty(properties, "offset_y", "Relative Y offset from command source.");
        addIntegerProperty(properties, "offset_z", "Relative Z offset from command source.");
        addIntegerProperty(properties, "from_offset_x", "For fill_nearby_region first corner relative X.");
        addIntegerProperty(properties, "from_offset_y", "For fill_nearby_region first corner relative Y.");
        addIntegerProperty(properties, "from_offset_z", "For fill_nearby_region first corner relative Z.");
        addIntegerProperty(properties, "to_offset_x", "For fill_nearby_region second corner relative X.");
        addIntegerProperty(properties, "to_offset_y", "For fill_nearby_region second corner relative Y.");
        addIntegerProperty(properties, "to_offset_z", "For fill_nearby_region second corner relative Z.");
        addIntegerProperty(properties, "duration_seconds", "For effect_give. Clamped to 1-3600 seconds.");
        addIntegerProperty(properties, "amplifier", "For effect_give. Zero-based effect amplifier, clamped to 0-10.");
        addIntegerProperty(properties, "level", "For enchant_held_item or experience set/add levels.");
        addIntegerProperty(properties, "amount", "For experience. Clamped to a safe positive range.");

        addNumberProperty(properties, "spread", "For particle delta x/y/z. Clamped to 0-16.");
        addNumberProperty(properties, "speed", "For particle speed. Clamped to 0-4.");
        addNumberProperty(properties, "volume", "For play_sound. Clamped to 0-10.");
        addNumberProperty(properties, "pitch", "For play_sound. Clamped to 0.5-2.");
        addNumberProperty(properties, "scale", "For attribute/data get scaling.");
        addNumberProperty(properties, "double_value", "Generic decimal value for attribute/worldborder.");
        addBooleanProperty(properties, "hide_particles", "For effect_give.");
        addBooleanProperty(properties, "gamerule_value", "For set_gamerule.");
        addBooleanProperty(properties, "flush", "For save_all.");
        addBooleanProperty(properties, "allow_commands", "For publish.");
        addBooleanProperty(properties, "visible", "For bossbar visible.");
        addBooleanProperty(properties, "include_uuids", "For list.");
        addBooleanProperty(properties, "respect_teams", "For spreadplayers.");

        addEnumProperty(properties, "time", "For set_time.", "day", "noon", "night", "midnight");
        addEnumProperty(properties, "weather", "For set_weather.", "clear", "rain", "thunder");
        addEnumProperty(properties, "difficulty", "For set_difficulty.", "peaceful", "easy", "normal", "hard");
        addEnumProperty(properties, "gamemode", "For set_gamemode on the requesting player only.", "survival", "creative", "adventure", "spectator");
        addEnumProperty(properties, "gamerule", "For set_gamerule. Limited to safe common boolean game rules.",
                "keepInventory", "doDaylightCycle", "doWeatherCycle", "doMobSpawning", "mobGriefing", "doFireTick", "doImmediateRespawn");
        addEnumProperty(properties, "setblock_mode", "For set_block_nearby.", "replace", "destroy", "keep");
        addEnumProperty(properties, "fill_mode", "For fill_nearby_region.", "replace", "destroy", "keep", "hollow", "outline");
        addEnumProperty(properties, "experience_mode", "For experience.", "add_points", "add_levels", "set_points", "set_levels");
        addEnumProperty(properties, "sound_source", "For play_sound.", "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice");
        addEnumProperty(properties, "advancement_mode", "For advancement.", "only", "from", "through", "until", "everything");
        addEnumProperty(properties, "banlist_type", "For banlist.", "players", "ips");
        addEnumProperty(properties, "slot", "For item replace.", "weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet", "hotbar.0", "hotbar.1", "hotbar.2", "hotbar.3", "hotbar.4", "hotbar.5", "hotbar.6", "hotbar.7", "hotbar.8");
        addEnumProperty(properties, "trigger_mode", "For trigger.", "none", "add", "set");
        addEnumProperty(properties, "hand", "For swing.", "mainhand", "offhand");
        addEnumProperty(properties, "publish_mode", "For publish gamemode.", "survival", "creative", "adventure", "spectator");
        addEnumProperty(properties, "datapack_position", "For datapack enable.", "default", "first", "last", "before", "after");
        addEnumProperty(properties, "effect_operation", "For effect.", "give", "clear");
        addEnumProperty(properties, "locate_type", "For locate.", "structure", "biome", "poi");
        addEnumProperty(properties, "place_type", "For place.", "template", "feature", "jigsaw", "structure");
        addEnumProperty(properties, "recipe_mode", "For recipe.", "give", "take");
        addEnumProperty(properties, "ride_operation", "For ride.", "mount", "dismount");
        addEnumProperty(properties, "schedule_operation", "For schedule.", "function", "clear");
        addEnumProperty(properties, "schedule_mode", "For schedule function.", "append", "replace");
        addEnumProperty(properties, "scoreboard_operation", "For scoreboard.",
                "objectives_list", "objectives_add", "objectives_remove", "objectives_setdisplay",
                "players_list", "players_get", "players_set", "players_add", "players_remove",
                "players_reset", "players_enable", "players_operation");
        addEnumProperty(properties, "scoreboard_operator", "For scoreboard players operation.", "=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><");
        addEnumProperty(properties, "title_operation", "For title.", "title", "subtitle", "actionbar", "times", "clear", "reset");
        addEnumProperty(properties, "whitelist_operation", "For whitelist.", "on", "off", "list", "add", "remove", "reload");
        addEnumProperty(properties, "loot_target", "For loot.", "give", "insert", "spawn", "replace_entity");
        addEnumProperty(properties, "loot_source", "For loot.", "loot", "kill", "mine");
        addEnumProperty(properties, "tag_operation", "For tag.", "list", "add", "remove");

        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("action");
        required.add("dialogue");
        parameters.add("required", required);
        return parameters;
    }

    private static String buildDivineSpellbookDescription() {
        return "Low-level fallback only. Alter Minecraft 1.21.1 underlying code by generating vanilla commands or action codes (NO '/' prefix). "
                + "Prefer minecraft_command_skill for: teleport/follow, lightning, time/weather/difficulty/gamemode/gamerule, give/clear/enchant/xp, summon, tp coordinates/dimensions, spawnpoint, setblock/fill, effects, particles, sounds, locate/place, entity/world discard, kill/kick. "
                + "If forced to use this fallback: [Teleport] use 'action:teleport_to_hero' or 'action:summon_hero_to_player'. "
                + "[Punishment] use 'action:massive_lightning', 'action:punishment_kill_player', or 'action:punishment_kick_player' only when explicitly justified. "
                + "[Entity Annihilation] use '" + HeroAIActionPacket.ACTION_DISCARD_ENTITIES + "' for creature-only erasure. "
                + "[World Erasure] use '" + HeroAIActionPacket.ACTION_DISCARD + "' only for explicit terrain/world deletion. ";
    }

    private static void addStringProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        properties.add(name, property);
    }

    private static void addIntegerProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("description", description);
        properties.add(name, property);
    }

    private static void addNumberProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "number");
        property.addProperty("description", description);
        properties.add(name, property);
    }

    private static void addBooleanProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "boolean");
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

    private static String buildSetDifficultyCommand(JsonObject args) {
        String value = getOptionalString(args, "difficulty", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("peaceful", "easy", "normal", "hard").contains(value)) {
            return null;
        }
        return "difficulty " + value;
    }

    private static String buildSetGamemodeCommand(JsonObject args) {
        String value = getOptionalString(args, "gamemode", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("survival", "creative", "adventure", "spectator").contains(value)) {
            return null;
        }
        return "gamemode " + value + " @s";
    }

    private static String buildSetGameruleCommand(JsonObject args) {
        String rule = normalizeGameRule(getOptionalString(args, "gamerule", ""));
        if (rule == null || !hasNonNull(args, "gamerule_value")) {
            return null;
        }
        return "gamerule " + rule + " " + getOptionalBoolean(args, "gamerule_value", false);
    }

    private static String buildDefaultGamemodeCommand(JsonObject args) {
        String value = getOptionalString(args, "gamemode", getOptionalString(args, "publish_mode", "")).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("survival", "creative", "adventure", "spectator").contains(value)) {
            return null;
        }
        return "defaultgamemode " + value;
    }

    private static String buildSetIdleTimeoutCommand(JsonObject args) {
        int minutes = clamp(getOptionalInt(args, "minutes", 0), 0, MAX_IDLE_TIMEOUT_MINUTES);
        return "setidletimeout " + minutes;
    }

    private static String buildCloneCommand(JsonObject args) {
        int x1 = clamp(getOptionalInt(args, "from_offset_x", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y1 = clamp(getOptionalInt(args, "from_offset_y", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z1 = clamp(getOptionalInt(args, "from_offset_z", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int x2 = clamp(getOptionalInt(args, "to_offset_x", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y2 = clamp(getOptionalInt(args, "to_offset_y", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z2 = clamp(getOptionalInt(args, "to_offset_z", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        if (volume(x1, y1, z1, x2, y2, z2) > MAX_FILL_VOLUME) {
            return null;
        }
        String destination = buildRelativePosition(args, MAX_BLOCK_OFFSET);
        return "clone " + formatRelativePosition(x1, y1, z1) + " " + formatRelativePosition(x2, y2, z2) + " " + destination;
    }

    private static String buildDamageCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        if (target == null) {
            return null;
        }
        int amount = clamp(getOptionalInt(args, "damage_amount", getOptionalInt(args, "amount", 1)), 1, 1000000);
        String damageType = hasText(args, "damage_type") ? normalizeResourceId(getOptionalString(args, "damage_type", "")) : null;
        if (hasText(args, "damage_type") && damageType == null) {
            return null;
        }
        return damageType == null ? "damage " + target + " " + amount : "damage " + target + " " + amount + " " + damageType;
    }

    private static String buildDeopCommand(JsonObject args) {
        String player = normalizePlayerName(getOptionalString(args, "player_name", ""));
        return player == null ? null : "deop " + player;
    }

    private static String buildEffectCommand(JsonObject args) {
        String operation = getOptionalString(args, "effect_operation", hasText(args, "effect_id") ? "give" : "clear").trim().toLowerCase(Locale.ROOT);
        return switch (operation) {
            case "give" -> buildEffectGiveCommand(args);
            case "clear" -> buildEffectClearCommand(args);
            default -> null;
        };
    }

    private static String buildExecuteCommand(JsonObject args) {
        String runCommand = normalizeEmbeddedRunCommand(getOptionalString(args, "run_command", ""));
        if (runCommand == null) {
            return null;
        }
        StringBuilder command = new StringBuilder("execute");
        if (hasText(args, "dimension_id")) {
            String dimensionId = MinecraftJavaIdResolver.dimensionId(getOptionalString(args, "dimension_id", ""));
            if (dimensionId == null) {
                return null;
            }
            command.append(" in ").append(dimensionId);
        }
        if (hasNonNull(args, "target")) {
            String atTarget = buildTargetSelector(args, true, true);
            if (atTarget == null) {
                return null;
            }
            command.append(" at ").append(atTarget);
        }
        if (hasNonNull(args, "offset_x") || hasNonNull(args, "offset_y") || hasNonNull(args, "offset_z")) {
            command.append(" positioned ").append(buildRelativePosition(args, MAX_BLOCK_OFFSET));
        }
        command.append(" run ").append(runCommand);
        return command.toString();
    }

    private static String buildFunctionCommand(JsonObject args) {
        String functionId = normalizeResourceId(getOptionalString(args, "function_id", ""));
        return functionId == null ? null : "function " + functionId;
    }

    private static String buildHelpCommand(JsonObject args) {
        if (!hasText(args, "command_name")) {
            return "help";
        }
        String commandName = normalizeSimpleName(getOptionalString(args, "command_name", ""), 32);
        return commandName == null ? null : "help " + commandName;
    }

    private static String buildKickCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        if (target == null) {
            return null;
        }
        return "kick " + target + optionalReason(args);
    }

    private static String buildKillCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, true);
        return target == null ? null : "kill " + target;
    }

    private static String buildAdvancementCommand(JsonObject args, String operation) {
        String target = buildTargetSelector(args, false, true);
        String mode = getOptionalString(args, "advancement_mode", "only").trim().toLowerCase(Locale.ROOT);
        if (target == null || !Set.of("only", "from", "through", "until", "everything").contains(mode)) {
            return null;
        }
        if ("everything".equals(mode)) {
            return "advancement " + operation + " " + target + " everything";
        }
        String advancementId = normalizeResourceId(getOptionalString(args, "advancement_id", ""));
        return advancementId == null ? null : "advancement " + operation + " " + target + " " + mode + " " + advancementId;
    }

    private static String buildAttributeGetCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        String attributeId = MinecraftJavaIdResolver.attributeId(getOptionalString(args, "attribute_id", ""));
        if (target == null || attributeId == null) {
            return null;
        }
        if (hasNonNull(args, "scale")) {
            return "attribute " + target + " " + attributeId + " get " + formatDouble(clamp(getOptionalDouble(args, "scale", 1.0D), -1000000.0D, 1000000.0D));
        }
        return "attribute " + target + " " + attributeId + " get";
    }

    private static String buildAttributeSetBaseCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        String attributeId = MinecraftJavaIdResolver.attributeId(getOptionalString(args, "attribute_id", ""));
        if (target == null || attributeId == null) {
            return null;
        }
        double value = clamp(getOptionalDouble(args, "double_value", getOptionalDouble(args, "value", 1.0D)), -1000000.0D, 1000000.0D);
        return "attribute " + target + " " + attributeId + " base set " + formatDouble(value);
    }

    private static String buildGiveItemCommand(JsonObject args) {
        String itemId = MinecraftJavaIdResolver.itemId(getOptionalString(args, "item_id", ""));
        if (itemId == null) {
            return null;
        }
        int count = clamp(getOptionalInt(args, "count", 1), 1, MAX_SKILL_GIVE_COUNT);
        return "give @s " + itemId + " " + count;
    }

    private static String buildClearInventoryCommand(JsonObject args) {
        String itemId = hasText(args, "item_id") ? MinecraftJavaIdResolver.itemId(getOptionalString(args, "item_id", "")) : null;
        if (hasText(args, "item_id") && itemId == null) {
            return null;
        }
        if (itemId == null) {
            return "clear @s";
        }
        int count = clamp(getOptionalInt(args, "count", 1), 1, MAX_CLEAR_COUNT);
        return "clear @s " + itemId + " " + count;
    }

    private static String buildEnchantHeldItemCommand(JsonObject args) {
        String enchantmentId = MinecraftJavaIdResolver.enchantmentId(getOptionalString(args, "enchantment_id", ""));
        if (enchantmentId == null) {
            return null;
        }
        int level = clamp(getOptionalInt(args, "level", 1), 1, 255);
        return "enchant @s " + enchantmentId + " " + level;
    }

    private static String buildExperienceCommand(JsonObject args) {
        String mode = getOptionalString(args, "experience_mode", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("add_points", "add_levels", "set_points", "set_levels").contains(mode)) {
            return null;
        }
        int amount = clamp(getOptionalInt(args, "amount", getOptionalInt(args, "level", 1)), 0, MAX_EXPERIENCE_AMOUNT);
        String operation = mode.startsWith("set_") ? "set" : "add";
        String unit = mode.endsWith("_levels") ? "levels" : "points";
        return "experience " + operation + " @s " + amount + " " + unit;
    }

    private static String buildSummonEntityCommand(JsonObject args) {
        String entityId = MinecraftJavaIdResolver.entityTypeId(getOptionalString(args, "entity_id", ""));
        if (entityId == null) {
            return null;
        }
        int distance = clamp(getOptionalInt(args, "distance", 5), 1, MAX_SKILL_SUMMON_DISTANCE);
        return "summon " + entityId + " ^ ^ ^" + distance;
    }

    private static String buildTeleportCoordinatesCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        String position = buildPosition(args);
        return target == null || position == null ? null : "tp " + target + " " + position;
    }

    private static String buildSpectateCommand(JsonObject args) {
        if (!hasNonNull(args, "target")) {
            return "spectate";
        }
        String target = buildTargetSelector(args, true, false);
        return target == null ? null : "spectate " + target + " @s";
    }

    private static String buildSetSpawnpointCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        if (target == null) {
            return null;
        }
        String pos = buildRelativePosition(args, MAX_TELEPORT_OFFSET);
        return "spawnpoint " + target + " " + pos;
    }

    private static String buildSetWorldSpawnCommand(JsonObject args) {
        return "setworldspawn " + buildRelativePosition(args, MAX_TELEPORT_OFFSET);
    }

    private static String buildSetBlockNearbyCommand(JsonObject args) {
        String blockId = MinecraftJavaIdResolver.blockId(getOptionalString(args, "block_id", ""));
        if (blockId == null) {
            return null;
        }
        String mode = getOptionalString(args, "setblock_mode", "replace").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("replace", "destroy", "keep").contains(mode)) {
            return null;
        }
        String pos = buildRelativePosition(args, MAX_BLOCK_OFFSET);
        return "setblock " + pos + " " + blockId + " " + mode;
    }

    private static String buildFillNearbyRegionCommand(JsonObject args) {
        String blockId = MinecraftJavaIdResolver.blockId(getOptionalString(args, "block_id", ""));
        if (blockId == null) {
            return null;
        }
        String mode = getOptionalString(args, "fill_mode", "replace").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("replace", "destroy", "keep", "hollow", "outline").contains(mode)) {
            return null;
        }

        int x1 = clamp(getOptionalInt(args, "from_offset_x", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y1 = clamp(getOptionalInt(args, "from_offset_y", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z1 = clamp(getOptionalInt(args, "from_offset_z", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int x2 = clamp(getOptionalInt(args, "to_offset_x", getOptionalInt(args, "offset_x", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y2 = clamp(getOptionalInt(args, "to_offset_y", getOptionalInt(args, "offset_y", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z2 = clamp(getOptionalInt(args, "to_offset_z", getOptionalInt(args, "offset_z", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        if (volume(x1, y1, z1, x2, y2, z2) > MAX_FILL_VOLUME) {
            return null;
        }
        return "fill " + formatRelativePosition(x1, y1, z1) + " " + formatRelativePosition(x2, y2, z2) + " " + blockId + " " + mode;
    }

    private static String buildFillBiomeNearbyRegionCommand(JsonObject args) {
        String biomeId = normalizeResourceId(getOptionalString(args, "biome_id", ""));
        if (biomeId == null) {
            return null;
        }
        int x1 = clamp(getOptionalInt(args, "from_offset_x", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y1 = clamp(getOptionalInt(args, "from_offset_y", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z1 = clamp(getOptionalInt(args, "from_offset_z", 0), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int x2 = clamp(getOptionalInt(args, "to_offset_x", getOptionalInt(args, "offset_x", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int y2 = clamp(getOptionalInt(args, "to_offset_y", getOptionalInt(args, "offset_y", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        int z2 = clamp(getOptionalInt(args, "to_offset_z", getOptionalInt(args, "offset_z", 0)), -MAX_BLOCK_OFFSET, MAX_BLOCK_OFFSET);
        if (volume(x1, y1, z1, x2, y2, z2) > MAX_FILL_VOLUME) {
            return null;
        }
        return "fillbiome " + formatRelativePosition(x1, y1, z1) + " " + formatRelativePosition(x2, y2, z2) + " " + biomeId;
    }

    private static String buildEffectGiveCommand(JsonObject args) {
        String effectId = MinecraftJavaIdResolver.effectId(getOptionalString(args, "effect_id", ""));
        String target = buildTargetSelector(args, true, true);
        if (effectId == null || target == null) {
            return null;
        }
        int seconds = clamp(getOptionalInt(args, "duration_seconds", 30), 1, MAX_EFFECT_SECONDS);
        int amplifier = clamp(getOptionalInt(args, "amplifier", 0), 0, MAX_EFFECT_AMPLIFIER);
        boolean hideParticles = getOptionalBoolean(args, "hide_particles", false);
        return "effect give " + target + " " + effectId + " " + seconds + " " + amplifier + " " + hideParticles;
    }

    private static String buildEffectClearCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, true);
        if (target == null) {
            return null;
        }
        if (!hasText(args, "effect_id")) {
            return "effect clear " + target;
        }
        String effectId = MinecraftJavaIdResolver.effectId(getOptionalString(args, "effect_id", ""));
        return effectId == null ? null : "effect clear " + target + " " + effectId;
    }

    private static String buildParticleCommand(JsonObject args) {
        String particleId = MinecraftJavaIdResolver.particleId(getOptionalString(args, "particle_id", ""));
        if (particleId == null) {
            return null;
        }
        String pos = buildRelativePosition(args, MAX_BLOCK_OFFSET);
        double spread = clamp(getOptionalDouble(args, "spread", 0.2D), 0.0D, 16.0D);
        double speed = clamp(getOptionalDouble(args, "speed", 0.0D), 0.0D, 4.0D);
        int count = clamp(getOptionalInt(args, "count", 20), 1, MAX_PARTICLE_COUNT);
        return "particle " + particleId + " " + pos + " " + formatDouble(spread) + " " + formatDouble(spread) + " " + formatDouble(spread)
                + " " + formatDouble(speed) + " " + count + " normal @a";
    }

    private static String buildPlaySoundCommand(JsonObject args) {
        String soundId = MinecraftJavaIdResolver.soundId(getOptionalString(args, "sound_id", ""));
        if (soundId == null) {
            return null;
        }
        String source = getOptionalString(args, "sound_source", "master").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice").contains(source)) {
            return null;
        }
        String target = buildTargetSelector(args, false, true);
        if (target == null) {
            return null;
        }
        String pos = buildRelativePosition(args, MAX_BLOCK_OFFSET);
        double volume = clamp(getOptionalDouble(args, "volume", 1.0D), 0.0D, 10.0D);
        double pitch = clamp(getOptionalDouble(args, "pitch", 1.0D), 0.5D, 2.0D);
        return "playsound " + soundId + " " + source + " " + target + " " + pos + " " + formatDouble(volume) + " " + formatDouble(pitch);
    }

    private static String buildLocateCommand(JsonObject args, String fieldName, String locateType) {
        String id = "structure".equals(locateType)
                ? MinecraftJavaIdResolver.structureId(getOptionalString(args, fieldName, ""))
                : normalizeResourceId(getOptionalString(args, fieldName, ""));
        return id == null ? null : "locate " + locateType + " " + id;
    }

    private static String buildLocateGeneralCommand(JsonObject args) {
        String type = getOptionalString(args, "locate_type", "structure").trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "structure" -> buildLocateCommand(args, "structure_id", "structure");
            case "biome" -> buildLocateCommand(args, "biome_id", "biome");
            case "poi" -> buildLocateCommand(args, "poi_id", "poi");
            default -> null;
        };
    }

    private static String buildLootCommand(JsonObject args) {
        String lootTarget = getOptionalString(args, "loot_target", "give").trim().toLowerCase(Locale.ROOT);
        String lootSource = getOptionalString(args, "loot_source", "loot").trim().toLowerCase(Locale.ROOT);
        String sourceClause;
        if ("loot".equals(lootSource)) {
            String table = normalizeResourceId(getOptionalString(args, "loot_table_id", ""));
            if (table == null) {
                return null;
            }
            sourceClause = "loot " + table;
        } else if ("kill".equals(lootSource)) {
            String target = buildTargetSelector(args, true, false);
            if (target == null) {
                return null;
            }
            sourceClause = "kill " + target;
        } else if ("mine".equals(lootSource)) {
            sourceClause = "mine " + buildRelativePosition(args, MAX_BLOCK_OFFSET);
        } else {
            return null;
        }

        return switch (lootTarget) {
            case "give" -> "loot give @s " + sourceClause;
            case "insert" -> "loot insert " + buildRelativePosition(args, MAX_BLOCK_OFFSET) + " " + sourceClause;
            case "spawn" -> "loot spawn " + buildRelativePosition(args, MAX_BLOCK_OFFSET) + " " + sourceClause;
            case "replace_entity" -> {
                String slot = getOptionalString(args, "slot", "weapon.mainhand").trim().toLowerCase(Locale.ROOT);
                if (!isSafeSlot(slot)) {
                    yield null;
                }
                yield "loot replace entity @s " + slot + " " + sourceClause;
            }
            default -> null;
        };
    }

    private static String buildMeCommand(JsonObject args) {
        String message = normalizePlainText(getOptionalString(args, "message", ""), 160);
        return message == null ? null : "me " + message;
    }

    private static String buildOpCommand(JsonObject args) {
        String player = normalizePlayerName(getOptionalString(args, "player_name", ""));
        return player == null ? null : "op " + player;
    }

    private static String buildPlaceTemplateCommand(JsonObject args) {
        String templateId = normalizeResourceId(getOptionalString(args, "template_id", ""));
        return templateId == null ? null : "place template " + templateId + " ~5 ~ ~";
    }

    private static String buildPlaceCommand(JsonObject args) {
        String type = getOptionalString(args, "place_type", "template").trim().toLowerCase(Locale.ROOT);
        String pos = buildRelativePosition(args, MAX_BLOCK_OFFSET);
        return switch (type) {
            case "template" -> {
                String id = normalizeResourceId(getOptionalString(args, "template_id", ""));
                yield id == null ? null : "place template " + id + " " + pos;
            }
            case "feature" -> {
                String id = normalizeResourceId(getOptionalString(args, "feature_id", getOptionalString(args, "structure_id", "")));
                yield id == null ? null : "place feature " + id + " " + pos;
            }
            case "structure" -> {
                String id = MinecraftJavaIdResolver.structureId(getOptionalString(args, "structure_id", ""));
                yield id == null ? null : "place structure " + id + " " + pos;
            }
            case "jigsaw" -> {
                String id = normalizeResourceId(getOptionalString(args, "template_id", ""));
                int depth = clamp(getOptionalInt(args, "value", 7), 1, 20);
                yield id == null ? null : "place jigsaw " + id + " minecraft:empty " + depth + " " + pos;
            }
            default -> null;
        };
    }

    private static String buildRecipeCommand(JsonObject args) {
        String mode = getOptionalString(args, "recipe_mode", "give").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("give", "take").contains(mode)) {
            return null;
        }
        if (!hasText(args, "recipe_id")) {
            return "recipe " + mode + " @s *";
        }
        String recipeId = normalizeResourceId(getOptionalString(args, "recipe_id", ""));
        return recipeId == null ? null : "recipe " + mode + " @s " + recipeId;
    }

    private static String buildRideCommand(JsonObject args) {
        String operation = getOptionalString(args, "ride_operation", "mount").trim().toLowerCase(Locale.ROOT);
        String target = buildTargetSelector(args, true, false);
        if (target == null) {
            return null;
        }
        if ("dismount".equals(operation)) {
            return "ride " + target + " dismount";
        }
        if (!"mount".equals(operation)) {
            return null;
        }
        String entityId = hasText(args, "entity_id") ? MinecraftJavaIdResolver.entityTypeId(getOptionalString(args, "entity_id", "")) : null;
        if (hasText(args, "entity_id") && entityId == null) {
            return null;
        }
        String vehicle = entityId == null ? "@e[limit=1,sort=nearest]" : "@e[type=" + entityId + ",limit=1,sort=nearest]";
        return "ride " + target + " mount " + vehicle;
    }

    private static String buildSayCommand(JsonObject args) {
        String message = normalizePlainText(getOptionalString(args, "message", ""), 160);
        return message == null ? null : "say " + message;
    }

    private static String buildScheduleCommand(JsonObject args) {
        String operation = getOptionalString(args, "schedule_operation", "function").trim().toLowerCase(Locale.ROOT);
        String functionId = normalizeResourceId(getOptionalString(args, "function_id", ""));
        if (functionId == null) {
            return null;
        }
        if ("clear".equals(operation)) {
            return "schedule clear " + functionId;
        }
        if (!"function".equals(operation)) {
            return null;
        }
        String time = normalizeTimeSpec(getOptionalString(args, "time_spec", "20t"));
        String mode = getOptionalString(args, "schedule_mode", "replace").trim().toLowerCase(Locale.ROOT);
        if (time == null || !Set.of("append", "replace").contains(mode)) {
            return null;
        }
        return "schedule function " + functionId + " " + time + " " + mode;
    }

    private static String buildScoreboardCommand(JsonObject args) {
        String operation = getOptionalString(args, "scoreboard_operation", "").trim().toLowerCase(Locale.ROOT);
        return switch (operation) {
            case "objectives_list" -> "scoreboard objectives list";
            case "objectives_add" -> {
                String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
                String criteria = normalizeCommandPath(getOptionalString(args, "criteria", "dummy"));
                if (objective == null || criteria == null) {
                    yield null;
                }
                String display = hasText(args, "display_name") ? " " + quoteJsonText(getOptionalString(args, "display_name", "")) : "";
                yield "scoreboard objectives add " + objective + " " + criteria + display;
            }
            case "objectives_remove" -> {
                String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
                yield objective == null ? null : "scoreboard objectives remove " + objective;
            }
            case "objectives_setdisplay" -> {
                String slot = normalizeDisplaySlot(getOptionalString(args, "display_slot", "sidebar"));
                String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
                yield slot == null || objective == null ? null : "scoreboard objectives setdisplay " + slot + " " + objective;
            }
            case "players_list" -> {
                if (!hasText(args, "score_holder")) {
                    yield "scoreboard players list";
                }
                String holder = normalizeScoreHolder(getOptionalString(args, "score_holder", ""));
                yield holder == null ? null : "scoreboard players list " + holder;
            }
            case "players_get" -> buildScoreboardPlayersValueCommand(args, "get");
            case "players_set" -> buildScoreboardPlayersValueCommand(args, "set");
            case "players_add" -> buildScoreboardPlayersValueCommand(args, "add");
            case "players_remove" -> buildScoreboardPlayersValueCommand(args, "remove");
            case "players_reset" -> buildScoreboardPlayersResetCommand(args);
            case "players_enable" -> {
                String holder = normalizeScoreHolder(getOptionalString(args, "score_holder", "@s"));
                String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
                yield holder == null || objective == null ? null : "scoreboard players enable " + holder + " " + objective;
            }
            case "players_operation" -> buildScoreboardPlayersOperationCommand(args);
            default -> null;
        };
    }

    private static String buildScoreboardPlayersValueCommand(JsonObject args, String operation) {
        String holder = normalizeScoreHolder(getOptionalString(args, "score_holder", "@s"));
        String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
        if (holder == null || objective == null) {
            return null;
        }
        if ("get".equals(operation)) {
            return "scoreboard players get " + holder + " " + objective;
        }
        int value = clamp(getOptionalInt(args, "value", 0), -1000000, 1000000);
        return "scoreboard players " + operation + " " + holder + " " + objective + " " + value;
    }

    private static String buildScoreboardPlayersResetCommand(JsonObject args) {
        String holder = normalizeScoreHolder(getOptionalString(args, "score_holder", "@s"));
        if (holder == null) {
            return null;
        }
        if (!hasText(args, "objective")) {
            return "scoreboard players reset " + holder;
        }
        String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
        return objective == null ? null : "scoreboard players reset " + holder + " " + objective;
    }

    private static String buildScoreboardPlayersOperationCommand(JsonObject args) {
        String holder = normalizeScoreHolder(getOptionalString(args, "score_holder", "@s"));
        String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
        String operator = getOptionalString(args, "scoreboard_operator", "=").trim();
        String holder2 = normalizeScoreHolder(getOptionalString(args, "score_holder2", "@s"));
        String objective2 = normalizeSimpleName(getOptionalString(args, "objective2", ""), 32);
        if (holder == null || objective == null || holder2 == null || objective2 == null
                || !Set.of("=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><").contains(operator)) {
            return null;
        }
        return "scoreboard players operation " + holder + " " + objective + " " + operator + " " + holder2 + " " + objective2;
    }

    private static String buildSpreadPlayersCommand(JsonObject args) {
        int x = clamp(getOptionalInt(args, "x", getOptionalInt(args, "offset_x", 0)), -30000000, 30000000);
        int z = clamp(getOptionalInt(args, "z", getOptionalInt(args, "offset_z", 0)), -30000000, 30000000);
        int spreadDistance = clamp(getOptionalInt(args, "spread_distance", 4), 1, 10000);
        int maxRange = clamp(getOptionalInt(args, "max_range", 32), spreadDistance, 1000000);
        boolean respectTeams = getOptionalBoolean(args, "respect_teams", false);
        String target = buildTargetSelector(args, true, true);
        return target == null ? null : "spreadplayers " + x + " " + z + " " + spreadDistance + " " + maxRange + " " + respectTeams + " " + target;
    }

    private static String buildStopSoundCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        if (target == null) {
            return null;
        }
        String source = getOptionalString(args, "sound_source", "").trim().toLowerCase(Locale.ROOT);
        String sound = hasText(args, "sound_id") ? MinecraftJavaIdResolver.soundId(getOptionalString(args, "sound_id", "")) : null;
        if (hasText(args, "sound_id") && sound == null) {
            return null;
        }
        String command = "stopsound " + target;
        if (!source.isEmpty()) {
            if (!Set.of("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice").contains(source)) {
                return null;
            }
            command += " " + source;
            if (sound != null) {
                command += " " + sound;
            }
        }
        return command;
    }

    private static String buildTagCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, true);
        if (target == null) {
            return null;
        }
        String operation = getOptionalString(args, "tag_operation", "list").trim().toLowerCase(Locale.ROOT);
        if ("list".equals(operation)) {
            return "tag " + target + " list";
        }
        if (!Set.of("add", "remove").contains(operation)) {
            return null;
        }
        String tag = normalizeSimpleName(getOptionalString(args, "tag_name", ""), 64);
        return tag == null ? null : "tag " + target + " " + operation + " " + tag;
    }

    private static String buildPrivateMessageCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        String message = normalizePlainText(getOptionalString(args, "message", ""), 240);
        return target == null || message == null ? null : "tell " + target + " " + message;
    }

    private static String buildTellRawCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        String json = normalizeJsonText(getOptionalString(args, "json_text", ""));
        if (json == null && hasText(args, "message")) {
            json = quoteJsonText(getOptionalString(args, "message", ""));
        }
        return target == null || json == null ? null : "tellraw " + target + " " + json;
    }

    private static String buildTitleCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        String operation = getOptionalString(args, "title_operation", "title").trim().toLowerCase(Locale.ROOT);
        if (target == null || !Set.of("title", "subtitle", "actionbar", "times", "clear", "reset").contains(operation)) {
            return null;
        }
        if ("clear".equals(operation) || "reset".equals(operation)) {
            return "title " + target + " " + operation;
        }
        if ("times".equals(operation)) {
            int fadeIn = clamp(getOptionalInt(args, "from_offset_x", 10), 0, 72000);
            int stay = clamp(getOptionalInt(args, "value", 70), 0, 72000);
            int fadeOut = clamp(getOptionalInt(args, "to_offset_x", 20), 0, 72000);
            return "title " + target + " times " + fadeIn + " " + stay + " " + fadeOut;
        }
        String json = normalizeJsonText(getOptionalString(args, "json_text", ""));
        if (json == null && hasText(args, "message")) {
            json = quoteJsonText(getOptionalString(args, "message", ""));
        }
        return json == null ? null : "title " + target + " " + operation + " " + json;
    }

    private static String buildTransferCommand(JsonObject args) {
        String host = normalizeHost(getOptionalString(args, "host", ""));
        if (host == null) {
            return null;
        }
        int port = clamp(getOptionalInt(args, "port", 25565), 1, 65535);
        String target = buildTargetSelector(args, false, true);
        return target == null ? "transfer " + host + " " + port : "transfer " + host + " " + port + " " + target;
    }

    private static String buildWhitelistCommand(JsonObject args) {
        String operation = getOptionalString(args, "whitelist_operation", "list").trim().toLowerCase(Locale.ROOT);
        if (Set.of("on", "off", "list", "reload").contains(operation)) {
            return "whitelist " + operation;
        }
        if (!Set.of("add", "remove").contains(operation)) {
            return null;
        }
        String player = normalizePlayerName(getOptionalString(args, "player_name", ""));
        return player == null ? null : "whitelist " + operation + " " + player;
    }

    private static String buildTeleportDimensionCommand(JsonObject args) {
        String dimensionId = MinecraftJavaIdResolver.dimensionId(getOptionalString(args, "dimension_id", ""));
        if (dimensionId == null) {
            return null;
        }
        int y = clamp(getOptionalInt(args, "y", 100), -64, 320);
        return "execute in " + dimensionId + " run tp @s ~ " + y + " ~";
    }

    private static String buildForceLoadCommand(JsonObject args, String operation) {
        int x1 = clamp(getOptionalInt(args, "chunk_x", getOptionalInt(args, "offset_x", 0)), -30000000, 30000000);
        int z1 = clamp(getOptionalInt(args, "chunk_z", getOptionalInt(args, "offset_z", 0)), -30000000, 30000000);
        if (hasNonNull(args, "to_chunk_x") || hasNonNull(args, "to_chunk_z")) {
            int x2 = clamp(getOptionalInt(args, "to_chunk_x", x1), -30000000, 30000000);
            int z2 = clamp(getOptionalInt(args, "to_chunk_z", z1), -30000000, 30000000);
            return "forceload " + operation + " " + x1 + " " + z1 + " " + x2 + " " + z2;
        }
        return "forceload " + operation + " " + x1 + " " + z1;
    }

    private static String buildItemReplaceHeldCommand(JsonObject args) {
        String itemId = MinecraftJavaIdResolver.itemId(getOptionalString(args, "item_id", ""));
        String slot = getOptionalString(args, "slot", "weapon.mainhand").trim().toLowerCase(Locale.ROOT);
        if (itemId == null || !isSafeSlot(slot)) {
            return null;
        }
        String target = buildTargetSelector(args, true, true);
        if (target == null) {
            return null;
        }
        int count = clamp(getOptionalInt(args, "count", 1), 1, MAX_SKILL_GIVE_COUNT);
        return "item replace entity " + target + " " + slot + " with " + itemId + " " + count;
    }

    private static String buildBossbarAddCommand(JsonObject args) {
        String id = normalizeResourceId(getOptionalString(args, "bossbar_id", ""));
        String name = quoteJsonText(getOptionalString(args, "display_name", "Herobrine"));
        return id == null ? null : "bossbar add " + id + " " + name;
    }

    private static String buildBossbarRemoveCommand(JsonObject args) {
        String id = normalizeResourceId(getOptionalString(args, "bossbar_id", ""));
        return id == null ? null : "bossbar remove " + id;
    }

    private static String buildBossbarSetIntCommand(JsonObject args, String field) {
        String id = normalizeResourceId(getOptionalString(args, "bossbar_id", ""));
        if (id == null) {
            return null;
        }
        int value = clamp(getOptionalInt(args, "value", 0), 0, 1000000);
        return "bossbar set " + id + " " + field + " " + value;
    }

    private static String buildBossbarSetVisibleCommand(JsonObject args) {
        String id = normalizeResourceId(getOptionalString(args, "bossbar_id", ""));
        if (id == null || !hasNonNull(args, "visible")) {
            return null;
        }
        return "bossbar set " + id + " visible " + getOptionalBoolean(args, "visible", true);
    }

    private static String buildDataGetEntityCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        if (target == null) {
            return null;
        }
        String command = "data get entity " + target;
        if (hasText(args, "nbt_path")) {
            String path = normalizeNbtPath(getOptionalString(args, "nbt_path", ""));
            if (path == null) {
                return null;
            }
            command += " " + path;
            if (hasNonNull(args, "scale")) {
                command += " " + formatDouble(clamp(getOptionalDouble(args, "scale", 1.0D), -1000000.0D, 1000000.0D));
            }
        }
        return command;
    }

    private static String buildDataGetBlockCommand(JsonObject args) {
        String command = "data get block " + buildRelativePosition(args, MAX_BLOCK_OFFSET);
        return appendDataPathAndScale(command, args);
    }

    private static String buildDataGetStorageCommand(JsonObject args) {
        String storageId = normalizeResourceId(getOptionalString(args, "storage_id", ""));
        if (storageId == null) {
            return null;
        }
        return appendDataPathAndScale("data get storage " + storageId, args);
    }

    private static String appendDataPathAndScale(String command, JsonObject args) {
        if (hasText(args, "nbt_path")) {
            String path = normalizeNbtPath(getOptionalString(args, "nbt_path", ""));
            if (path == null) {
                return null;
            }
            command += " " + path;
            if (hasNonNull(args, "scale")) {
                command += " " + formatDouble(clamp(getOptionalDouble(args, "scale", 1.0D), -1000000.0D, 1000000.0D));
            }
        }
        return command;
    }

    private static String buildDataMergeEntityCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        String nbt = normalizeNbtValue(getOptionalString(args, "nbt_value", ""));
        return target == null || nbt == null ? null : "data merge entity " + target + " " + nbt;
    }

    private static String buildDataRemoveEntityCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        String path = normalizeNbtPath(getOptionalString(args, "nbt_path", ""));
        return target == null || path == null ? null : "data remove entity " + target + " " + path;
    }

    private static String buildDatapackEnableCommand(JsonObject args) {
        String name = normalizeDatapackName(getOptionalString(args, "datapack_name", ""));
        if (name == null) {
            return null;
        }
        String position = getOptionalString(args, "datapack_position", "default").trim().toLowerCase(Locale.ROOT);
        if ("default".equals(position)) {
            return "datapack enable " + name;
        }
        if (Set.of("first", "last").contains(position)) {
            return "datapack enable " + name + " " + position;
        }
        if (Set.of("before", "after").contains(position)) {
            String existing = normalizeDatapackName(getOptionalString(args, "existing_datapack_name", ""));
            return existing == null ? null : "datapack enable " + name + " " + position + " " + existing;
        }
        return null;
    }

    private static String buildDatapackDisableCommand(JsonObject args) {
        String name = normalizeDatapackName(getOptionalString(args, "datapack_name", ""));
        return name == null ? null : "datapack disable " + name;
    }

    private static String buildBanPlayerCommand(JsonObject args) {
        String player = normalizePlayerName(getOptionalString(args, "player_name", ""));
        return player == null ? null : "ban " + player + optionalReason(args);
    }

    private static String buildBanIpCommand(JsonObject args) {
        String ip = normalizeIp(getOptionalString(args, "ip", ""));
        return ip == null ? null : "ban-ip " + ip + optionalReason(args);
    }

    private static String buildBanlistCommand(JsonObject args) {
        String type = getOptionalString(args, "banlist_type", "").trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return "banlist";
        }
        return Set.of("players", "ips").contains(type) ? "banlist " + type : null;
    }

    private static String buildPardonPlayerCommand(JsonObject args) {
        String player = normalizePlayerName(getOptionalString(args, "player_name", ""));
        return player == null ? null : "pardon " + player;
    }

    private static String buildPardonIpCommand(JsonObject args) {
        String ip = normalizeIp(getOptionalString(args, "ip", ""));
        return ip == null ? null : "pardon-ip " + ip;
    }

    private static String buildDebugFunctionCommand(JsonObject args) {
        String functionId = normalizeResourceId(getOptionalString(args, "function_id", ""));
        return functionId == null ? null : "debug function " + functionId;
    }

    private static String buildPublishCommand(JsonObject args) {
        boolean allowCommands = getOptionalBoolean(args, "allow_commands", false);
        String gamemode = getOptionalString(args, "publish_mode", getOptionalString(args, "gamemode", "survival")).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("survival", "creative", "adventure", "spectator").contains(gamemode)) {
            return null;
        }
        int port = clamp(getOptionalInt(args, "port", 0), 0, 65535);
        return port > 0 ? "publish " + allowCommands + " " + gamemode + " " + port : "publish " + allowCommands + " " + gamemode;
    }

    private static String buildRandomRollCommand(JsonObject args) {
        int min = clamp(getOptionalInt(args, "min", 1), Integer.MIN_VALUE, Integer.MAX_VALUE);
        int max = clamp(getOptionalInt(args, "max", 100), Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
        return "random roll " + min + ".." + max;
    }

    private static String buildRandomResetCommand(JsonObject args) {
        String id = normalizeResourceId(getOptionalString(args, "random_sequence_id", getOptionalString(args, "bossbar_id", "minecraft:default")));
        return id == null ? null : "random reset " + id;
    }

    private static String buildRotateCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, false);
        if (target == null) {
            return null;
        }
        int yaw = clamp(getOptionalInt(args, "yaw", 0), -180, 180);
        int pitch = clamp(getOptionalInt(args, "pitch_degrees", 0), -90, 90);
        return "rotate " + target + " " + yaw + " " + pitch;
    }

    private static String buildSwingCommand(JsonObject args) {
        String hand = getOptionalString(args, "hand", "mainhand").trim().toLowerCase(Locale.ROOT);
        return Set.of("mainhand", "offhand").contains(hand) ? "swing " + hand : null;
    }

    private static String buildStopwatchCommand(JsonObject args, String operation) {
        String name = normalizeSimpleName(getOptionalString(args, "name", getOptionalString(args, "waypoint_name", "herobrine")), 32);
        return name == null ? null : "stopwatch " + operation + " " + name;
    }

    private static String buildTestCommand(JsonObject args) {
        String id = normalizeCommandPath(getOptionalString(args, "test_id", ""));
        return id == null ? null : "test run " + id;
    }

    private static String buildTickRateCommand(JsonObject args) {
        double rate = clamp(getOptionalDouble(args, "double_value", getOptionalDouble(args, "value", 20.0D)), 1.0D, 10000.0D);
        return "tick rate " + formatDouble(rate);
    }

    private static String buildTickTimedCommand(JsonObject args, String operation) {
        int ticks = clamp(getOptionalInt(args, "value", getOptionalInt(args, "seconds", 1) * 20), 1, 72000);
        return "tick " + operation + " " + ticks;
    }

    private static String buildTeamAddCommand(JsonObject args) {
        String team = normalizeSimpleName(getOptionalString(args, "team_name", ""), 16);
        if (team == null) {
            return null;
        }
        String displayName = hasText(args, "display_name") ? " " + quoteJsonText(getOptionalString(args, "display_name", "")) : "";
        return "team add " + team + displayName;
    }

    private static String buildTeamRemoveCommand(JsonObject args) {
        String team = normalizeSimpleName(getOptionalString(args, "team_name", ""), 16);
        return team == null ? null : "team remove " + team;
    }

    private static String buildTeamJoinCommand(JsonObject args) {
        String team = normalizeSimpleName(getOptionalString(args, "team_name", ""), 16);
        String target = buildTargetSelector(args, true, true);
        return team == null || target == null ? null : "team join " + team + " " + target;
    }

    private static String buildTeamLeaveCommand(JsonObject args) {
        String target = buildTargetSelector(args, true, true);
        return target == null ? null : "team leave " + target;
    }

    private static String buildTeamMessageCommand(JsonObject args) {
        String message = normalizePlainText(getOptionalString(args, "message", ""), 160);
        return message == null ? null : "teammsg " + message;
    }

    private static String buildWaypointListCommand(JsonObject args) {
        if (!hasText(args, "waypoint_name")) {
            return "waypoint list";
        }
        String name = normalizeCommandPath(getOptionalString(args, "waypoint_name", ""));
        return name == null ? null : "waypoint list " + name;
    }

    private static String buildDialogClearCommand(JsonObject args) {
        String target = buildTargetSelector(args, false, true);
        return target == null ? null : "dialog clear " + target;
    }

    private static String buildFetchProfileCommand(JsonObject args) {
        String profile = normalizeProfileId(getOptionalString(args, "profile_id", getOptionalString(args, "player_name", "")));
        return profile == null ? null : "fetchprofile " + profile;
    }

    private static String buildTriggerCommand(JsonObject args) {
        String objective = normalizeSimpleName(getOptionalString(args, "objective", ""), 32);
        if (objective == null) {
            return null;
        }
        String mode = getOptionalString(args, "trigger_mode", "none").trim().toLowerCase(Locale.ROOT);
        if ("none".equals(mode)) {
            return "trigger " + objective;
        }
        if (!Set.of("add", "set").contains(mode)) {
            return null;
        }
        int value = clamp(getOptionalInt(args, "value", 1), -1000000, 1000000);
        return "trigger " + objective + " " + mode + " " + value;
    }

    private static String buildWorldBorderCenterCommand(JsonObject args) {
        int x = clamp(getOptionalInt(args, "x", getOptionalInt(args, "offset_x", 0)), -30000000, 30000000);
        int z = clamp(getOptionalInt(args, "z", getOptionalInt(args, "offset_z", 0)), -30000000, 30000000);
        return "worldborder center " + x + " " + z;
    }

    private static String buildWorldBorderSizeCommand(JsonObject args, String operation) {
        int size = clamp(getOptionalInt(args, "size", getOptionalInt(args, "value", 1000)), 1, MAX_WORLD_BORDER_SIZE);
        int seconds = clamp(getOptionalInt(args, "seconds", 0), 0, 86400);
        return seconds > 0 ? "worldborder " + operation + " " + size + " " + seconds : "worldborder " + operation + " " + size;
    }

    private static String buildWorldBorderDamageBufferCommand(JsonObject args) {
        double value = clamp(getOptionalDouble(args, "double_value", getOptionalDouble(args, "value", 5.0D)), 0.0D, 1000000.0D);
        return "worldborder damage buffer " + formatDouble(value);
    }

    private static String buildWorldBorderWarningTimeCommand(JsonObject args) {
        int seconds = clamp(getOptionalInt(args, "seconds", getOptionalInt(args, "value", 15)), 0, 86400);
        return "worldborder warning time " + seconds;
    }

    private static String buildWorldBorderWarningDistanceCommand(JsonObject args) {
        int distance = clamp(getOptionalInt(args, "warning_distance", getOptionalInt(args, "value", 5)), 0, 1000000);
        return "worldborder warning distance " + distance;
    }

    private static String buildTargetSelector(JsonObject args, boolean allowEntities, boolean allowAllPlayers) {
        String target = getOptionalString(args, "target", "self").trim().toLowerCase(Locale.ROOT);
        int radius = clamp(getOptionalInt(args, "radius", 16), 1, MAX_SELECTOR_RADIUS);
        return switch (target) {
            case "self" -> "@s";
            case "nearest_player" -> "@p";
            case "all_players" -> allowAllPlayers ? "@a" : null;
            case "nearby_entities" -> allowEntities ? "@e[distance=.." + radius + "]" : null;
            case "nearby_non_player_entities" -> allowEntities ? "@e[type=!player,distance=.." + radius + "]" : null;
            case "nearest_entity" -> allowEntities ? "@e[limit=1,sort=nearest]" : null;
            case "nearby_items" -> allowEntities ? "@e[type=minecraft:item,distance=.." + radius + "]" : null;
            default -> null;
        };
    }

    private static String buildPosition(JsonObject args) {
        String mode = getOptionalString(args, "coordinate_mode", "relative").trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "absolute" -> buildAbsolutePosition(args);
            case "local_forward" -> "^ ^ ^" + clamp(getOptionalInt(args, "distance", 5), 1, MAX_SKILL_SUMMON_DISTANCE);
            case "relative" -> buildRelativePosition(args, MAX_TELEPORT_OFFSET);
            default -> null;
        };
    }

    private static String buildAbsolutePosition(JsonObject args) {
        if (!hasNonNull(args, "x") || !hasNonNull(args, "y") || !hasNonNull(args, "z")) {
            return null;
        }
        int x = clamp(getOptionalInt(args, "x", 0), -30000000, 30000000);
        int y = clamp(getOptionalInt(args, "y", 100), -64, 320);
        int z = clamp(getOptionalInt(args, "z", 0), -30000000, 30000000);
        return x + " " + y + " " + z;
    }

    private static String buildRelativePosition(JsonObject args, int maxOffset) {
        int x = clamp(getOptionalInt(args, "offset_x", 0), -maxOffset, maxOffset);
        int y = clamp(getOptionalInt(args, "offset_y", 0), -maxOffset, maxOffset);
        int z = clamp(getOptionalInt(args, "offset_z", 0), -maxOffset, maxOffset);
        return formatRelativePosition(x, y, z);
    }

    private static String formatRelativePosition(int x, int y, int z) {
        return formatRelativeCoord(x) + " " + formatRelativeCoord(y) + " " + formatRelativeCoord(z);
    }

    private static String formatRelativeCoord(int offset) {
        return offset == 0 ? "~" : "~" + offset;
    }

    private static int volume(int x1, int y1, int z1, int x2, int y2, int z2) {
        return (Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
    }

    private static String normalizeGameRule(String rawRule) {
        if (rawRule == null) {
            return null;
        }
        String key = rawRule.trim().replace("-", "_");
        if (key.isEmpty()) {
            return null;
        }
        String exact = GAME_RULES.get(key);
        if (exact != null) {
            return exact;
        }
        return GAME_RULES.get(key.toLowerCase(Locale.ROOT));
    }

    private static String normalizeEmbeddedRunCommand(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 256 || value.startsWith("/") || value.contains("\n")
                || value.contains("\r") || value.contains(";") || value.contains("&&") || value.contains("||")) {
            return null;
        }
        String first = value.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!Set.of("clear", "damage", "difficulty", "effect", "enchant", "experience", "xp", "fill",
                "function", "gamemode", "gamerule", "give", "kill", "locate", "particle", "place",
                "playsound", "recipe", "say", "scoreboard", "setblock", "setworldspawn", "spawnpoint",
                "stopsound", "summon", "tag", "tell", "tellraw", "time", "title", "tp", "teleport",
                "weather", "worldborder").contains(first)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![a-z0-9_.-])([a-z0-9_.-]+):[a-z0-9_/.-]+")
                .matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            if (!VANILLA_NAMESPACE.equals(matcher.group(1))
                    && !MinecraftJavaIdResolver.isRegisteredModResource(matcher.group())) {
                return null;
            }
        }
        return value;
    }

    private static String normalizeTimeSpec(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.matches("[1-9][0-9]{0,6}[tsd]") ? value : null;
    }

    private static String normalizeDisplaySlot(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 32) {
            return null;
        }
        return value.matches("[A-Za-z0-9_.-]+") ? value : null;
    }

    private static String normalizeScoreHolder(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.equals("*")) {
            return value;
        }
        if (value.matches("@[pares](\\[[A-Za-z0-9_=!,.:-]+])?")) {
            return value;
        }
        return value.matches("[A-Za-z0-9_+.-]{1,40}") ? value : null;
    }

    private static String normalizeJsonText(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 512 || value.contains("\n") || value.contains("\r") || value.contains(";")) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(value);
            return element == null || element.isJsonNull() ? null : element.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeHost(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 253) {
            return null;
        }
        return value.matches("[A-Za-z0-9.-]+") ? value : null;
    }

    private static boolean isSafeSlot(String slot) {
        return Set.of("weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet",
                "hotbar.0", "hotbar.1", "hotbar.2", "hotbar.3", "hotbar.4", "hotbar.5", "hotbar.6", "hotbar.7", "hotbar.8").contains(slot);
    }

    private static String optionalReason(JsonObject args) {
        if (!hasText(args, "reason")) {
            return "";
        }
        String reason = normalizePlainText(getOptionalString(args, "reason", ""), 120);
        return reason == null ? "" : " " + reason;
    }

    private static String normalizePlayerName(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.matches("[A-Za-z0-9_]{1,16}") ? value : null;
    }

    private static String normalizeIp(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        String[] parts = value.split("\\.");
        for (String part : parts) {
            int number;
            try {
                number = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (number < 0 || number > 255) {
                return null;
            }
        }
        return value;
    }

    private static String normalizeProfileId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.matches("[A-Za-z0-9_]{1,16}")) {
            return value;
        }
        return value.matches("[0-9a-fA-F-]{32,36}") ? value : null;
    }

    private static String normalizeSimpleName(String raw, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            return null;
        }
        return value.matches("[A-Za-z0-9_+.-]+") ? value : null;
    }

    private static String normalizeCommandPath(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 128) {
            return null;
        }
        return value.matches("[A-Za-z0-9_./:-]+") ? value : null;
    }

    private static String normalizeNbtPath(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 96) {
            return null;
        }
        return value.matches("[A-Za-z0-9_\\.\\[\\]-]+") ? value : null;
    }

    private static String normalizeNbtValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 256 || value.contains("\n") || value.contains("\r") || value.contains(";")) {
            return null;
        }
        return value.matches("[A-Za-z0-9_{}\\[\\]\\.:'\",+\\- ]+") ? value : null;
    }

    private static String normalizeDatapackName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 128) {
            return null;
        }
        return value.matches("[A-Za-z0-9_./:+-]+") ? value : null;
    }

    private static String normalizePlainText(String raw, int maxLength) {
        String value = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').trim();
        if (value.isEmpty() || value.length() > maxLength || value.contains("/") || value.contains(";")) {
            return null;
        }
        return value.replaceAll("\\s+", " ");
    }

    private static String quoteJsonText(String raw) {
        String text = normalizePlainText(raw, 80);
        if (text == null) {
            text = "Herobrine";
        }
        return "{\"text\":\"" + escapeJson(text) + "\"}";
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
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
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            return null;
        }
        String namespace = value.substring(0, value.indexOf(':'));
        return VANILLA_NAMESPACE.equals(namespace) ? value : null;
    }

    private static boolean hasText(JsonObject object, String propertyName) {
        return hasNonNull(object, propertyName) && !getOptionalString(object, propertyName, "").trim().isEmpty();
    }

    private static boolean hasNonNull(JsonObject object, String propertyName) {
        return object != null && object.has(propertyName) && object.get(propertyName) != null && !object.get(propertyName).isJsonNull();
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
            if (!hasNonNull(object, propertyName)) {
                return fallback;
            }
            return object.get(propertyName).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double getOptionalDouble(JsonObject object, String propertyName, double fallback) {
        try {
            if (!hasNonNull(object, propertyName)) {
                return fallback;
            }
            return object.get(propertyName).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getOptionalBoolean(JsonObject object, String propertyName, boolean fallback) {
        try {
            if (!hasNonNull(object, propertyName)) {
                return fallback;
            }
            return object.get(propertyName).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}


