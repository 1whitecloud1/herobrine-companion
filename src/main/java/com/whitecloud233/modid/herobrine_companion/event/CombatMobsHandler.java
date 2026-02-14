package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatMobsHandler {

    private static final String PEACEFUL_TAG = "herobrine_companion_peaceful";

    @SubscribeEvent
    public static void onMobTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player) {

            if (player.getTags().contains(PEACEFUL_TAG)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            
            if (event.getTarget() instanceof LivingEntity) {
                
                if (player.getTags().contains(PEACEFUL_TAG)) {
                    
                    player.removeTag(PEACEFUL_TAG);
                    
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_broken"));
                    
                    player.level().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);

                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0)); 
                    
                    double range = 20.0D;
                    for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
                        if (mob.getTarget() == null) {
                            mob.setTarget(player);
                        }
                    }
                }
            }
        }
    }
}
