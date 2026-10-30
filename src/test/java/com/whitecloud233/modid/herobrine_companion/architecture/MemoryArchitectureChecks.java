package com.whitecloud233.modid.herobrine_companion.architecture;

import com.sun.source.tree.CompilationUnitTree;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MemoryArchitectureChecks {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path client = root.resolve("src/main/java/com/whitecloud233/modid/herobrine_companion/client");
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(client.resolve("memory"))) { paths.filter(p -> p.toString().endsWith(".java")).forEach(files::add); }
        files.add(client.resolve("gui/MemoryImportScreen.java"));
        files.add(client.resolve("service/ConversationStore.java"));
        files.add(client.resolve("service/AIPromptAssembler.java"));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> failures = new ArrayList<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        Set<String> calls = new HashSet<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : parser.parse()) {
                String filename = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                boolean pure = unit.getPackageName().toString().endsWith("client.memory");
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                        String name = tree.toString();
                        if (pure && (name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.")
                                || name.startsWith("java.net.") || name.contains(".client.service.AIService")
                                || name.contains(".network.PacketHandler") || name.contains(".client.jvm."))) {
                            failures.add(filename + " violates import boundary: " + name);
                        }
                        if (filename.equals("ConversationStore.java") && name.startsWith("net.minecraft.client.")) {
                            failures.add("Conversation storage depends directly on game UI");
                        }
                        return super.visitMemberSelect(tree, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        String method = tree.getMethodSelect().toString();
                        calls.add(filename + ":" + method);
                        if (filename.equals("MemoryImportScreen.java") && (method.equals("Files.writeString")
                                || method.equals("Files.readAllBytes") || method.startsWith("JsonParser.")
                                || method.startsWith("AIService."))) failures.add("Import UI bypasses independent service: " + method);
                        if (pure && (method.endsWith(".sendToServer") || method.endsWith(".requestExecution"))) failures.add("Import executes tools: " + method);
                        return super.visitMethodInvocation(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) failures.add(diagnostic.toString());
        if (!calls.contains("ConversationStore.java:ConversationFileWriter.write")) failures.add("Import persistence bypasses atomic writer");
        if (calls.stream().noneMatch(call -> call.startsWith("AIPromptAssembler.java:") && call.endsWith("ImportedMemoryContext.render"))) failures.add("Imported reference not connected to prompt assembly");
        if (!calls.contains("MemoryImportScreen.java:store.importMemory")) failures.add("Import screen is not connected to storage");
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("MemoryArchitectureChecks: " + files.size() + " import source boundaries passed");
    }
}
