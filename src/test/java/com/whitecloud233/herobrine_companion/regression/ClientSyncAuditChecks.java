package com.whitecloud233.herobrine_companion.regression;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.ToolProvider;

/** Behavioral regressions for snapshots, claim acknowledgements and connection/respawn state. */
public final class ClientSyncAuditChecks {
    private static final String MOD = "com.whitecloud233.herobrine_companion";
    private static Path sourceRoot;

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("") : Path.of(args[0]);
        sourceRoot = root.resolve("src/main/java/" + MOD.replace('.', '/'));
        String fixture;
        try (var stream = ClientSyncAuditChecks.class.getResourceAsStream("/regression/client-sync-audit.java.txt")) {
            if (stream == null) throw new AssertionError("Missing synchronization fixture");
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> blocks = Map.ofEntries(
                Map.entry("PROFILE", members("entity/logic/data/HeroOwnerProfile.java",
                        Set.of("<init>", "hasClaimedReward", "claimReward", "applyClaimedRewardsSnapshot", "getClaimedRewardsRevision", "getClaimedRewards"), true)),
                Map.entry("ENTITY", members("entity/HeroEntity.java", Set.of("hasClaimedReward", "claimReward", "applyClaimedRewardsSnapshot", "getClaimedRewardsRevision", "getClaimedRewards"), false)),
                Map.entry("CLIENT", members("client/network/ClientStateSync.java", Set.of("applySyncRewards", "clearSessionState"), false)),
                Map.entry("SCREEN", members("client/gui/HeroRewardScreen.java", Set.of("needsStateRefresh", "requestReward"), false)),
                Map.entry("CLAIM", members("network/ClaimRewardPacket.java", Set.of("handle"), false)),
                Map.entry("QUEST", members("entity/logic/quest/QuestProgress.java", Set.of("copyTo"), true)),
                Map.entry("COOLDOWN", members("entity/logic/quest/QuestCooldownStore.java", Set.of("copyTo"), true)),
                Map.entry("QUEST_CLIENT", members("client/ClientQuestState.java", null, false)),
                Map.entry("DIGEST", members("client/network/ClientMemoryDigest.java", null, false)),
                Map.entry("AGENT", members("client/agent/ClientAgentStatusStore.java", null, false)),
                Map.entry("TOOLS", members("client/agent/ClientAgentToolRequestStore.java", null, false)),
                Map.entry("EVENTS", members("client/event/ClientForgeEvents.java", Set.of("onClientPlayerLogin", "onClientPlayerLogout"), false)),
                Map.entry("REQUEST", members("client/gui/HeroRequestScreen.java", Set.of("refreshAcceptState"), false)));
        for (var block : blocks.entrySet()) fixture = fixture.replace("/* " + block.getKey() + " */", block.getValue());
        for (String type : new String[]{"ClientQuestState", "ClientAgentStatusStore", "ClientAgentToolRequestStore"}) {
            fixture = fixture.replace(MOD + ".client." + (type.equals("ClientQuestState") ? "" : "agent.") + type, type);
        }
        fixture = fixture.replace(MOD + ".client.network.ClientStateSync", "ClientStateSync")
                .replace("net.minecraft.server.level.ServerLevel", "ServerLevel");
        fixture = fixture.replace("/* CLAIM_INVOKE */", MOD.contains(".modid.")
                ? "handle(new ClaimRewardPacket(entityId, rewardId), () -> new NetworkEvent.Context(player));"
                : "new ClaimRewardPacket(entityId, rewardId).handle(new IPayloadContext(player));");
        try (var compiled = SourceFixturesChecks.compile("ClientSyncAuditProbe", fixture)) {
            compiled.type().getMethod("verify").invoke(null);
        }
        requireCalls("entity/logic/data/PlayerLifecycleHandler.java", "onPlayerClone", Set.of("QuestProgress.copyTo", "QuestCooldownStore.copyTo"));
        requireCalls("entity/logic/data/PlayerLifecycleHandler.java", "syncVisitState", Set.of("HeroQuestManager.syncQuestStateTo"));
        requireCalls("client/gui/HeroRewardScreen.java", "render", Set.of("needsStateRefresh"));
        requireCalls("client/gui/HeroRequestScreen.java", "render", Set.of("refreshAcceptState"));
        System.out.println("Client synchronization lifecycle/UI wiring checks passed");
    }

    private static String members(String file, Set<String> methods, boolean fields) throws Exception {
        return SourceFixturesChecks.members(Files.readString(sourceRoot.resolve(file)), tree -> methods == null
                || tree instanceof MethodTree method && methods.contains(method.getName().toString())
                || fields && tree instanceof VariableTree field && (field.getModifiers().getFlags().contains(Modifier.STATIC)
                || Set.of("hero", "claimedRewards", "claimedRewardsRevision").contains(field.getName().toString())));
    }

    private static void requireCalls(String file, String methodName, Set<String> expected) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        Set<String> observed = new java.util.HashSet<>();
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, java.util.List.of("-proc:none"), null,
                    manager.getJavaFileObjects(sourceRoot.resolve(file)));
            for (var unit : task.parse()) new TreeScanner<Void, Void>() {
                @Override public Void visitMethod(MethodTree method, Void unused) {
                    return method.getName().contentEquals(methodName) ? super.visitMethod(method, unused) : null;
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    String name = call.getMethodSelect().toString();
                    for (String required : expected) if (name.endsWith(required)) observed.add(required);
                    return super.visitMethodInvocation(call, unused);
                }
            }.scan(unit, null);
        }
        if (!observed.equals(expected)) throw new AssertionError(file + "#" + methodName + " is missing synchronization calls " + expected);
    }
}
