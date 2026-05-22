package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class ReminiscingStateDefinition implements HeroMindStateDefinition {

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.REMINISCING;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.nostalgiaScore() >= 0.35f && snapshot.sorrowWeight() >= 0.30f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        return snapshot.nostalgiaScore() < 0.20f ? SimpleNeuralNetwork.MindState.OBSERVER : null;
    }

    @Override
    public int minDwellTicks() {
        return 2400;
    }

    @Override
    public void tickServer(HeroEntity hero) {
        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 20.0D);
        if (focus == null) return;

        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.keepDistance(hero, focus, 4.0D, 8.0D, 0.5D, 0.4D);
        hero.getNavigation().stop();

        if (hero.tickCount % 40 == 0) {
            HeroStateBehaviorSupport.stareAtSky(hero);
        }

        if (hero.tickCount % 120 == 0 && hero.getRandom().nextFloat() < 0.12F) {
            ServerLevel level = (ServerLevel) hero.level();
            level.playSound(null, hero.blockPosition(), SoundEvents.GHAST_AMBIENT, SoundSource.NEUTRAL, 0.25F, 0.5F);
        }

        if (hero.tickCount % 200 == 0 && hero.getRandom().nextFloat() < 0.06F) {
            ServerLevel level = (ServerLevel) hero.level();
            level.playSound(null, hero.blockPosition(), SoundEvents.CAVE_VINES_BREAK, SoundSource.AMBIENT, 0.35F, 0.7F);
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.SOUL, 5, 1.4D, 0.5D, 1.8D);
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.SNOWFLAKE, 5, 1.6D, 0.7D, 1.4D);
    }
}
