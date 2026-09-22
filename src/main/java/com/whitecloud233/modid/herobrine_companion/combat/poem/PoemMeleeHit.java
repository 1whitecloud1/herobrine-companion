package com.whitecloud233.modid.herobrine_companion.combat.poem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** A scoped vanilla attack: every target in one cut receives the same stored attack charge. */
public final class PoemMeleeHit {
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();
    private PoemMeleeHit() { }

    public static void attack(ServerPlayer player, Entity target, float strength) {
        Hit previous = CURRENT.get();
        Entity damageTarget = target instanceof PartEntity<?> part ? part.getParent() : target;
        int invulnerableTime = damageTarget.invulnerableTime;
        CURRENT.set(new Hit(player, target, strength));
        try {
            // Authored, separate cuts can hit again; the controller deduplicates each individual cut.
            damageTarget.invulnerableTime = 0;
            player.attack(target);
        } finally {
            // A cancelled/blocked hit must not strip immunity left by an earlier damage source.
            if (damageTarget.invulnerableTime == 0) damageTarget.invulnerableTime = invulnerableTime;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean allows(Player player, Entity target) {
        Hit hit = CURRENT.get();
        return hit != null && hit.player == player && hit.target == target;
    }

    public static float strength(Player player) {
        Hit hit = CURRENT.get();
        return hit != null && hit.player == player ? hit.strength : -1;
    }

    public static boolean suppressVanillaSweep(ItemStack stack) {
        Hit hit = CURRENT.get();
        return hit != null && hit.player.getMainHandItem() == stack;
    }

    private record Hit(Player player, Entity target, float strength) { }
}
