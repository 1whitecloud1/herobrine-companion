package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.HeroMindStateRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.EnumSet;
import java.util.List;

/**
 * ⚡ 统一心智状态调度器 (Master Goal)
 * 拒绝类爆炸！将 8 个离散的 Goal 合并为 1 个状态机，极大地节约了内存和 CPU 开销。
 */
public class HeroStateGoals extends Goal {

    private final HeroEntity hero;

    public HeroStateGoals(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    public boolean canUse() {
        return hero.getTradingPlayer() == null && !hero.isCompanionMode();
    }

    @Override
    public boolean canContinueToUse() {
        return hero.getTradingPlayer() == null && !hero.isCompanionMode();
    }

    @Override
    public void tick() {
        if (hero.tickCount % 10 != 0) return;
        HeroMindStateRegistry.tickServer(hero);
    }
}
