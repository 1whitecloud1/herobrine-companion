package com.whitecloud233.modid.herobrine_companion.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConfigArchitectureChecks {
    private static final Set<String> LEGACY_FIELDS = Set.of("poemOfTheEndExplosion", "poemActionsEnabled", "heroKingAuraEnabled",
            "heroBlockRestoration", "heroCleanItems", "cleaveSkillEnabled", "soulBoundPactEnabled",
            "abyssalGazeEnabled", "transcendencePermitEnabled", "aiVisionInterval", "awakenedMobAiDialogueEnabled",
            "heroLeafVanishEnabled", "pranksterFakeChestEnabled");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path sources = root.resolve("src/main/java");
        List<Path> files;
        try (var paths = Files.walk(sources)) { files = paths.filter(p -> p.toString().endsWith(".java")).toList(); }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> failures = new ArrayList<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        boolean[] guards = {false, false, false, false};
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : parser.parse()) {
                String file = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                boolean consumer = file.equals("ConfigScreen.java") || file.equals("SyncServerConfigPacket.java")
                        || file.equals("ServerConfigRequest.java");
                boolean core = Set.of("ServerRule.java", "ServerConfigSnapshot.java", "ServerConfigUpdate.java", "ServerConfigService.java").contains(file);
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                        String name = tree.toString();
                        if (!file.equals("ServerConfigAccess.java") && (tree.getExpression().toString().equals("Config")
                                || tree.getExpression().toString().endsWith(".config.Config"))
                                && LEGACY_FIELDS.contains(tree.getIdentifier().toString())) failures.add(file + " reads legacy mutable rule: " + name);
                        if (core && (name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge."))) failures.add(file + " depends on engine adapter: " + name);
                        return super.visitMemberSelect(tree, unused);
                    }
                    @Override public Void visitAssignment(AssignmentTree tree, Void unused) {
                        if (consumer && tree.getVariable().toString().startsWith("Config.")) failures.add(file + " writes configuration cache directly");
                        return super.visitAssignment(tree, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        String method = tree.getMethodSelect().toString();
                        if (consumer && method.startsWith("Config.") && (method.endsWith(".set") || method.endsWith(".save"))) failures.add(file + " bypasses configuration service");
                        if (file.equals("SyncServerConfigPacket.java") && method.equals("ServerConfigAccess.update")) guards[0] = true;
                        if (file.equals("ServerConfigRequest.java") && method.equals("ServerConfigAccess.update")) guards[1] = true;
                        if (file.equals("ServerConfigAccess.java") && method.endsWith(".isSingleplayerOwner")) guards[2] = true;
                        if (file.equals("Config.java") && method.endsWith(".getSpec")) guards[3] = true;
                        if (file.equals("ServerConfigAccess.java") && method.endsWith(".isSingleplayer")) failures.add("LAN guests must not inherit host authority");
                        return super.visitMethodInvocation(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) failures.add(diagnostic.toString());
        for (boolean guard : guards) if (!guard) failures.add("Required config ownership/spec/service guard is missing");
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("ConfigArchitectureChecks: " + files.size() + " sources checked for configuration boundary regressions");
    }
}
