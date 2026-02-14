package com.whitecloud233.modid.herobrine_companion.entity;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public class GhostCreeperEntity extends Creeper {
    public GhostCreeperEntity(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation getDefaultLootTable() {
        return new ResourceLocation(HerobrineCompanion.MODID, "entities/ghost_creeper");
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // Override to prevent default Creeper drops (gunpowder)
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, 
            (entity) -> !(entity instanceof GhostCreeperEntity) && !(entity instanceof GhostZombieEntity) && !(entity instanceof GhostSkeletonEntity)
        ));
    }
}
