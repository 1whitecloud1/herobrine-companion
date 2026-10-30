package com.whitecloud233.modid.herobrine_companion.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
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

public final class AIArchitectureChecks {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path client = root.resolve("src/main/java/com/whitecloud233/modid/herobrine_companion/client");
        List<Path> files = new ArrayList<>();
        files.add(client.resolve("service/AIService.java"));
        files.add(client.resolve("service/AIRequestContext.java"));
        files.add(client.resolve("service/AIChatAttempt.java"));
        files.add(client.resolve("service/ConversationSummaryService.java"));
        try (var paths = Files.walk(client.resolve("llm"))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        Set<String> wireFields = Set.of("choices", "finish_reason", "stop_reason", "candidates", "incomplete_details");
        Set<String> chainMethods = Set.of("chatWithRetry", "sendStreamingRequest", "finalizeTextReply", "executeNamedToolAction",
                "executeToolAction", "executeComputerControlAction", "executeWebLookupAction", "executeRegistryLookupAction",
                "executeJvmCodeAction", "executeAgentToolAction", "retryWithFallback", "completeReply");
        List<String> failures = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : task.parse()) {
                String filename = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                boolean service = filename.equals("AIService.java");
                boolean summary = filename.equals("ConversationSummaryService.java");
                new TreeScanner<Void, Void>() {
                    @Override public Void visitLiteral(LiteralTree tree, Void unused) {
                        if (service && tree.getValue() instanceof String value && wireFields.contains(value)) {
                            failures.add("AIService parses provider field: " + value);
                        }
                        return super.visitLiteral(tree, unused);
                    }
                    @Override public Void visitNewClass(NewClassTree tree, Void unused) {
                        if (service && (tree.getIdentifier().toString().endsWith("Screen"))) {
                            failures.add("AIService constructs screen: " + tree.getIdentifier());
                        }
                        return super.visitNewClass(tree, unused);
                    }
                    @Override public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                        String name = tree.toString();
                        if (summary && (tree.getIdentifier().contentEquals("markApiKeyValid")
                                || tree.getIdentifier().contentEquals("markApiKeyInvalid"))) {
                            failures.add("Background summary mutates global credential validation: " + name);
                        }
                        if (!service && (name.startsWith("net.minecraft.client.") || name.startsWith("net.minecraftforge.client."))) {
                            failures.add(filename + " depends on game UI: " + name);
                        }
                        if (service && tree.getIdentifier().contentEquals("setScreen")) failures.add("AIService opens UI directly");
                        return super.visitMemberSelect(tree, unused);
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        if (service && chainMethods.contains(tree.getName().toString()) && tree.getParameters().size() > 3) {
                            failures.add("Long retry argument chain restored: " + tree.getName());
                        }
                        return super.visitMethod(tree, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        var arguments = tree.getArguments();
                        if (service && tree.getMethodSelect().toString().equals("AIDebugLog.record")
                                && arguments.size() == 9 && arguments.get(0) instanceof LiteralTree taskName
                                && Set.of("localize", "actor_dialogue").contains(taskName.getValue())
                                && arguments.get(7) instanceof LiteralTree rawBody && rawBody.getValue() == null) {
                            failures.add("Debug log discards raw response for " + taskName.getValue());
                        }
                        return super.visitMethodInvocation(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .forEach(d -> failures.add(d.toString()));
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("AIArchitectureChecks: " + files.size() + " AI source boundaries passed");
    }
}
