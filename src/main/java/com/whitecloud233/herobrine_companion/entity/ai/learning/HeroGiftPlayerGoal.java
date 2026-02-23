package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroGiftPlayerGoal extends Goal {
    private final HeroEntity hero;
    private Player owner;
    private int cooldown;
    private int tickCounter;

    public HeroGiftPlayerGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.cooldown = 1500; // 初始冷却 5分钟
    }

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getOwnerUUID() == null) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        // [深度学习] 根据心智状态调整频率
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        
        if (state == SimpleNeuralNetwork.MindState.JUDGE) {
            return false; // 审判者不送礼
        }
        
        int chance = 10; // 默认 1/10 (在冷却结束后)
        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            chance = 3; // 守护者：非常频繁
        } else if (state == SimpleNeuralNetwork.MindState.MONSTER_KING) {
            chance = 5; // 怪物之王：频繁送怪物掉落物
        }

        if (this.hero.getRandom().nextInt(chance) != 0) return false;

        this.owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        return this.owner != null && this.hero.distanceToSqr(this.owner) < 100.0D;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null && this.tickCounter < 60;
    }

    @Override
    public void start() {
        this.tickCounter = 0;
        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 30.0F);
        // 播放一个提示音效
        this.hero.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;
        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 30.0F);
        this.tickCounter++;

        if (this.tickCounter == 20) { // 1秒后扔出礼物
            giveGift();
        }
    }

    private void giveGift() {
        ItemStack gift = selectGift();
        if (gift.isEmpty()) return;

        Vec3 lookVec = this.hero.getLookAngle();
        ItemEntity itemEntity = new ItemEntity(this.hero.level(),
                this.hero.getX() + lookVec.x,
                this.hero.getY() + 1.0D,
                this.hero.getZ() + lookVec.z,
                gift);

        // 稍微给一点速度扔向玩家
        Vec3 throwVec = this.owner.position().subtract(this.hero.position()).normalize().scale(0.3);
        itemEntity.setDeltaMovement(throwVec.x, 0.2, throwVec.z);
        itemEntity.setDefaultPickUpDelay();

        this.hero.level().addFreshEntity(itemEntity);

        if (this.owner instanceof ServerPlayer serverPlayer) {
            HeroDialogueHandler.onGift(this.hero, serverPlayer);
        }
    }

    private ItemStack selectGift() {
        // [深度学习] 根据心智状态决定礼物类型
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        int trust = this.hero.getTrustLevel();
        double roll = this.hero.getRandom().nextDouble();

        // 1. 怪物之王：只送怪物掉落物
        if (state == SimpleNeuralNetwork.MindState.MONSTER_KING) {
            if (roll < 0.3) return new ItemStack(Items.ROTTEN_FLESH, 8);
            if (roll < 0.6) return new ItemStack(Items.BONE, 4);
            if (roll < 0.8) return new ItemStack(Items.GUNPOWDER, 4);
            return new ItemStack(Items.ENDER_PEARL, 2);
        }

        // 2. 守护者：送好东西
        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            if (roll < 0.2) return new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 1);
            if (roll < 0.5) return new ItemStack(Items.DIAMOND, 2);
            return new ItemStack(Items.GOLDEN_APPLE, 1);
        }
        
        // 3. 代码之神：送红石相关
        if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD) {
            if (roll < 0.3) return new ItemStack(Items.REDSTONE, 16);
            if (roll < 0.6) return new ItemStack(Items.OBSERVER, 2);
            return new ItemStack(Items.COMMAND_BLOCK, 1); // 极其稀有，或者只是展示一下
        }

        // 4. 默认逻辑 (基于信任度)
        if (trust > 70 && roll < 0.1) {
            return new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 1);
        } else if (trust > 50 && roll < 0.2) {
            return new ItemStack(Items.DIAMOND);
        } else if (trust > 20 && roll < 0.4) {
            return new ItemStack(Items.GOLD_INGOT, 2);
        } else {
            if (roll < 0.3) return new ItemStack(Items.ROTTEN_FLESH, 4);
            if (roll < 0.5) return new ItemStack(Items.BONE, 2);
            if (roll < 0.7) return new ItemStack(Items.GUNPOWDER, 2);
            if (roll < 0.85) return new ItemStack(Items.SPIDER_EYE, 2);
            return new ItemStack(Items.ENDER_PEARL);
        }
    }

    @Override
    public void stop() {
        this.owner = null;
        // 根据状态调整冷却
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            this.cooldown = 6000; // 5分钟
        } else {
            this.cooldown = 12000 + this.hero.getRandom().nextInt(12000); // 10-20分钟
        }
    }
}
