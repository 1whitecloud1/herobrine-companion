package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class MaintainerStateDefinition implements HeroMindStateDefinition {

    private static final String NO_TASK_KEY = "MindMaintainerNoTaskTicks";
    private static final float DIRECT_ATTACK_EXIT_TO_JUDGE_MIN = 0.20f;

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.MAINTAINER;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.entropyScore() >= 0.35f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        if (snapshot.directAttackScore() >= DIRECT_ATTACK_EXIT_TO_JUDGE_MIN
                && snapshot.annoyanceWeight() >= 0.55f) {
            return SimpleNeuralNetwork.MindState.JUDGE;
        }
        if (snapshot.entropyScore() <= 0.20f) return SimpleNeuralNetwork.MindState.OBSERVER;
        if (hero != null && hero.getPersistentData().getInt(NO_TASK_KEY) >= 600) return SimpleNeuralNetwork.MindState.OBSERVER;
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

        ServerLevel level = (ServerLevel) hero.level();
        boolean acted = false;
        int fire = HeroStateBehaviorSupport.extinguishNearbyFire(level, owner.blockPosition(), 16, 3, 5, 32);
        if (fire > 0) {
            acted = true;
        }
        int lava = HeroStateBehaviorSupport.clearNearbyLava(level, owner.blockPosition(), 16, 4, 6, 24);
        if (lava > 0) {
            acted = true;
        }
        if (HeroStateBehaviorSupport.clearAnomalyFire(level, hero.blockPosition(), 6)) {
            acted = true;
        }
        if (Config.heroCleanItems && cleanupItems(hero, owner)) {
            acted = true;
        }

        int noTask = hero.getPersistentData().getInt(NO_TASK_KEY);
        hero.getPersistentData().putInt(NO_TASK_KEY, acted ? 0 : noTask + 10);
        if (acted) {
            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.HAPPY_VILLAGER, hero.position().add(0.0D, 1.0D, 0.0D), 6, 0.3D);
        }
    }
    @Override
    public void tickServerMovement(HeroEntity hero) {
        ServerPlayer owner = HeroStateBehaviorSupport.getOwner(hero);
        if (owner == null) return;

        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.keepDistance(hero, owner, 4.0D, 20.0D, 1.0D, 1.1D);
    }
    private boolean cleanupItems(HeroEntity hero, ServerPlayer owner) {
        List<ItemEntity> items = HeroStateBehaviorSupport.getNearbyItems(hero, owner, 20.0D);
        boolean cleaned = false;
        int removed = 0;
        for (ItemEntity item : items) {
            if (removed >= 12) break;
            if (item.getAge() < 1200 || isProtected(item.getItem())) continue;
            item.discard();
            removed++;
            cleaned = true;
        }
        return cleaned;
    }

    private boolean isProtected(ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_NAME)|| stack.isEnchanted()) return true;
        return stack.is(Items.DIAMOND) || stack.is(Items.NETHERITE_INGOT) || stack.is(Items.TOTEM_OF_UNDYING);
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.HAPPY_VILLAGER, 4, 0.8D, 0.5D, 1.0D);
    }
}
