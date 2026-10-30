package com.whitecloud233.modid.herobrine_companion.compat.safety;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Inspects bytecode without defining optional classes or running their static initializers. */
public final class MixinContract {
    private MixinContract() { }

    public static String mismatch(ClassNode mixin, ClassNode target, Function<String, ClassNode> parents) {
        if (mixin == null || target == null) return "missing mixin or target bytecode";
        for (var field : mixin.fields) {
            if ((annotation(field.visibleAnnotations, "Shadow;") != null || annotation(field.invisibleAnnotations, "Shadow;") != null)
                    && !fieldExists(target, field.name, field.desc, parents, 0)) return "missing shadow field " + field.name + field.desc;
        }
        for (MethodNode method : mixin.methods) {
            if ((annotation(method.visibleAnnotations, "Shadow;") != null || annotation(method.invisibleAnnotations, "Shadow;") != null)
                    && methods(target, method.name + method.desc, parents, 0).isEmpty()) return "missing shadow method " + method.name + method.desc;
            for (AnnotationNode injection : annotations(method)) {
                if (!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || value(injection, "method") == null) continue;
                List<MethodNode> selected = new ArrayList<>();
                for (Object selector : list(value(injection, "method"))) selected.addAll(methods(target, selector.toString(), parents, 0));
                if (selected.isEmpty()) return "missing injection method " + value(injection, "method");
                if (injection.desc.endsWith("ModifyVariable;") && selected.stream().noneMatch(candidate -> candidate.desc.startsWith("(F"))) {
                    return "changed float animation argument";
                }
                for (Object atObject : list(value(injection, "at"))) {
                    if (!(atObject instanceof AnnotationNode at)) continue;
                    Object descriptor = value(at, "target");
                    if (descriptor == null) continue;
                    String kind = String.valueOf(value(at, "value"));
                    String reference = descriptor.toString();
                    if (kind.equals("INVOKE") && selected.stream().noneMatch(candidate -> calls(candidate, reference))) return "missing call " + reference;
                    if (kind.equals("FIELD") && selected.stream().noneMatch(candidate -> fields(candidate, reference))) return "missing field access " + reference;
                }
            }
        }
        return null;
    }

    private static boolean fieldExists(ClassNode type, String name, String descriptor, Function<String, ClassNode> parents, int depth) {
        if (type == null || depth > 8) return false;
        if (type.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor))) return true;
        return type.superName != null && fieldExists(parents.apply(type.superName), name, descriptor, parents, depth + 1);
    }

    private static List<MethodNode> methods(ClassNode type, String selector, Function<String, ClassNode> parents, int depth) {
        if (type == null || depth > 8) return List.of();
        int descriptorAt = selector.indexOf('(');
        String name = descriptorAt < 0 ? selector : selector.substring(0, descriptorAt);
        String descriptor = descriptorAt < 0 ? null : selector.substring(descriptorAt);
        List<MethodNode> found = type.methods.stream().filter(method -> method.name.equals(name)
                && (descriptor == null || method.desc.equals(descriptor))).toList();
        if (!found.isEmpty() || name.equals("<init>")) return found;
        return type.superName == null ? List.of() : methods(parents.apply(type.superName), selector, parents, depth + 1);
    }

    private static boolean calls(MethodNode method, String reference) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                String actual = "L" + call.owner + ";" + call.name + call.desc;
                if (actual.equals(reference)) return true;
                // Forge production uses SRG for vanilla symbols; the optional mod symbols stay unmapped.
                if (call.owner.equals("net/minecraft/world/entity/Entity") && call.name.equals("m_20275_")
                        && reference.equals("Lnet/minecraft/world/entity/Entity;distanceToSqr(DDD)D") && call.desc.equals("(DDD)D")) return true;
            }
        }
        return false;
    }

    private static boolean fields(MethodNode method, String reference) {
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && reference.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) return true;
        }
        return false;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    private static AnnotationNode annotation(List<AnnotationNode> values, String suffix) {
        return values == null ? null : values.stream().filter(value -> value.desc.endsWith(suffix)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static List<?> list(Object value) { return value instanceof List<?> list ? list : value == null ? List.of() : List.of(value); }
}
