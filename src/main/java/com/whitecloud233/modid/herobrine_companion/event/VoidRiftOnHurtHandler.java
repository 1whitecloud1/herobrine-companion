package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.VoidRiftEntity;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class VoidRiftOnHurtHandler {

    private VoidRiftOnHurtHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        if (event.isCanceled() || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0.0F) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        // Only a direct melee hit may open a rift. Skill/projectile damage must
        // never feed back into this listener, including the rift's own pulses.
        if (event.getSource().getDirectEntity() != attacker ||
                !(event.getSource().is(DamageTypes.PLAYER_ATTACK) ||
                        event.getSource().is(DamageTypes.MOB_ATTACK) ||
                        event.getSource().is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof PoemOfTheEndItem poem) ||
                poem.getMode(weapon) != PoemOfTheEndItem.MODE_VOID_SHATTER) {
            return;
        }

        long now = level.getGameTime();
        var state = attacker.getPersistentData();
        if ((state.contains("PoemLastRiftTick") && now >= state.getLong("PoemLastRiftTick") &&
                now - state.getLong("PoemLastRiftTick") < 10) ||
                (attacker instanceof Player player && player.getCooldowns().isOnCooldown(poem))) {
            return;
        }
        // Reserve before spawning: sweeping/multi-target attacks share one rift.
        state.putLong("PoemLastRiftTick", now);
        if (attacker instanceof Player player) {
            player.getCooldowns().addCooldown(poem, 10);
        }
        spawnRift(level, target, attacker);
    }

    private static void spawnRift(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        Vec3 dir = target.position().subtract(attacker.position());
        if (dir.lengthSqr() < 0.0001D) {
            dir = target.getLookAngle();
        }

        dir = dir.normalize();

        double surfaceOffset = Math.max(target.getBbWidth() * 0.42D, 0.28D);
        double x = target.getX() - dir.x * surfaceOffset;
        double y = target.getY() + target.getBbHeight() * 0.62D;
        double z = target.getZ() - dir.z * surfaceOffset;

        VoidRiftEntity rift = new VoidRiftEntity(level, x, y, z, attacker.getUUID());
        level.addFreshEntity(rift);
    }
}
