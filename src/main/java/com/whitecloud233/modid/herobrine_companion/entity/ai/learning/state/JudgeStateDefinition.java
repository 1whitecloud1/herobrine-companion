package com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

public final class JudgeStateDefinition implements HeroMindStateDefinition {

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.JUDGE;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.annoyanceWeight() >= 0.55f || snapshot.entropyScore() >= 0.65f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        return snapshot.annoyanceWeight() <= 0.35f && snapshot.entropyScore() <= 0.40f
                ? SimpleNeuralNetwork.MindState.OBSERVER
                : null;
    }

    @Override
    public int minDwellTicks() {
        return 2400;
    }

    @Override
    public void tickServer(HeroEntity hero) {
        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 32.0D);
        if (focus == null) return;

        ServerLevel level = (ServerLevel) hero.level();
        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.stopAndLookAt(hero, focus);

        double distSqr = hero.distanceToSqr(focus);
        if (distSqr < 100.0D) {
            BlockPos perch = HeroStateBehaviorSupport.findHighestNearbyPerch(level, focus.blockPosition(), 8, 14, 2, 6);
            if (perch != null) {
                HeroStateBehaviorSupport.teleportToPerch(hero, perch);
            } else {
                HeroStateBehaviorSupport.driftAway(hero, focus, 10.0D, 1.0D);
            }
        } else if (distSqr > 256.0D) {
            HeroStateBehaviorSupport.moveToOrbit(hero, focus, 12.0D, 0.7D, 180.0F);
        }

        if (hero.tickCount % 80 == 0) {
            focus.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
            focus.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, true));
        }

        if (hero.tickCount % 180 == 0) {
            level.playSound(null, focus.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.7F, 0.7F);
        }

        if (hero.tickCount % 240 == 0) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                BlockPos strike = focus.blockPosition().offset(hero.getRandom().nextInt(11) - 5, 0, hero.getRandom().nextInt(11) - 5);
                bolt.moveTo(strike.getX(), strike.getY(), strike.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.ELECTRIC_SPARK, 3, 1.5D, 0.2D, 2.0D);
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.SMOKE, 5, 1.3D, 0.7D, 1.3D);
    }
}
