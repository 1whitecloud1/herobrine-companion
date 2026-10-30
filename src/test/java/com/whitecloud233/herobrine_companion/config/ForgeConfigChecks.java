package com.whitecloud233.herobrine_companion.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.util.EnumMap;
import java.util.Map;

public final class ForgeConfigChecks {
    private static int checks;

    public static void main(String[] args) {
        CommentedConfig memory = CommentedConfig.inMemory();
        Config.SPEC.correct(memory);
        // 1.21.1（NeoForge）没有 Forge 的 setConfig，改用 IConfigSpec.ILoadedConfig 绑定同一份内存配置。
        Config.SPEC.acceptConfig(memoryConfig(memory));
        try {
            ServerConfigAccess.reload();
            Config.reloadLocalValues();
            check(ServerConfigAccess.readLocal().equals(ServerConfigSnapshot.defaults()), "Declared defaults disagree with Forge spec");
            Map<ServerRule, String> keys = Map.ofEntries(
                    Map.entry(ServerRule.POEM_EXPLOSION, "poemOfTheEndExplosion_v2"),
                    Map.entry(ServerRule.POEM_ACTIONS, "poemActionsEnabled"),
                    Map.entry(ServerRule.KING_AURA, "heroKingAuraEnabled_v2"),
                    Map.entry(ServerRule.BLOCK_RESTORATION, "heroBlockRestoration_v2"),
                    Map.entry(ServerRule.CLEAN_ITEMS, "heroCleanItems_v2"),
                    Map.entry(ServerRule.CLEAVE_SKILL, "cleaveSkillEnabled"),
                    Map.entry(ServerRule.SOUL_BOUND_PACT, "soulBoundPactEnabled"),
                    Map.entry(ServerRule.ABYSSAL_GAZE, "abyssalGazeEnabled"),
                    Map.entry(ServerRule.TRANSCENDENCE_PERMIT, "transcendencePermitEnabled"),
                    Map.entry(ServerRule.AI_VISION_INTERVAL, "aiVisionInterval"),
                    Map.entry(ServerRule.AWAKENED_DIALOGUE, "awakenedMobAiDialogueEnabled"),
                    Map.entry(ServerRule.LEAF_VANISH, "heroLeafVanishEnabled"),
                    Map.entry(ServerRule.PRANKSTER_FAKE_CHEST, "pranksterFakeChestEnabled"),
                    Map.entry(ServerRule.BATTLE_MODE_TRUST_THRESHOLD, "battleModeTrustThreshold"),
                    Map.entry(ServerRule.BATTLE_MODE_DURATION_SECONDS, "battleModeDurationSeconds"),
                    Map.entry(ServerRule.BATTLE_MODE_COOLDOWN_SECONDS, "battleModeCooldownSeconds"));
            for (ServerRule rule : ServerRule.values()) {
                int next = rule.isToggle() ? (rule.defaultValue() == 0 ? 1 : 0) : 123;
                ServerConfigSnapshot desired = ServerConfigAccess.readLocal().with(rule, next);
                ServerConfigAccess.saveLocal(desired);
                Object stored = memory.get(keys.get(rule));
                check(stored.equals(rule.isToggle() ? next != 0 : next), "Wrong Forge binding for " + rule);
                check(ServerConfigAccess.snapshot().equals(desired), "Published snapshot mismatches stored config for " + rule);
            }
            ServerConfigSnapshot local = ServerConfigAccess.readLocal();
            var remote = ServerConfigSnapshot.defaults().with(ServerRule.AI_VISION_INTERVAL, 500);
            ServerConfigAccess.acceptRemote(remote);
            check(ServerConfigAccess.snapshot().equals(remote), "Remote snapshot not published");
            check(ServerConfigAccess.readLocal().equals(local), "Remote snapshot overwrote local config");
            ServerConfigAccess.reload();
            check(ServerConfigAccess.snapshot().equals(remote), "Local reload overwrote active remote rules");
            LocalConfigAccess.update(Config.ENABLE_UPDATE_CHECKER, false);
            check(!Config.enableUpdateChecker && ServerConfigAccess.snapshot().equals(remote), "Local preferences changed remote rule view");
            ServerConfigAccess.restoreLocalView();
            check(ServerConfigAccess.snapshot().equals(local), "Disconnect failed to restore local rules");
            check(memory.get("enableUpdateChecker").equals(false), "Local option not saved to memory");
            check(Config.aiVisionInterval == local.value(ServerRule.AI_VISION_INTERVAL), "Compatibility field no longer mirrors snapshot");
            ServerConfigAccess.saveLocal(ServerConfigSnapshot.defaults());
            check(ServerConfigAccess.readLocal().equals(ServerConfigSnapshot.defaults()), "Reset to defaults failed");
            System.out.println("ForgeConfigChecks: " + checks + " checks passed");
        } finally {
            Config.SPEC.acceptConfig(null);
        }
    }

    /**
     * 用内存 CommentedConfig 构造 ILoadedConfig，替代 Forge 的 ModConfigSpec#setConfig。
     *
     * <p>1.21.1（NeoForge）把 ILoadedConfig 密封到包内私有的 LoadedConfig，既不能匿名实现也不能用
     * 动态代理，因此这里反射调用它唯一的构造器（config, path, modConfig）。modConfig 传 null 即可：
     * 这项检查只读 config()，不写盘。
     */
    private static net.neoforged.fml.config.IConfigSpec.ILoadedConfig memoryConfig(CommentedConfig config) {
        try {
            Class<?> loadedConfig = Class.forName("net.neoforged.fml.config.LoadedConfig");
            var ctor = loadedConfig.getDeclaredConstructor(CommentedConfig.class, java.nio.file.Path.class,
                    Class.forName("net.neoforged.fml.config.ModConfig"));
            ctor.setAccessible(true);
            return (net.neoforged.fml.config.IConfigSpec.ILoadedConfig) ctor.newInstance(
                    config, java.nio.file.Path.of("herobrine_companion-memory.toml"), null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot build ILoadedConfig for the in-memory check", e);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
