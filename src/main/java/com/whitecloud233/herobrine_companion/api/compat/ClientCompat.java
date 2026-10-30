package com.whitecloud233.herobrine_companion.api.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client-only input hooks; never referenced by common/server initialization. */
public final class ClientCompat {
    private ClientCompat() { }

    public static KeyMapping epicFightKey(String action) {
        Object result = OptionalCompat.call("epicfight.HeroEpicFightKeys", action, new Class<?>[0]);
        return result instanceof KeyMapping mapping ? mapping
                : action.equals("attack") ? Minecraft.getInstance().options.keyAttack : Minecraft.getInstance().options.keyUse;
    }
}
