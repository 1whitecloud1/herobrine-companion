package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

public class GhostSkeletonEntity extends Skeleton {
    public GhostSkeletonEntity(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "entities/ghost_skeleton"));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // Override to prevent default Skeleton drops (bones, arrows)
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, 
            (entity) -> !(entity instanceof GhostCreeperEntity) && !(entity instanceof GhostZombieEntity) && !(entity instanceof GhostSkeletonEntity)
        ));
    }
}
