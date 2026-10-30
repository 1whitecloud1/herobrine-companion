package com.whitecloud233.herobrine_companion.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ArchitectureChecks {
    private static final String MOD = "com.whitecloud233.herobrine_companion";
    private static final List<String> CLIENT_PREFIXES = List.of(
            "net.minecraft.client.", "net.minecraftforge.client.", "com.mojang.blaze3d.", MOD + ".client.");
    private static final Map<String, Set<String>> CLIENT_EXCEPTIONS = Map.ofEntries(
            Map.entry("network/PoemAnimationPacket.java", Set.of(MOD + ".client.animation.StandalonePoemAnimation")),
            Map.entry("network/QuestStateSyncPacket.java", Set.of(MOD + ".client.ClientQuestState")),
            Map.entry("network/StructureIndexResponsePacket.java", Set.of(MOD + ".client.service.ModContentIndex")),
            Map.entry("entity/logic/HeroInteractionHandler.java", Set.of(MOD + ".client.ClientQuestState", MOD + ".client.event.ClientHooks")),
            Map.entry("network/AgentStatusPacket.java", Set.of(MOD + ".client.network.ClientAgentStatus")),
            Map.entry("network/AgentToolResultPacket.java", Set.of(MOD + ".client.network.ClientAgentToolResult")),
            Map.entry("network/AgentFeatureReply.java", Set.of(MOD + ".client.network.ClientAgentFeatureHandler")),
            Map.entry("network/ToolApprovalPromptPacket.java", Set.of(MOD + ".client.network.ClientAgentToolConfirmation")),
            Map.entry("network/AIObservationPacket.java", Set.of(MOD + ".client.network.ClientAiPrompts")),
            Map.entry("network/ai/ActorDialoguePromptPacket.java", Set.of(MOD + ".client.network.ClientAiPrompts")),
            Map.entry("network/ai/HeroCrossChatPromptPacket.java", Set.of(MOD + ".client.network.ClientAiPrompts")),
            Map.entry("network/ai/PresentCrossChatAiLinePacket.java", Set.of(MOD + ".client.network.ClientAiPrompts", MOD + ".client.service.CrossChatHistoryStore")),
            Map.entry("network/ChallengeAfterimagePacket.java", Set.of(MOD + ".client.network.ClientFxHandler")),
            Map.entry("network/PaleLightningArcPacket.java", Set.of(MOD + ".client.network.ClientFxHandler")),
            Map.entry("network/PaleLightningPacket.java", Set.of(MOD + ".client.network.ClientFxHandler")),
            Map.entry("network/HeroMemoryDigestPacket.java", Set.of(MOD + ".client.network.ClientMemoryDigest")),
            Map.entry("network/SavePosePacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/SyncHeroCosmeticsPacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/SyncHeroVisitPacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/SyncPeacefulPacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/HeroContentPacket.java", Set.of(MOD + ".client.network.ClientHeroContent")),
            Map.entry("network/SyncRewardsPacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/ai/AppendCrossChatHistoryPacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/ai/SyncCrossChatStatePacket.java", Set.of(MOD + ".client.network.ClientStateSync")),
            Map.entry("network/TriggerEternalOathPacket.java", Set.of(MOD + ".client.network.ClientUiDispatch")),
            Map.entry("network/ai/OpenCrossChatInvitePacket.java", Set.of(MOD + ".client.network.ClientUiDispatch")),
            Map.entry("network/ai/OpenCrossSessionHubPacket.java", Set.of(MOD + ".client.network.ClientUiDispatch")),
            Map.entry("network/ai/OpenHeroChatPacket.java", Set.of(MOD + ".client.network.ClientUiDispatch")),
            Map.entry("network/ServerConfigReply.java", Set.of(MOD + ".client.network.ClientServerConfig")),
            // 建筑模板(1.20.1 独立通道并入主通道):handle 内用 FMLEnvironment.dist 守卫后才碰客户端。
            Map.entry("network/TemplateLibraryReply.java", Set.of(MOD + ".client.network.ClientTemplateLibrary")),
            Map.entry("network/TemplatePlacementReply.java", Set.of(MOD + ".client.network.ClientTemplatePlacement")),
            Map.entry("network/TemplatePreviewPacket.java", Set.of(MOD + ".client.structure.TemplatePreviewBox")),
            // 1.21.1 移植新增的气泡同步载荷：handle 内用 FMLEnvironment.dist 守卫后才碰客户端。
            Map.entry("network/ai/SyncSpeechBubblePacket.java", Set.of("net.minecraft.client.Minecraft"))
    );

    /** 陪伴模式的两个入口：必须走 CompanionModeService，不能直接改实体。 */
    private static final Set<String> MODE_ENTRIES = Set.of(
            "network/ToggleCompanionPacket.java", "entity/ai/agent/tool/HeroSetModeTool.java");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path sources = root.resolve("src/main/java/" + MOD.replace('.', '/'));
        List<Path> files;
        try (var paths = Files.walk(sources)) {
            files = paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> protectedSource(relative(sources, p))).sorted().toList();
        }
        if (files.isEmpty()) throw new AssertionError("No architecture sources found: " + sources);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("Architecture checks require a JDK, not a JRE");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> failures = new ArrayList<>();
        Map<String, String> registeredPayloads = new TreeMap<>();
        Set<String> seenExceptions = new HashSet<>();
        Set<String> seenEntries = new HashSet<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : parser.parse()) {
                String file = relative(sources, Path.of(unit.getSourceFile().toUri()));
                Map<String, String> imports = new HashMap<>();
                for (ImportTree entry : unit.getImports()) {
                    String name = entry.getQualifiedIdentifier().toString();
                    imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                }
                Set<String> dependencies = new TreeSet<>();
                Set<String> methods = new HashSet<>();
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                        String name = node.toString();
                        if (CLIENT_PREFIXES.stream().anyMatch(name::startsWith)) dependencies.add(name);
                        return super.visitMemberSelect(node, unused);
                    }

                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        String method = node.getMethodSelect().toString();
                        String methodName = method.substring(method.lastIndexOf('.') + 1);
                        methods.add(method);
                        // NeoForge 1.21.1 没有数字包 ID：每个载荷按 TYPE 注册。
                        // 这里改为收集 PacketHandler 里注册的 payload 类型，检查没有重复注册。
                        if (file.equals("network/PacketHandler.java")
                                && (methodName.equals("playToServer") || methodName.equals("playToClient"))) {
                            String registered = node.getArguments().get(0).toString();
                            // 形如 FooPacket.TYPE：取限定部分（FooPacket）作为载荷标识。
                            String qualifiedFirst = registered.substring(0, registered.lastIndexOf('.'));
                            String shortName = qualifiedFirst.substring(qualifiedFirst.lastIndexOf('.') + 1);
                            if (registeredPayloads.putIfAbsent(shortName, imports.getOrDefault(shortName, unit.getPackageName() + "." + shortName)) != null) {
                                failures.add("Payload registered more than once: " + shortName);
                            }
                        }
                        return super.visitMethodInvocation(node, unused);
                    }
                }.scan(unit, null);
                Set<String> qualifiedDependencies = new HashSet<>(dependencies);
                dependencies.removeIf(name -> qualifiedDependencies.stream().anyMatch(other -> other.startsWith(name + ".")));
                Set<String> allowed = CLIENT_EXCEPTIONS.getOrDefault(file, Set.of());
                for (String dependency : dependencies) {
                    boolean permitted = allowed.stream().anyMatch(a -> dependency.equals(a) || dependency.startsWith(a + "."));
                    if (!permitted) failures.add(file + " depends on client implementation: " + dependency);
                }
                for (String exception : allowed) {
                    if (dependencies.stream().noneMatch(d -> d.equals(exception) || d.startsWith(exception + "."))) {
                        failures.add("Stale client exception: " + file + " -> " + exception);
                    }
                }
                if (!allowed.isEmpty()) seenExceptions.add(file);
                if (MODE_ENTRIES.contains(file)) {
                    seenEntries.add(file);
                    if (!methods.contains("CompanionModeService.changeMode")) failures.add(file + " bypasses companion service");
                    if (methods.stream().anyMatch(m -> m.endsWith(".setCompanionMode") || m.endsWith(".setOwnerUUID"))) {
                        failures.add(file + " mutates companion state directly");
                    }
                }
                if (file.equals("entity/logic/HeroServerTick.java") || file.equals("entity/logic/data/HeroLogic.java")) {
                    if (methods.stream().anyMatch(m -> m.endsWith(".setEquipment") || m.endsWith(".setEquipmentSnapshot")
                            || m.endsWith(".getArmorItemsTag") || m.endsWith(".getHandItemsTag"))) {
                        failures.add(file + " bypasses snapshot coordinator");
                    }
                }
                if (file.equals("event/HeroVisuals.java") && methods.stream().anyMatch(m -> m.endsWith(".addFreshEntity"))) {
                    failures.add("Client visuals must not create server entities");
                }
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) failures.add(diagnostic.toString());
        }
        if (!seenExceptions.equals(CLIENT_EXCEPTIONS.keySet())) {
            Set<String> missing = new TreeSet<>(CLIENT_EXCEPTIONS.keySet());
            missing.removeAll(seenExceptions);
            failures.add("Client exception references a missing source file: " + missing);
        }
        if (!seenEntries.equals(MODE_ENTRIES)) failures.add("Companion mode entry missing from checks");
        if (registeredPayloads.isEmpty()) failures.add("No payload registrations found in PacketHandler");
        if (!failures.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), failures));
        System.out.println("ArchitectureChecks: " + files.size() + " protected sources and " + registeredPayloads.size() + " distinct payload registrations passed");
    }

    private static boolean protectedSource(String file) {
        return file.startsWith("network/") || file.startsWith("entity/logic/") || file.startsWith("entity/companion/")
                || file.equals("entity/HeroEntity.java") || file.equals("event/HeroVisuals.java") || MODE_ENTRIES.contains(file);
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
