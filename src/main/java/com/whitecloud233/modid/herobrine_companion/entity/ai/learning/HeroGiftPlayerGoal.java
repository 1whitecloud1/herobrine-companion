package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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
        this.cooldown = 1500;
    }

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getOwnerUUID() == null) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (!canGiftInState(state)) return false;

        int chance = 10;
        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            chance = 3;
        } else if (state == SimpleNeuralNetwork.MindState.MONSTER_KING) {
            chance = 5;
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
        this.hero.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;
        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 30.0F);
        this.tickCounter++;

        if (this.tickCounter == 20) {
            giveGift();
        }
    }

    private void giveGift() {
        ItemStack gift = selectGift();
        if (gift.isEmpty()) return;

        Vec3 lookVec = this.hero.getLookAngle();
        ItemEntity itemEntity = new ItemEntity(
                this.hero.level(),
                this.hero.getX() + lookVec.x,
                this.hero.getY() + 1.0D,
                this.hero.getZ() + lookVec.z,
                gift
        );

        Vec3 throwVec = this.owner.position().subtract(this.hero.position()).normalize().scale(0.3D);
        itemEntity.setDeltaMovement(throwVec.x, 0.2D, throwVec.z);
        itemEntity.setDefaultPickUpDelay();
        this.hero.level().addFreshEntity(itemEntity);

        if (this.owner instanceof ServerPlayer serverPlayer) {
            HeroDialogueHandler.onGift(this.hero, serverPlayer);
        }
    }

    private ItemStack selectGift() {
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (!canGiftInState(state)) return ItemStack.EMPTY;
        double roll = this.hero.getRandom().nextDouble();

        if (state == SimpleNeuralNetwork.MindState.MONSTER_KING) {
            if (roll < 0.3D) return new ItemStack(Items.ROTTEN_FLESH, 8);
            if (roll < 0.6D) return new ItemStack(Items.BONE, 4);
            if (roll < 0.8D) return new ItemStack(Items.GUNPOWDER, 4);
            return new ItemStack(Items.ENDER_PEARL, 2);
        }

        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            if (roll < 0.2D) return new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 1);
            if (roll < 0.5D) return new ItemStack(Items.DIAMOND, 2);
            return new ItemStack(Items.GOLDEN_APPLE, 1);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void stop() {
        this.owner = null;
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            this.cooldown = 6000;
        } else {
            this.cooldown = 12000 + this.hero.getRandom().nextInt(12000);
        }
    }

    private boolean canGiftInState(SimpleNeuralNetwork.MindState state) {
        return state == SimpleNeuralNetwork.MindState.PROTECTOR
                || state == SimpleNeuralNetwork.MindState.MONSTER_KING;
    }
}
