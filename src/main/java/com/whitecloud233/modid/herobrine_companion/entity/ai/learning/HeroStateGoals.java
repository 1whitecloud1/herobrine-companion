package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

import net.minecraft.core.BlockPos;
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

public class HeroStateGoals {

    // ==========================================
    // 状态基类：处理 20-tick 间隔和交易拦截
    // ==========================================
    public static abstract class StateGoal extends Goal {
        protected final HeroEntity hero;
        private final MindState targetState;

        public StateGoal(HeroEntity hero, MindState targetState) {
            this.hero = hero;
            this.targetState = targetState;
            // 不中断移动或看人等其他基础行为
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            // 只有当精神状态匹配，且没有在交易时，Goal 才会激活
            return hero.getMindState() == targetState && hero.getTradingPlayer() == null;
        }

        @Override
        public void tick() {
            if (hero.tickCount % 20 == 0) {
                executeStateLogic();
            }
        }

        protected abstract void executeStateLogic();
    }

    // ==========================================
    // 1. 观察者 (Observer)
    // ==========================================
    public static class ObserverGoal extends StateGoal {
        public ObserverGoal(HeroEntity hero) { super(hero, MindState.OBSERVER); }
        @Override protected void executeStateLogic() {
            if (hero.getRandom().nextFloat() < 0.05f) {
                hero.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
            }
        }
    }

    // ==========================================
    // 2. 守护者 (Protector)
    // ==========================================
    public static class ProtectorGoal extends StateGoal {
        public ProtectorGoal(HeroEntity hero) { super(hero, MindState.PROTECTOR); }
        @Override protected void executeStateLogic() {
            if (hero.tickCount % 100 == 0 && hero.getOwnerUUID() != null) {
                ServerPlayer p = (ServerPlayer) hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (p != null && p.distanceToSqr(hero) < 64 * 64) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false));
                }
            }
        }
    }

    // ==========================================
    // 3. 审判官 (Judge)
    // ==========================================
    public static class JudgeGoal extends StateGoal {
        public JudgeGoal(HeroEntity hero) { super(hero, MindState.JUDGE); }
        @Override protected void executeStateLogic() {
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
    }

    // ==========================================
    // 4. 恶作剧者 (Prankster)
    // ==========================================
    public static class PranksterGoal extends StateGoal {
        public PranksterGoal(HeroEntity hero) { super(hero, MindState.PRANKSTER); }
        @Override protected void executeStateLogic() {
            if (hero.getRandom().nextFloat() < 0.2f) {
                ((ServerLevel) hero.level()).sendParticles(ParticleTypes.WITCH, hero.getX(), hero.getY() + 1, hero.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    // ==========================================
    // 5. 维护者 (Maintainer)
    // ==========================================
    public static class MaintainerGoal extends StateGoal {
        public MaintainerGoal(HeroEntity hero) { super(hero, MindState.MAINTAINER); }
        @Override protected void executeStateLogic() {
            if (!com.whitecloud233.modid.herobrine_companion.config.Config.heroCleanItems) return;

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
                        ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                        if (owner != null) {
                            HeroDialogueHandler.onCleanseArea(hero, owner);
                            hero.getHeroBrain().inputEntropy(hero.getOwnerUUID(), -0.5f);
                        }
                    }
                }
            }
        }

        private boolean isValuableItem(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (stack.hasCustomHoverName() || stack.isEnchanted()) return true;

            // 1.20.1 中获取物品稀有度的方式
            Rarity rarity = stack.getRarity();
            if (rarity == Rarity.EPIC || rarity == Rarity.RARE) return true;

            Item item = stack.getItem();
            return item == Items.DIAMOND || item == Items.DIAMOND_BLOCK || item == Items.NETHERITE_INGOT ||
                    item == Items.NETHERITE_BLOCK || item == Items.NETHERITE_SCRAP || item == Items.NETHER_STAR ||
                    item == Items.TOTEM_OF_UNDYING || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.BEACON;
        }
    }

    // ==========================================
    // 6. 乱码领主 (Glitch Lord)
    // ==========================================
    public static class GlitchLordGoal extends StateGoal {
        public GlitchLordGoal(HeroEntity hero) { super(hero, MindState.GLITCH_LORD); }
        @Override protected void executeStateLogic() {
            if (hero.getRandom().nextFloat() < 0.3f) {
                double x = hero.getX() + (hero.getRandom().nextDouble() - 0.5) * 10;
                double y = hero.getY() + (hero.getRandom().nextDouble() - 0.5) * 5;
                double z = hero.getZ() + (hero.getRandom().nextDouble() - 0.5) * 10;
                ((ServerLevel) hero.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 5, 0.2, 0.2, 0.2, 0.5);
            }
        }
    }

    // ==========================================
    // 7. 怪物之王 (Monster King)
    // ==========================================
    public static class MonsterKingGoal extends StateGoal {
        public MonsterKingGoal(HeroEntity hero) { super(hero, MindState.MONSTER_KING); }
        @Override protected void executeStateLogic() {
            if (hero.tickCount % 100 == 0) {
                List<Monster> monsters = hero.level().getEntitiesOfClass(Monster.class, hero.getBoundingBox().inflate(16));
                for (Monster m : monsters) {
                    m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                    m.setGlowingTag(true);
                }
            }
        }
    }

    // ==========================================
    // 8. 追忆者 (Reminiscing)
    // ==========================================
    public static class ReminiscingGoal extends StateGoal {
        public ReminiscingGoal(HeroEntity hero) { super(hero, MindState.REMINISCING); }
        @Override protected void executeStateLogic() {
            if (hero.getRandom().nextFloat() < 0.001f) {
                hero.level().playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
            }
        }
    }
}