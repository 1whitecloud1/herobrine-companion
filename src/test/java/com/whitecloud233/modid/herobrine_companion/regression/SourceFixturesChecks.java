package com.whitecloud233.modid.herobrine_companion.regression;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/** Test-only compiler support (the regression source set includes files ending in Checks). */
public final class SourceFixturesChecks {
    private SourceFixturesChecks() { }

    public static String resource(String name) throws Exception {
        try (var stream = SourceFixturesChecks.class.getResourceAsStream("/regression/c-group/" + name)) {
            if (stream == null) throw new AssertionError("Missing baseline " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String members(String source, Predicate<Tree> include) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavaFileObject input = new SimpleJavaFileObject(URI.create("string:///Fixture.java"), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignored) { return source; }
            };
            JavacTask task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null, List.of(input));
            var unit = task.parse().iterator().next();
            var positions = Trees.instance(task).getSourcePositions();
            ClassTree type = (ClassTree) unit.getTypeDecls().get(0);
            StringBuilder selected = new StringBuilder();
            for (Tree member : type.getMembers()) {
                if (!include.test(member)) continue;
                int start = (int) positions.getStartPosition(unit, member), end = (int) positions.getEndPosition(unit, member);
                selected.append(source, start, end).append('\n');
            }
            return selected.toString();
        }
    }

    public static Compiled compile(String name, String source) throws Exception {
        Path root = Files.createDirectories(Path.of("build", "regression-fixtures"));
        Path directory = Files.createTempDirectory(root, name + "-");
        Path file = directory.resolve(name + ".java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, errors, "-proc:none", "-encoding", "UTF-8",
                "-classpath", System.getProperty("java.class.path"), "-d", directory.toString(), file.toString());
        if (status != 0) throw new AssertionError("Cannot compile " + name + ": " + errors.toString(StandardCharsets.UTF_8));
        URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, SourceFixturesChecks.class.getClassLoader());
        return new Compiled(loader.loadClass(name), loader);
    }

    public record Compiled(Class<?> type, URLClassLoader loader) implements AutoCloseable {
        @Override public void close() throws Exception { this.loader.close(); }
    }
}
