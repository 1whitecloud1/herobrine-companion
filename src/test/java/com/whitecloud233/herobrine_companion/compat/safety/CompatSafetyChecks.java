package com.whitecloud233.herobrine_companion.compat.safety;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompatSafetyChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(CompatSafety.allowed("accessories"), "Default compatibility is available before a failure");
        System.setProperty("herobrine.compat.safeMode", "true");
        check(!CompatSafety.allowed("epicfight") && !CompatSafety.allowed("accessories"), "Safe mode disables every integration");
        System.clearProperty("herobrine.compat.safeMode");
        System.setProperty("herobrine.compat.accessories", "false");
        AtomicInteger attempts = new AtomicInteger();
        check(!CompatSafety.run("accessories", "disabled", attempts::incrementAndGet) && attempts.get() == 0, "Disabled integration is never called");
        System.clearProperty("herobrine.compat.accessories");
        check(!CompatSafety.run("accessories", "fixture failure", () -> { attempts.incrementAndGet(); throw new NoSuchMethodError("fixture"); }), "Linkage failure is isolated");
        check(!CompatSafety.run("accessories", "retry", attempts::incrementAndGet) && attempts.get() == 1, "Failed integration is not retried");
        check(CompatSafety.allowed("waveycapes"), "Other integrations remain enabled");
        check(CompatSafety.call("waveycapes", "fixture", () -> { throw new IllegalStateException("fixture"); }, 7) == 7, "Runtime failure returns fallback");
        try {
            CompatSafety.run("simplehats", "fatal", () -> { throw new OutOfMemoryError("fixture"); });
            throw new AssertionError("Fatal JVM error swallowed");
        } catch (OutOfMemoryError expected) { checks++; }
        ClassNode target = type("example/Target");
        target.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "value", "Z", null, null));
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "execute", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "example/Receiver", "action", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(method);
        ClassNode mixin = type("example/Mixin");
        FieldNode field = new FieldNode(Opcodes.ACC_PRIVATE, "value", "Z", null, null);
        field.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
        mixin.fields.add(field);
        MethodNode hook = new MethodNode(Opcodes.ACC_PRIVATE, "hook", "()V", null, null);
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = List.of("value", "INVOKE", "target", "Lexample/Receiver;action()V");
        AnnotationNode redirect = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;");
        redirect.values = List.of("method", List.of("execute()V"), "at", at);
        hook.visibleAnnotations = List.of(redirect);
        mixin.methods.add(hook);
        check(MixinContract.mismatch(mixin, target, ignored -> null) == null, "Matching bytecode passes preflight");
        method.name = "renamed";
        check(MixinContract.mismatch(mixin, target, ignored -> null).contains("missing injection"), "Renamed target is rejected");
        method.name = "execute";
        method.instructions.clear();
        check(MixinContract.mismatch(mixin, target, ignored -> null).contains("missing call"), "Changed injection site is rejected");
        target.fields.clear();
        check(MixinContract.mismatch(mixin, target, ignored -> null).contains("shadow field"), "Changed shadow field is rejected");
        check(CompatPreflight.inspect("epicfight", "999.0", true, ignored -> { throw new AssertionError("Unknown version bytecode must not load"); }) != null,
                "Unsupported versions are rejected before optional classes load");

        for (String id : List.of("accessories", "armourers_workshop", "epicfight", "simplehats")) {
            String version = switch (id) {
                case "accessories" -> "1.1.0-beta.53+1.21.1";
                case "armourers_workshop" -> "3.2.7-beta";
                case "epicfight" -> "21.17.2";
                default -> "0.4.0";
            };
            String issue = CompatPreflight.inspect(id, version, true, CompatSafetyChecks::read);
            check(issue == null, "Pinned " + id + " should pass bytecode preflight: " + issue);
        }
        System.out.println("COMPAT_SAFETY_OK: " + checks + " checks");
    }

    private static ClassNode read(String name) {
        try (InputStream input = CompatSafetyChecks.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static ClassNode type(String name) {
        ClassNode node = new ClassNode();
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
