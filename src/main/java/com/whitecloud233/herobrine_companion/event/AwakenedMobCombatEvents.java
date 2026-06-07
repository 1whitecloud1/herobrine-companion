package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobBrain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class AwakenedMobCombatEvents {
    private AwakenedMobCombatEvents() {
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            return;
        }

        if (AwakenedMobBrain.shouldSuppressAggro(mob, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            AwakenedMobBrain.handlePlayerAttack(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (mob.getTarget() instanceof Player player && AwakenedMobBrain.shouldSuppressAggro(mob, player)) {
            mob.setTarget(null);
        }
    }
}
