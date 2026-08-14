package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.CleaveBladeEntity;
import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.GlitchEchoEntity;
import com.whitecloud233.herobrine_companion.entity.GlitchVillagerEntity;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.VoidRiftEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class ModEvents {

    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HERO.get(), HeroEntity.createAttributes().build());
        event.put(ModEntities.GHOST_CREEPER.get(), Creeper.createAttributes().build());
        event.put(ModEntities.GHOST_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(ModEntities.GHOST_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(ModEntities.GHOST_STEVE.get(), GhostSteveEntity.createAttributes().build());
        event.put(ModEntities.GLITCH_VILLAGER.get(), Villager.createAttributes().build());
        event.put(ModEntities.DESTRUCTION_GOD_HEROBRINE.get(), DestructionGodHerobrineEntity.createAttributes().build());
        // Glitch Echo and Void Rift don't need attributes as they are just Entities, not LivingEntities
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // [修改] 使用 WORLD_SURFACE 而不是 MOTION_BLOCKING_NO_LEAVES，以确保在地面生成
        event.register(ModEntities.GHOST_STEVE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.WORLD_SURFACE,
                GhostSteveEntity::checkGhostSteveSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        // Logic removed
    }
}
