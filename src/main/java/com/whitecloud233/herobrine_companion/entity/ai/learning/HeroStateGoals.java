package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
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
        // 不中断移动或看人等其他基础行为
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        // 只要没在交易，这个总管家 Goal 就一直处于激活准备状态
        return hero.getTradingPlayer() == null;
    }

    @Override
    public void tick() {
        // 统一控制：每秒 (20 tick) 只执行一次逻辑判定，保护 TPS
        if (hero.tickCount % 20 != 0) return;

        // 获取当前心智状态，直接使用 switch 分发逻辑
        switch (hero.getHeroBrain().getState()) {

            case OBSERVER -> {
                if (hero.getRandom().nextFloat() < 0.05f) {
                    hero.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
                }
            }

            case PROTECTOR -> {
                if (hero.tickCount % 100 == 0 && hero.getOwnerUUID() != null) {
                    if (hero.level().getPlayerByUUID(hero.getOwnerUUID()) instanceof ServerPlayer p) {
                        if (p.distanceToSqr(hero) < 64 * 64) {
                            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, false));
                            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false));
                        }
                    }
                }
            }

            case JUDGE -> {
                if (hero.getRandom().nextFloat() < 0.01f) {
                    int offsetX = hero.getRandom().nextInt(30) - 15;
                    int offsetZ = hero.getRandom().nextInt(30) - 15;
                    if (Math.abs(offsetX) < 5) offsetX = (offsetX < 0 ? -5 : 5);
                    if (Math.abs(offsetZ) < 5) offsetZ = (offsetZ < 0 ? -5 : 5);

                    BlockPos pos = hero.blockPosition().offset(offsetX, 0, offsetZ);
                    if (hero.level().getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 3, false) == null) {
                        Entity lightning = EntityType.LIGHTNING_BOLT.create(hero.level());
                        if (lightning != null) {
                            lightning.moveTo(pos.getX(), pos.getY(), pos.getZ());
                            hero.level().addFreshEntity(lightning);
                        }
                    }
                }
            }

            case PRANKSTER -> {
                if (hero.getRandom().nextFloat() < 0.2f) {
                    ((ServerLevel) hero.level()).sendParticles(ParticleTypes.WITCH, hero.getX(), hero.getY() + 1, hero.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                }
            }

            case MAINTAINER -> {
                if (!com.whitecloud233.herobrine_companion.config.Config.heroCleanItems) return;
                if (hero.tickCount % 100 == 0) {
                    ServerLevel level = (ServerLevel) hero.level();
                    List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, hero.getBoundingBox().inflate(32));

                    if (items.size() > 5) {
                        int clearedCount = 0;
                        for (ItemEntity itemEntity : items) {
                            if (itemEntity.getAge() < 1200 || isValuableItem(itemEntity.getItem())) continue;
                            level.sendParticles(ParticleTypes.PORTAL, itemEntity.getX(), itemEntity.getY() + 0.2, itemEntity.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                            itemEntity.discard();
                            clearedCount++;
                        }
                        if (clearedCount > 0 && hero.getOwnerUUID() != null) {
                            if (level.getPlayerByUUID(hero.getOwnerUUID()) instanceof ServerPlayer owner) {
                                HeroDialogueHandler.onCleanseArea(hero, owner);
                                hero.getHeroBrain().inputEntropy(hero.getOwnerUUID(), -0.5f);
                            }
                        }
                    }
                }
            }

            case GLITCH_LORD -> {
                if (hero.getRandom().nextFloat() < 0.3f) {
                    double x = hero.getX() + (hero.getRandom().nextDouble() - 0.5) * 10;
                    double y = hero.getY() + (hero.getRandom().nextDouble() - 0.5) * 5;
                    double z = hero.getZ() + (hero.getRandom().nextDouble() - 0.5) * 10;
                    ((ServerLevel) hero.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 5, 0.2, 0.2, 0.2, 0.5);
                }
            }

            case MONSTER_KING -> {
                if (hero.tickCount % 100 == 0) {
                    List<Monster> monsters = hero.level().getEntitiesOfClass(Monster.class, hero.getBoundingBox().inflate(16));
                    for (Monster m : monsters) {
                        m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                        m.setGlowingTag(true);
                    }
                }
            }

            case REMINISCING -> {
                if (hero.getRandom().nextFloat() < 0.001f) {
                    hero.level().playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
                }
            }
        }
    }

    private boolean isValuableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.isEnchanted()) return true;
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC || rarity == Rarity.RARE) return true;

        Item item = stack.getItem();
        return item == Items.DIAMOND || item == Items.DIAMOND_BLOCK || item == Items.NETHERITE_INGOT ||
                item == Items.NETHERITE_BLOCK || item == Items.NETHERITE_SCRAP || item == Items.NETHER_STAR ||
                item == Items.TOTEM_OF_UNDYING || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.BEACON;
    }
}