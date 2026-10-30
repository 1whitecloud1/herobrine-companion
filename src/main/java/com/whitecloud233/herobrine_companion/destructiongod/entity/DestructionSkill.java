package com.whitecloud233.herobrine_companion.destructiongod.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Stable wire ids and shared skill metadata. Static and casting trigger ticks intentionally differ. */
public enum DestructionSkill {
    NONE(0, 0, 0, 0, 20, null, 0F, 0F),
    WORLD_REND(1, 58, 30, 0, 150, () -> SoundEvents.TRIDENT_RIPTIDE_3.value(), 3.5F, .6F),
    APOCALYPSE_CRACK(2, 40, 18, 0, 90, () -> SoundEvents.GENERIC_EXPLODE.value(), 2.5F, .7F),
    WORLD_PEEL(3, 46, 22, 0, 120, () -> SoundEvents.ENDERMAN_TELEPORT, 2.2F, .45F),
    WORLD_COLLAPSE(4, 84, 30, 0, 220, () -> SoundEvents.WITHER_SPAWN, 4F, .5F),
    SCYTHE_ICHIMONJI(5, 34, 11, 14, 26, () -> SoundEvents.PLAYER_ATTACK_SWEEP, 2.3F, .6F),
    SCYTHE_REVERSE_MOON(6, 38, 14, 16, 44, () -> SoundEvents.ENDERMAN_SCREAM, 2.4F, .6F),
    SCYTHE_EXECUTION(7, 52, 28, 24, 110, () -> SoundEvents.TRIDENT_RETURN, 3F, .45F),
    DESTRUCTION_LIGHTNING(8, 68, 20, 22, 150, () -> SoundEvents.TRIDENT_THUNDER.value(), 3.2F, .55F),
    DESTRUCTION_GOD_ORB(9, 320, 42, 28, 150, () -> SoundEvents.WITHER_SPAWN, 4F, .45F),
    THUNDER_SKYNET(10, 200, 28, 36, 150, () -> SoundEvents.LIGHTNING_BOLT_THUNDER, 4.5F, .55F),
    SCYTHE_FAULT_SPLIT(11, 280, 120, 120, 150, () -> SoundEvents.WARDEN_SONIC_BOOM, 3.8F, .55F);

    private static final Map<Integer, DestructionSkill> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(DestructionSkill::id, skill -> skill));
    private final int id, duration, triggerTick, castingTriggerTick, cooldown;
    private final SoundCue sound;

    DestructionSkill(int id, int duration, int triggerTick, int castingTriggerTick, int cooldown,
                     Supplier<SoundEvent> sound, float volume, float pitch) {
        this.id = id;
        this.duration = duration;
        this.triggerTick = triggerTick;
        this.castingTriggerTick = castingTriggerTick;
        this.cooldown = cooldown;
        this.sound = sound == null ? null : new SoundCue(sound, volume, pitch);
    }

    public static DestructionSkill fromId(int id) { return BY_ID.getOrDefault(id, NONE); }
    public int id() { return this.id; }
    public int duration() { return this.duration; }
    public int triggerTick() { return this.triggerTick; }
    public int castingTriggerTick() { return this.castingTriggerTick; }
    public int cooldown() { return this.cooldown; }
    public SoundCue sound() { return this.sound; }

    public int duration(int phase) {
        return this == THUNDER_SKYNET ? this.triggerTick + 22 + 162 + Mth.clamp(phase, 1, 4) * 14 + 8 : this.duration;
    }

    public boolean isScythe() {
        return this == SCYTHE_ICHIMONJI || this == SCYTHE_REVERSE_MOON
                || this == SCYTHE_EXECUTION || this == SCYTHE_FAULT_SPLIT;
    }

    // Resolve registry-backed sounds only when playing, not while loading metadata on either side.
    public record SoundCue(Supplier<SoundEvent> event, float volume, float pitch) { }
}
