package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobBrain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedMobCombatEvents {
    private AwakenedMobCombatEvents() {
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewTarget() instanceof Player player)) {
            return;
        }

        if (AwakenedMobBrain.shouldSuppressAggro(mob, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            AwakenedMobBrain.handlePlayerAttack(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (mob.getTarget() instanceof Player player && AwakenedMobBrain.shouldSuppressAggro(mob, player)) {
            mob.setTarget(null);
        }
    }
}
