package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.network.ServerConfigClientState;

import java.util.EnumMap;
import java.util.Map;

public final class ServerConfigChecks {
    private static int checks;

    public static void main(String[] args) {
        ServerConfigSnapshot defaults = ServerConfigSnapshot.defaults();
        check(defaults.value(ServerRule.AI_VISION_INTERVAL) == 30, "Vision default changed");
        check(!defaults.enabled(ServerRule.POEM_EXPLOSION) && !defaults.enabled(ServerRule.CLEAVE_SKILL), "Destructive defaults changed");
        check(defaults.enabled(ServerRule.SOUL_BOUND_PACT) && defaults.enabled(ServerRule.PRANKSTER_FAKE_CHEST), "Existing enabled defaults changed");
        check(defaults.enabled(ServerRule.POEM_ACTIONS), "Poem action default must stay enabled");
        for (ServerRule rule : ServerRule.values()) {
            check(ServerRule.fromWireId(rule.wireId()) == rule, "Rule id round trip failed");
            check(defaults.value(rule) == rule.defaultValue(), "Default missing for " + rule);
        }
        expectFailure(() -> ServerRule.fromWireId(100));
        EnumMap<ServerRule, Integer> input = new EnumMap<>(ServerRule.class);
        input.put(ServerRule.AI_VISION_INTERVAL, 900);
        ServerConfigSnapshot bounded = new ServerConfigSnapshot(input);
        input.put(ServerRule.AI_VISION_INTERVAL, 5);
        check(bounded.value(ServerRule.AI_VISION_INTERVAL) == 600, "Snapshot aliases input or lost normalization");
        try { bounded.values().put(ServerRule.POEM_EXPLOSION, 1); throw new AssertionError("Snapshot is mutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check(bounded.with(ServerRule.AI_VISION_INTERVAL, Integer.MIN_VALUE).value(ServerRule.AI_VISION_INTERVAL) == 5, "Interval lower bound missing");
        check(bounded.with(ServerRule.POEM_EXPLOSION, Integer.MAX_VALUE).enabled(ServerRule.POEM_EXPLOSION), "Boolean upper bound missing");
        check(!bounded.with(ServerRule.POEM_EXPLOSION, -1).enabled(ServerRule.POEM_EXPLOSION), "Boolean lower bound missing");

        for (boolean host : new boolean[]{false, true}) {
            for (int permission : new int[]{0, 1, 2, 4}) {
                MemoryStore store = new MemoryStore();
                boolean expected = host || permission >= 2;
                var result = ServerConfigService.update(store, ServerConfigUpdate.single(ServerRule.POEM_EXPLOSION, 1), host, permission);
                check(result.authorized() == expected, "LAN guest/OP authorization changed");
                check(result.changed() == expected && store.writes == (expected ? 1 : 0), "Unauthorized update saved state");
                check(result.effective().enabled(ServerRule.POEM_EXPLOSION) == expected, "Result does not report authoritative value");
            }
        }
        MemoryStore store = new MemoryStore();
        ServerConfigService.update(store, ServerConfigUpdate.single(ServerRule.BLOCK_RESTORATION, 1), false, 2);
        ServerConfigService.update(store, ServerConfigUpdate.single(ServerRule.CLEAN_ITEMS, 1), false, 2);
        check(store.read().enabled(ServerRule.BLOCK_RESTORATION) && store.read().enabled(ServerRule.CLEAN_ITEMS), "Independent patches overwrite one another");
        check(store.writes == 2, "Each changed patch must save once");
        var unchanged = ServerConfigService.update(store, ServerConfigUpdate.single(ServerRule.CLEAN_ITEMS, 1), false, 2);
        check(!unchanged.changed() && store.writes == 2, "Equal configuration should not save again");
        var clamped = ServerConfigService.update(store, ServerConfigUpdate.single(ServerRule.AI_VISION_INTERVAL, -100), true, 0);
        check(clamped.effective().value(ServerRule.AI_VISION_INTERVAL) == 5, "Result must report normalized server value");
        ServerConfigSnapshot beforeLegacy = store.read().with(ServerRule.PRANKSTER_FAKE_CHEST, 0);
        store.value = beforeLegacy;
        EnumMap<ServerRule, Integer> legacy = new EnumMap<>(ServerRule.class);
        legacy.putAll(defaults.values());
        legacy.remove(ServerRule.PRANKSTER_FAKE_CHEST);
        var result = ServerConfigService.update(store, new ServerConfigUpdate(legacy), true, 0);
        check(!result.effective().enabled(ServerRule.PRANKSTER_FAKE_CHEST), "Legacy missing flag overwrites server setting");
        legacy.clear();
        check(result.effective().value(ServerRule.AI_VISION_INTERVAL) == 30, "Update aliases caller map");

        ServerConfigClientState client = new ServerConfigClientState();
        check(!client.editable() && client.snapshot() == null, "Editing enabled before first server reply");
        client.request(1, 100);
        check(client.status() == ServerConfigClientState.Status.PENDING, "Query must enter pending state");
        check(!client.accept(2, defaults, true, true) && client.snapshot() == null, "Unexpected response accepted");
        check(client.accept(1, defaults, true, true) && client.editable(), "Matching query response not accepted");
        client.request(3, 500);
        check(!client.editable() && client.snapshot().equals(defaults), "Pending update must not optimistically change state");
        ServerConfigSnapshot changed = defaults.with(ServerRule.CLEAN_ITEMS, 1);
        check(client.accept(0, changed, true, true) && !client.editable(), "Broadcast must not finish an outstanding request");
        check(client.accept(3, changed, false, false), "Rejection must carry actual state");
        check(!client.editable() && client.status() == ServerConfigClientState.Status.REJECTED, "Permission rejection must disable editing");
        client.request(4, 1000);
        client.expire(1000 + ServerConfigClientState.REQUEST_TIMEOUT_NANOS - 1);
        check(client.status() == ServerConfigClientState.Status.PENDING, "Request timed out too early");
        client.expire(1000 + ServerConfigClientState.REQUEST_TIMEOUT_NANOS);
        check(client.status() == ServerConfigClientState.Status.TIMED_OUT, "Timeout boundary missed");
        check(!client.accept(4, defaults, true, true) && client.snapshot().equals(changed), "Late response rolled back confirmed state");
        client.request(5, 2000);
        check(client.accept(5, defaults, true, true) && client.editable(), "Retry did not recover");
        client.clear();
        check(client.snapshot() == null && !client.editable() && client.status() == ServerConfigClientState.Status.EMPTY, "Disconnect did not clear remote view");
        check(!client.accept(5, changed, true, true), "Disconnected old response was accepted");
        expectFailure(() -> client.request(0, 0));
        System.out.println("ServerConfigChecks: " + checks + " checks passed");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid configuration input to be rejected");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private static final class MemoryStore implements ServerConfigService.Store {
        private ServerConfigSnapshot value = ServerConfigSnapshot.defaults();
        private int writes;
        @Override public ServerConfigSnapshot read() { return value; }
        @Override public void save(ServerConfigSnapshot snapshot) { value = snapshot; writes++; }
    }
}
