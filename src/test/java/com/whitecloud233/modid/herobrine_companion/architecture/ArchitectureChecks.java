package com.whitecloud233.modid.herobrine_companion.architecture;

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
import java.util.stream.Stream;

public final class ArchitectureChecks {
    private static final String MOD = "com.whitecloud233.modid.herobrine_companion";
    private static final List<String> CLIENT_PREFIXES = List.of(
            "net.minecraft.client.", "net.minecraftforge.client.", "com.mojang.blaze3d.", MOD + ".client.");
    private static final Map<String, Set<String>> CLIENT_EXCEPTIONS = Map.of(
            "network/NetworkClientBridge.java", Set.of(
                    MOD + ".client.network.ClientAgentStatus", MOD + ".client.network.ClientAgentToolConfirmation",
                    MOD + ".client.network.ClientAgentToolResult", MOD + ".client.network.ClientAiPrompts",
                    MOD + ".client.network.ClientFxHandler", MOD + ".client.network.ClientMemoryDigest",
                    MOD + ".client.network.ClientAgentFeatureHandler",
                    MOD + ".client.network.ClientHeroContent",
                    MOD + ".client.structure.TemplatePreviewBox",
                    MOD + ".client.network.ClientStateSync", MOD + ".client.network.ClientUiDispatch"),
            "network/PoemAnimationPacket.java", Set.of(MOD + ".client.animation.StandalonePoemAnimation"),
            "network/QuestStateSyncPacket.java", Set.of(MOD + ".client.ClientQuestState"),
            "network/StructureIndexPacket.java", Set.of(MOD + ".client.service.ModContentIndex"),
            "entity/logic/HeroInteractionHandler.java", Set.of(MOD + ".client.ClientQuestState", MOD + ".client.event.ClientHooks"));
    private static final Set<String> MODE_ENTRIES = Set.of(
            "network/ToggleCompanionPacket.java", "entity/ai/agent/tool/HeroSetModeTool.java");

    /**
     * 挑战状态的三份副本。这些键必须经由 {@code ChallengeSession} 读取，
     * 不允许任何调用方直接判读。
     */
    private static final Set<String> CHALLENGE_KEYS = Set.of(
            "IsChallengeActive", "IsFakeOutPhase", "HeroFakeOutPhase");

    /** 臣服压制与龙的锚定。只要求走 {@code NbtKeys} 常量，读写方本来就是多点协作，不强制单一入口。 */
    private static final Set<String> SUBMISSION_KEYS = Set.of(
            "HeroSubmission", "HeroSubmissionYaw", "HeroSubmissionStationary",
            "HeroSubmissionAnchorX", "HeroSubmissionAnchorY", "HeroSubmissionAnchorZ");

    /**
     * Jean 骑乘状态。历史上事件类、容器洗刷类与三个 Mixin 各自复制了一份私有常量，
     * 值相同但极易在改动时只改一处，因此同样收进 {@code NbtKeys}。
     */
    private static final Set<String> JEAN_KEYS = Set.of(
            "HerobrineCompanionJeanFrontHero", "HerobrineCompanionJeanFrontHeroTime",
            "HerobrineCompanionJeanPlayerRidden", "HerobrineCompanionJeanMountNoAi",
            "HerobrineCompanionJeanFrontSeat", "HerobrineCompanionJeanFrontSeatNoAi",
            "herobrine_companion_jean");

    /**
     * 误伤确认与委托进度。误伤确认键曾被 {@code HeroCombatHandler} 与
     * {@code HeroProtectionEvents} 各定义一份；委托键曾被 {@code HeroQuestManager}
     * 与 {@code QuestProgress} 各定义一份。现在分别以 {@code AttackConfirmation}
     * 和 {@code QuestProgress} 为唯一来源。
     */
    private static final Set<String> COMBAT_QUEST_KEYS = Set.of(
            "PendingAttackConfirmPlayer", "PendingAttackConfirmUntil",
            "HeroActiveQuestId", "HeroActiveQuestProgress");

    /**
     * 已迁移到 {@code util/NbtKeys} 的存档键。这些键名一旦拼错就是静默丢数据，
     * 因此不允许在新代码里再以裸字面量出现。新增键时把字符串登记到这里。
     */
    private static final Set<String> MIGRATED_NBT_KEYS = Stream.of(
            CHALLENGE_KEYS.stream(), SUBMISSION_KEYS.stream(),
            JEAN_KEYS.stream(), COMBAT_QUEST_KEYS.stream())
            .flatMap(java.util.function.Function.identity())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** 键的唯一定义处：这些文件里允许出现键名本身。 */
    private static final Set<String> KEY_SOURCE_FILES = Set.of(
            "util/NbtKeys.java", "util/CombatKeys.java", "entity/logic/quest/QuestProgress.java");

    /** 每个键对应的定义类，仅用于生成可读的报错信息。 */
    private static final Map<String, String> KEY_HOME = buildKeyHomes();

