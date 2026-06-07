package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class ProtectorStateDefinition implements HeroMindStateDefinition {
    private static final float DIRECT_ATTACK_EXIT_TO_JUDGE_MIN = 0.20f;

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.PROTECTOR;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.respectWeight() >= 0.65f
                && snapshot.entropyScore() < 0.55f
                && snapshot.annoyanceWeight() < 0.45f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        if (snapshot.directAttackScore() >= DIRECT_ATTACK_EXIT_TO_JUDGE_MIN
                && snapshot.annoyanceWeight() >= 0.55f) {
            return SimpleNeuralNetwork.MindState.JUDGE;
        }
        if (snapshot.entropyScore() >= 0.55f) return SimpleNeuralNetwork.MindState.MAINTAINER;
        if (snapshot.respectWeight() < 0.50f) return SimpleNeuralNetwork.MindState.OBSERVER;
        return null;
    }

    @Override
    public int minDwellTicks() {
        return 1800;
    }

    @Override
    public void tickServerSupport(HeroEntity hero) {
        ServerPlayer owner = HeroStateBehaviorSupport.getOwner(hero);
        if (owner == null) return;

        if (hero.tickCount % 180 == 0 && hero.distanceToSqr(owner) <= 32.0D * 32.0D) {
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 3600, 1, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 3600, 1, false, true));
        }

        if (owner.isOnFire()) {
            owner.clearFire();
        }
        if (owner.getAirSupply() < owner.getMaxAirSupply() / 2) {
            owner.setAirSupply(Math.min(owner.getMaxAirSupply(), owner.getAirSupply() + 30));
        }

        LivingEntity attacker = HeroStateBehaviorSupport.getRecentAttacker(owner, 80);
        if (attacker != null) {
            HeroStateBehaviorSupport.lookAtPos(hero, attacker.position().add(0.0D, attacker.getBbHeight() * 0.6D, 0.0D));
            attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, true));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
            if (HeroStateBehaviorSupport.isLowHealth(owner, 0.35D)) {
                owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true));
            }
        }

        if (hero.tickCount % 60 == 0) {
            ServerLevel level = (ServerLevel) hero.level();
            if (HeroStateBehaviorSupport.hasNearbyHostiles(hero, owner, 16.0D)) {
                HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.HAPPY_VILLAGER, owner.position().add(0.0D, 1.0D, 0.0D), 6, 0.4D);
            }
        }
    }
    @Override
    public void tickServerMovement(HeroEntity hero) {
        ServerPlayer owner = HeroStateBehaviorSupport.getOwner(hero);
        if (owner == null) return;

        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.stopAndLookAt(hero, owner);
        HeroStateBehaviorSupport.keepDistance(hero, owner, 3.0D, HeroStateBehaviorSupport.isNight(hero) ? 4.0D : 6.0D, 0.9D, 1.0D);

        LivingEntity attacker = HeroStateBehaviorSupport.getRecentAttacker(owner, 80);
        if (attacker != null && HeroStateBehaviorSupport.isLowHealth(owner, 0.35D)) {
            HeroStateBehaviorSupport.moveNearPlayer(hero, owner, 2.0D, 4.0D, 1.2D);
        }
    }
    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.HAPPY_VILLAGER, 4, 1.2D, 0.6D, 1.6D);
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.END_ROD, 8, 0.8D, 1.0D, 0.8D);
    }
}
