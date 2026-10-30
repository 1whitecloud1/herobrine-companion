package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import net.minecraft.client.KeyMapping;
import yesman.epicfight.client.input.EpicFightKeyMappings;

/** Loaded only by the client input hooks after the Epic Fight bridge is active. */
public final class HeroEpicFightKeys {
    private HeroEpicFightKeys() { }

    public static KeyMapping attack() { return EpicFightKeyMappings.ATTACK; }
    public static KeyMapping innate() { return EpicFightKeyMappings.WEAPON_INNATE_SKILL; }
    public static KeyMapping guard() { return EpicFightKeyMappings.GUARD; }
}