    private static Map<String, String> buildKeyHomes() {
        Map<String, String> homes = new HashMap<>();
        CHALLENGE_KEYS.forEach(k -> homes.put(k, "NbtKeys"));
        SUBMISSION_KEYS.forEach(k -> homes.put(k, "NbtKeys"));
        JEAN_KEYS.forEach(k -> homes.put(k, "NbtKeys"));
        COMBAT_QUEST_KEYS.forEach(k -> homes.put(k,
                k.startsWith("PendingAttackConfirm") ? "CombatKeys" : "QuestProgress"));
        return Map.copyOf(homes);
    }

    /** 收集编译单元里所有字符串字面量的值（不含注释）。 */
    private static Set<String> rawStrings(CompilationUnitTree unit) {
        Set<String> values = new HashSet<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitLiteral(LiteralTree node, Void unused) {
                if (node.getValue() instanceof String text) values.add(text);
                return super.visitLiteral(node, unused);
            }
        }.scan(unit, null);
        return values;
    }

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
        Map<Integer, String> actualIds = new TreeMap<>();
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
                        methods.add(method);
                        if (file.equals("network/PacketHandler.java") && method.equals("reg")) {
                            if (node.getArguments().size() != 5
                                    || !(node.getArguments().get(0) instanceof LiteralTree id)
                                    || !(id.getValue() instanceof Integer wireId)
                                    || !(node.getArguments().get(1) instanceof MemberSelectTree type)
                                    || !type.getIdentifier().contentEquals("class")) {
                                failures.add("Packet IDs must be explicit integer literals: " + node);
                            } else {
                                String name = type.getExpression().toString();
                                String qualified = imports.getOrDefault(name, unit.getPackageName() + "." + name);
                                if (wireId < 0 || actualIds.putIfAbsent(wireId, qualified) != null) {
                                    failures.add("Duplicate or negative packet ID: " + wireId);
                                }
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
        checkMigratedNbtKeys(sources, failures);
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) failures.add(diagnostic.toString());
        }
        if (!seenExceptions.equals(CLIENT_EXCEPTIONS.keySet())) failures.add("Client exception references a missing source file");
        if (!seenEntries.equals(MODE_ENTRIES)) failures.add("Companion mode entry missing from checks");
        if (new HashSet<>(actualIds.values()).size() != actualIds.size()) failures.add("Packet type registered more than once");
        Map<Integer, String> expectedIds = readIds(root.resolve("src/test/resources/architecture/packet-ids.txt"));
        if (!actualIds.equals(expectedIds)) failures.add("Packet mapping changed. Expected " + expectedIds + ", actual " + actualIds);
        if (!failures.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), failures));
        System.out.println("ArchitectureChecks: " + files.size() + " protected sources and " + actualIds.size() + " fixed packet IDs passed");
    }

    private static boolean protectedSource(String file) {
        return file.startsWith("network/") || file.startsWith("structure/template/") || file.startsWith("entity/logic/") || file.startsWith("entity/companion/")
                || file.equals("entity/HeroEntity.java") || file.equals("event/HeroVisuals.java") || MODE_ENTRIES.contains(file);
    }

    /**
     * NBT 键的裸字面量检查。范围是整个源码树，不是 {@link #protectedSource} 的子集：
     * 挑战键散落在 fight/、event/、entity/ 各处，只查受保护目录会漏掉大半。
     *
     * <p>只解析不编译，因此不依赖 Minecraft 类路径。</p>
     */
    private static void checkMigratedNbtKeys(Path sources, List<String> failures) throws IOException {
        List<Path> all;
        try (var paths = Files.walk(sources)) {
            all = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            failures.add("NBT key checks require a JDK, not a JRE");
            return;
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null,
                    manager.getJavaFileObjectsFromPaths(all));
            for (CompilationUnitTree unit : parser.parse()) {
                String file = relative(sources, Path.of(unit.getSourceFile().toUri()));
                // 键的唯一来源就是各自的常量类本身，不允许任何调用方再写裸字面量。
                if (KEY_SOURCE_FILES.contains(file)) continue;
                for (String literal : rawStrings(unit)) {
                    if (!MIGRATED_NBT_KEYS.contains(literal)) continue;
                    failures.add(file + " hardcodes migrated NBT key \"" + literal
                            + "\"; use the constants defined in "
                            + KEY_HOME.getOrDefault(literal, "the corresponding key class")
                            + (CHALLENGE_KEYS.contains(literal)
                                    ? " and read through ChallengeSession, not the raw key" : ""));
                }
            }
        }
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Map<Integer, String> readIds(Path path) throws IOException {
        Map<Integer, String> ids = new TreeMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] entry = line.split("\\s+");
            if (entry.length != 2 || ids.put(Integer.parseInt(entry[0]), entry[1]) != null) {
                throw new AssertionError("Invalid packet baseline: " + line);
            }
        }
        if (ids.isEmpty()) throw new AssertionError("Packet baseline is empty");
        return ids;
    }
}
