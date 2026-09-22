package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Inspect the installed EF binary without initializing the Minecraft client. */
public final class PoemControlEngineCheck {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (var input = PoemControlEngineCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing EF input class: " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    public static void main(String[] args) throws Exception {
        var engine = read("yesman/epicfight/client/events/engine/ControlEngine");
        for (String name : List.of("attackLightPressToggle", "weaponInnatePressToggle", "weaponInnatePressCounter")) {
            String type = name.endsWith("Counter") ? "I" : "Z";
            check(engine.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(type)), "Missing shadow: " + name);
        }
        for (String name : List.of("handleEpicFightKeyMappings", "maybeAttack", "handleSeparateWeaponInnateSkill", "maybeGuard", "releaseAllServedKeys")) {
            check(engine.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals("()V")), "Missing input hook: " + name);
        }
        var handle = engine.methods.stream().filter(m -> m.name.equals("handleEpicFightKeyMappings")).findFirst().orElseThrow();
        boolean readsLight = false, castsCombo = false, nativeBuffer = false, comboSlot = false;
        for (var instruction : handle.instructions) {
            if (instruction instanceof FieldInsnNode f) {
                readsLight |= f.getOpcode() == Opcodes.GETFIELD && f.name.equals("attackLightPressToggle");
                comboSlot |= f.owner.equals("yesman/epicfight/skill/SkillSlots")
                        && (f.name.equals("COMBO_ATTACKS") || f.name.equals("BASIC_ATTACK"));
            } else if (instruction instanceof MethodInsnNode m) {
                castsCombo |= m.name.equals("sendCastRequest");
                nativeBuffer |= m.name.equals("reserveKey");
            }
        }
        check(readsLight && comboSlot && castsCombo && nativeBuffer, "The native light/reservation path changed");
        var keys = read("yesman/epicfight/client/input/EpicFightKeyMappings");
        for (String name : List.of("ATTACK", "WEAPON_INNATE_SKILL", "GUARD")) {
            check(keys.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals("Lnet/minecraft/client/KeyMapping;")),
                    "Missing separately registered EF key mapping: " + name);
        }
        var mixin = read("com/whitecloud233/herobrine_companion/mixin/epicfight/PoemAttackControlMixin");
        check(mixin.invisibleAnnotations != null && mixin.invisibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Pseudo;")),
                "Client input mixin must remain optional when EF is absent");
        System.out.println("POEM_CONTROL_ENGINE_OK: " + checks + " checks; installed EF fields/hooks; native light buffer; optional client mixin");
    }
}
