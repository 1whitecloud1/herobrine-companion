package com.whitecloud233.herobrine_companion.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/** NeoForge 规范与不可变运行快照之间唯一的规则映射。 */
public final class ServerConfigAccess {
    private static final Map<ServerRule, ModConfigSpec.ConfigValue<?>> VALUES = createValues();
    private static volatile ServerConfigSnapshot effective = ServerConfigSnapshot.defaults();
    private static boolean remoteView;
    private static final ServerConfigService.Store STORE = new ServerConfigService.Store() {
        @Override public ServerConfigSnapshot read() { return effective; }
        @Override public void save(ServerConfigSnapshot snapshot) { saveLocal(snapshot); }
    };

    private ServerConfigAccess() {
    }

    private static Map<ServerRule, ModConfigSpec.ConfigValue<?>> createValues() {
        EnumMap<ServerRule, ModConfigSpec.ConfigValue<?>> values = new EnumMap<>(ServerRule.class);
        values.put(ServerRule.POEM_EXPLOSION, Config.POEM_OF_THE_END_EXPLOSION);
        values.put(ServerRule.POEM_ACTIONS, Config.POEM_ACTIONS_ENABLED);
        values.put(ServerRule.KING_AURA, Config.HERO_KING_AURA_ENABLED);
        values.put(ServerRule.BLOCK_RESTORATION, Config.HERO_BLOCK_RESTORATION);
        values.put(ServerRule.CLEAN_ITEMS, Config.HERO_CLEAN_ITEMS);
        values.put(ServerRule.CLEAVE_SKILL, Config.CLEAVE_SKILL_ENABLED);
        values.put(ServerRule.SOUL_BOUND_PACT, Config.SOUL_BOUND_PACT_ENABLED);
        values.put(ServerRule.ABYSSAL_GAZE, Config.ABYSSAL_GAZE_ENABLED);
        values.put(ServerRule.TRANSCENDENCE_PERMIT, Config.TRANSCENDENCE_PERMIT_ENABLED);
        values.put(ServerRule.AI_VISION_INTERVAL, Config.AI_VISION_INTERVAL);
        values.put(ServerRule.AWAKENED_DIALOGUE, Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED);
        values.put(ServerRule.LEAF_VANISH, Config.HERO_LEAF_VANISH_ENABLED);
        values.put(ServerRule.PRANKSTER_FAKE_CHEST, Config.PRANKSTER_FAKE_CHEST_ENABLED);
        values.put(ServerRule.BATTLE_MODE_TRUST_THRESHOLD, Config.BATTLE_MODE_TRUST_THRESHOLD);
        values.put(ServerRule.BATTLE_MODE_DURATION_SECONDS, Config.BATTLE_MODE_DURATION_SECONDS);
        values.put(ServerRule.BATTLE_MODE_COOLDOWN_SECONDS, Config.BATTLE_MODE_COOLDOWN_SECONDS);
        if (values.size() != ServerRule.values().length) throw new IllegalStateException("Missing NeoForge server-rule binding");
        return Map.copyOf(values);
    }

    public static ServerConfigSnapshot snapshot() { return effective; }

    public static ServerConfigSnapshot readLocal() {
        if (!Config.SPEC.isLoaded()) return ServerConfigSnapshot.defaults();
        EnumMap<ServerRule, Integer> values = new EnumMap<>(ServerRule.class);
        VALUES.forEach((rule, value) -> values.put(rule, rule.isToggle() ? ((Boolean) value.get() ? 1 : 0) : (Integer) value.get()));
        return new ServerConfigSnapshot(values);
    }

    static synchronized void reload() { if (!remoteView) publish(readLocal()); }

    public static synchronized void saveLocal(ServerConfigSnapshot snapshot) {
        if (!Config.SPEC.isLoaded()) return;
        boolean changed = false;
        for (ServerRule rule : ServerRule.values()) {
            ModConfigSpec.ConfigValue<?> config = VALUES.get(rule);
            if (rule.isToggle()) {
                ModConfigSpec.BooleanValue value = (ModConfigSpec.BooleanValue) config;
                boolean next = snapshot.enabled(rule);
                if (value.get() != next) { value.set(next); changed = true; }
            } else {
                ModConfigSpec.IntValue value = (ModConfigSpec.IntValue) config;
                int next = snapshot.value(rule);
                if (value.get() != next) { value.set(next); changed = true; }
            }
        }
        if (changed) Config.saveSpec();
        publish(readLocal());
    }

    public static synchronized ServerConfigService.Result update(ServerPlayer player, ServerConfigUpdate update) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null || !server.isSameThread()) return new ServerConfigService.Result(effective, false, false);
        return ServerConfigService.update(STORE, update, server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2) ? 2 : 0);
    }

    public static boolean canEdit(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        return server != null && ServerConfigService.canEdit(server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2) ? 2 : 0);
    }

    /** 仅更新远端服务器的本地运行视图，不将远端规则写入本机配置文件。 */
    public static synchronized void acceptRemote(ServerConfigSnapshot snapshot) {
        remoteView = true;
        publish(snapshot);
    }

    public static synchronized void restoreLocalView() {
        remoteView = false;
        publish(readLocal());
    }

    private static void publish(ServerConfigSnapshot snapshot) {
        effective = snapshot;
        Config.poemOfTheEndExplosion = snapshot.enabled(ServerRule.POEM_EXPLOSION);
        Config.poemActionsEnabled = snapshot.enabled(ServerRule.POEM_ACTIONS);
        Config.heroKingAuraEnabled = snapshot.enabled(ServerRule.KING_AURA);
        Config.heroBlockRestoration = snapshot.enabled(ServerRule.BLOCK_RESTORATION);
        Config.heroCleanItems = snapshot.enabled(ServerRule.CLEAN_ITEMS);
        Config.cleaveSkillEnabled = snapshot.enabled(ServerRule.CLEAVE_SKILL);
        Config.soulBoundPactEnabled = snapshot.enabled(ServerRule.SOUL_BOUND_PACT);
        Config.abyssalGazeEnabled = snapshot.enabled(ServerRule.ABYSSAL_GAZE);
        Config.transcendencePermitEnabled = snapshot.enabled(ServerRule.TRANSCENDENCE_PERMIT);
        Config.aiVisionInterval = snapshot.value(ServerRule.AI_VISION_INTERVAL);
        Config.awakenedMobAiDialogueEnabled = snapshot.enabled(ServerRule.AWAKENED_DIALOGUE);
        Config.heroLeafVanishEnabled = snapshot.enabled(ServerRule.LEAF_VANISH);
        Config.pranksterFakeChestEnabled = snapshot.enabled(ServerRule.PRANKSTER_FAKE_CHEST);
    }
}
