package com.whitecloud233.herobrine_companion.client.jvm.impl;

import com.whitecloud233.herobrine_companion.client.jvm.JvmCodeAction;
import com.whitecloud233.herobrine_companion.client.jvm.JvmCodeCompiler;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 唯一的 JDK 编译入口（单一职责：把方法体编译并加载为契约实例）。
 * 用内存文件管理器把 .class 字节保存在内存中，避免写盘，也不污染游戏类加载器。
 */
public class InMemoryJvmCodeCompiler implements JvmCodeCompiler {
    private static final String WRAPPER_PACKAGE = "com.whitecloud233.herobrine_companion.client.jvm";
    private static final String WRAPPER_CLASS = "HbGeneratedJvmAction";
    private static final String WRAPPER_QUALIFIED = WRAPPER_PACKAGE + "." + WRAPPER_CLASS;
    private static final int MAX_SOURCE_LENGTH = 12_000;

    private static final String WRAPPER_PREAMBLE = "package " + WRAPPER_PACKAGE + ";\n"
            + "import net.minecraft.client.Minecraft;\n"
            + "import net.minecraft.client.player.LocalPlayer;\n"
            + "import net.minecraft.server.MinecraftServer;\n"
            + "import net.minecraft.server.level.ServerLevel;\n"
            + "import net.minecraft.server.level.ServerPlayer;\n"
            + "import net.minecraft.world.entity.Entity;\n"
            + "import net.minecraft.world.level.Level;\n"
            + "import net.minecraft.core.BlockPos;\n"
            + "import net.minecraft.world.phys.Vec3;\n"
            + "import net.minecraft.network.chat.Component;\n"
            + "import java.util.*;\n"
            + "public class " + WRAPPER_CLASS + " implements JvmCodeAction {\n"
            + "    public String run() throws Throwable {\n";

    private static final String WRAPPER_SUFFIX = "\n    }\n}\n";

    @Override
    public boolean isAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    @Override
    public JvmCodeCompileResult compile(String methodBody) {
        if (methodBody == null || methodBody.isBlank()) {
            return new JvmCodeCompileResult(null, "Empty code body.");
        }
        if (methodBody.length() > MAX_SOURCE_LENGTH) {
            return new JvmCodeCompileResult(null, "Code body too long (max " + MAX_SOURCE_LENGTH + " chars).");
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new JvmCodeCompileResult(null, "No Java compiler available; this requires a JDK runtime.");
        }

        String source = WRAPPER_PREAMBLE + methodBody + WRAPPER_SUFFIX;
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        try (InMemoryFileManager fileManager = new InMemoryFileManager(standardFileManager)) {
            JavaFileObject sourceFile = new StringSourceFile(WRAPPER_QUALIFIED, source);
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path", ""),
                    "-proc:none"
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, List.of(sourceFile));
            boolean succeeded = Boolean.TRUE.equals(task.call());
            if (!succeeded) {
                return new JvmCodeCompileResult(null, formatDiagnostics(diagnostics));
            }

            try {
                Class<?> clazz = fileManager.createClassLoader().loadClass(WRAPPER_QUALIFIED);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof JvmCodeAction action) {
                    return new JvmCodeCompileResult(action, null);
                }
                return new JvmCodeCompileResult(null, "Compiled class does not implement JvmCodeAction.");
            } catch (Throwable error) {
                return new JvmCodeCompileResult(null, "Failed to load generated class: " + error);
            }
        } catch (Throwable error) {
            return new JvmCodeCompileResult(null, "Compiler failure: " + error);
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("line ").append(diagnostic.getLineNumber())
                    .append(": ").append(diagnostic.getMessage(Locale.ROOT));
        }
        String formatted = builder.toString().trim();
        return formatted.isEmpty() ? "Compilation failed with no diagnostics." : formatted;
    }

    /** 内存文件管理器：拦截 .class 输出，把字节存进 Map。 */
    private static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, byte[]> classBytes = new HashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            return new ClassOutputFile(className, this.classBytes);
        }

        ClassLoader createClassLoader() {
            return new MemoryClassLoader(this.classBytes, JvmCodeAction.class.getClassLoader());
        }
    }

    /** 内存中源码文件对象。 */
    private static final class StringSourceFile extends SimpleJavaFileObject {
        private final String source;

        StringSourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.source;
        }
    }

    /** 内存中 .class 输出文件对象：close 时把字节写入共享 Map。 */
    private static final class ClassOutputFile extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, byte[]> classBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        ClassOutputFile(String className, Map<String, byte[]> classBytes) {
            super(URI.create("mem:///" + className.replace('.', '/') + ".class"), JavaFileObject.Kind.CLASS);
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        public OutputStream openOutputStream() {
            return new FilterOutputStream(this.output) {
                @Override
                public void close() throws IOException {
                    super.close();
                    ClassOutputFile.this.classBytes.put(className, output.toByteArray());
                }
            };
        }
    }

    /** 从内存字节加载类的 ClassLoader，父加载器是契约接口的加载器（保证同一实例）。 */
    private static final class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> classBytes;

        MemoryClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
            super(parent);
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = this.classBytes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
