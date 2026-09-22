package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.projectile.VoidRiftEntity;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class VoidRiftOnHurtHandler {
    private VoidRiftOnHurtHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Post event) {
        var target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        // Rift pulses keep the owner attribution, but are not direct melee hits.
        if (event.getSource().getDirectEntity() != player
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof PoemOfTheEndItem poem)
                || poem.getMode(weapon) != PoemOfTheEndItem.MODE_VOID_SHATTER) {
            return;
        }
        long now = level.getGameTime();
        var state = player.getPersistentData();
        if ((state.contains("PoemLastRiftTick") && now >= state.getLong("PoemLastRiftTick")
                && now - state.getLong("PoemLastRiftTick") < 10)
                || player.getCooldowns().isOnCooldown(poem)) {
            return;
        }
        // Reserve before spawning so sweeping and repeated hit callbacks share one rift.
        state.putLong("PoemLastRiftTick", now);
        player.getCooldowns().addCooldown(poem, 10);
        Vec3 direction = target.position().subtract(player.position());
        if (direction.lengthSqr() < 0.0001D) direction = target.getLookAngle();
        direction = direction.normalize();
        double offset = Math.max(target.getBbWidth() * 0.42D, 0.28D);
        level.addFreshEntity(new VoidRiftEntity(level,
                target.getX() - direction.x * offset,
                target.getY() + target.getBbHeight() * 0.62D,
                target.getZ() - direction.z * offset, player.getUUID()));
    }
}
