package com.whitecloud233.herobrine_companion.network;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks the attributed argument types at every production translation call site. */
public final class TranslationArgumentChecks {
    public static void main(String[] args) throws Exception {
        List<Path> sources = new ArrayList<>();
        for (String directory : args) {
            try (var files = Files.walk(Path.of(directory))) {
                // Other project types are resolved from the freshly compiled main output.
                sources.addAll(files.filter(path -> path.toString().endsWith(".java"))
                        .filter(TranslationArgumentChecks::usesTranslations).sorted().toList());
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var options = List.of("-proc:none", "-implicit:none", "-Xlint:none", "-sourcepath", "",
                    "-classpath", System.getProperty("java.class.path"));
            List<JavaFileObject> inputs = new ArrayList<>();
            manager.getJavaFileObjectsFromPaths(sources).forEach(inputs::add);
            JavaFileObject fixture = fixture();
            inputs.add(fixture);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, options, null, inputs);
            var units = task.parse();
            task.analyze();
            var errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
            if (!errors.isEmpty()) {
                throw new AssertionError("Could not attribute translation sources: " + errors.stream().limit(10).toList());
            }
            var scanner = new ArgumentsScanner(Trees.instance(task), task.getTypes(), task);
            for (var unit : units) {
                if (unit.getSourceFile().toUri().equals(fixture.toUri())) {
                    var fixtureScanner = new ArgumentsScanner(Trees.instance(task), task.getTypes(), task);
                    fixtureScanner.scan(unit, null);
                    if (fixtureScanner.failures.size() != 8) {
                        throw new AssertionError("Audit fixtures: expected 8 unsafe arguments, got " + fixtureScanner.failures);
                    }
                } else {
                    scanner.scan(unit, null);
                }
            }
            if (!scanner.failures.isEmpty()) {
                throw new AssertionError("Unsafe translation arguments:\n" + String.join("\n", scanner.failures));
            }
            System.out.println("TranslationArgumentChecks: " + scanner.calls + " translation calls, "
                    + scanner.arguments + " arguments checked in " + sources.size() + " source files");
        }
    }

    private static JavaFileObject fixture() {
        return new SimpleJavaFileObject(URI.create("string:///TranslationArgumentFixtures.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return """
                        import net.minecraft.network.chat.Component;
                        class TranslationArgumentFixtures {
                            void check(java.util.UUID uuid, java.util.Set<String> set, Object unknown,
                                       Object[] values, Throwable error, boolean flag, Component nested) {
                                Component.translatable("bad.uuid", uuid);
                                Component.translatable("bad.set", set);
                                Component.translatable("bad.char", 'x');
                                Component.translatable("bad.null", (Object) null);
                                Component.translatable("bad.object", unknown);
                                Component.translatable("bad.array", values);
                                Component.translatable("bad.primitive_array", new int[]{1});
                                Component.translatable("bad.nullable_message", error.getMessage());
                                Component.translatable("good.values", "text", 1, true, nested, flag ? nested : "text");
                                Component.translatable("good.array", new String[]{"text"});
                                Component.translatableWithFallback("good.fallback", "fallback", uuid.toString());
                                Component.translatableEscape("good.escaped", uuid, set, unknown, (Object) null);
                            }
                        }
                        """;
            }
        };
    }

    private static boolean usesTranslations(Path path) {
        try {
            return Files.readString(path).contains("translatable");
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private static final class ArgumentsScanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Types types;
        private final List<TypeMirror> allowed;
        private final List<String> failures = new ArrayList<>();
        private int calls;
        private int arguments;

        private ArgumentsScanner(Trees trees, Types types, JavacTask task) {
            this.trees = trees;
            this.types = types;
            this.allowed = List.of("java.lang.String", "java.lang.Number", "java.lang.Boolean",
                            "net.minecraft.network.chat.Component").stream()
                    .map(name -> task.getElements().getTypeElement(name).asType()).toList();
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            var element = trees.getElement(getCurrentPath());
            if (element instanceof ExecutableElement method
                    && method.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals("net.minecraft.network.chat.Component")) {
                String name = method.getSimpleName().toString();
                if (name.equals("translatable") || name.equals("translatableWithFallback")) {
                    calls++;
                    int firstArgument = name.equals("translatableWithFallback") ? 2 : 1;
                    for (int index = firstArgument; index < call.getArguments().size(); index++) {
                        checkArgument(new TreePath(getCurrentPath(), call.getArguments().get(index)),
                                call.getArguments().size() == firstArgument + 1);
                    }
                }
            }
            return super.visitMethodInvocation(call, unused);
        }

        private void checkArgument(TreePath path, boolean varargsArray) {
            if (path.getLeaf() instanceof ConditionalExpressionTree conditional) {
                checkArgument(new TreePath(path, conditional.getTrueExpression()), varargsArray);
                checkArgument(new TreePath(path, conditional.getFalseExpression()), varargsArray);
                return;
            }
            if (path.getLeaf() instanceof ParenthesizedTree parenthesized) {
                checkArgument(new TreePath(path, parenthesized.getExpression()), varargsArray);
                return;
            }
            TypeMirror type = trees.getTypeMirror(path);
            if (varargsArray && type instanceof ArrayType arrayType && !arrayType.getComponentType().getKind().isPrimitive()
                    && path.getLeaf() instanceof NewArrayTree array && array.getInitializers() != null) {
                for (ExpressionTree value : array.getInitializers()) checkArgument(new TreePath(path, value), false);
                return;
            }
            arguments++;
            var element = trees.getElement(path);
            boolean nullableMessage = element instanceof ExecutableElement method
                    && (method.getSimpleName().contentEquals("getMessage") || method.getSimpleName().contentEquals("getLocalizedMessage"))
                    && method.getEnclosingElement().toString().equals("java.lang.Throwable");
            if (varargsArray && type instanceof ArrayType arrayType
                    && !arrayType.getComponentType().getKind().isPrimitive()
                    && allowed.stream().anyMatch(base -> types.isAssignable(arrayType.getComponentType(), base))) {
                return;
            }
            if (type != null && type.getKind() != TypeKind.NULL
                    && !nullableMessage
                    && (allowedPrimitive(type) || allowed.stream().anyMatch(base -> types.isAssignable(type, base)))) {
                return;
            }
            CompilationUnitTree unit = path.getCompilationUnit();
            long position = trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
            failures.add(unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(position)
                    + ": " + path.getLeaf() + (nullableMessage ? " may return null" : " has unsupported type " + type));
        }

        private static boolean allowedPrimitive(TypeMirror type) {
            return switch (type.getKind()) {
                case BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> true;
                default -> false;
            };
        }
    }
}
