package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.*;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HerobrineCompanion.MODID);

    public static final RegistryObject<EntityType<HeroEntity>> HERO = ENTITY_TYPES.register("hero",
            () -> EntityType.Builder.of(HeroEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F) // Normal player size
                    .build("hero"));

    public static final RegistryObject<EntityType<GhostCreeperEntity>> GHOST_CREEPER = ENTITY_TYPES.register("ghost_creeper",
            () -> EntityType.Builder.of(GhostCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .build("ghost_creeper"));

    public static final RegistryObject<EntityType<GhostZombieEntity>> GHOST_ZOMBIE = ENTITY_TYPES.register("ghost_zombie",
            () -> EntityType.Builder.of(GhostZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .build("ghost_zombie"));

    public static final RegistryObject<EntityType<GhostSkeletonEntity>> GHOST_SKELETON = ENTITY_TYPES.register("ghost_skeleton",
            () -> EntityType.Builder.of(GhostSkeletonEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .build("ghost_skeleton"));

    public static final RegistryObject<EntityType<GhostSteveEntity>> GHOST_STEVE = ENTITY_TYPES.register("ghost_steve",
            () -> EntityType.Builder.of(GhostSteveEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .build("ghost_steve"));

    public static final RegistryObject<EntityType<GlitchEchoEntity>> GLITCH_ECHO = ENTITY_TYPES.register("glitch_echo",
            () -> EntityType.Builder.of(GlitchEchoEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .fireImmune()
                    .noSummon()
                    .build("glitch_echo"));

    public static final RegistryObject<EntityType<RealmBreakerLightningEntity>> REALM_BREAKER_LIGHTNING = ENTITY_TYPES.register("realm_breaker_lightning",
            () -> EntityType.Builder.<RealmBreakerLightningEntity>of(RealmBreakerLightningEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("realm_breaker_lightning"));

    public static final RegistryObject<EntityType<VoidRiftEntity>> VOID_RIFT = ENTITY_TYPES.register("void_rift",
            () -> EntityType.Builder.<VoidRiftEntity>of(VoidRiftEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F) // [修改] 增大碰撞箱以匹配视觉
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("void_rift"));

    public static final RegistryObject<EntityType<GlitchVillagerEntity>> GLITCH_VILLAGER = ENTITY_TYPES.register("glitch_villager",
            () -> EntityType.Builder.of(GlitchVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("glitch_villager"));

    @SubscribeEvent
    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(HERO.get(), HeroEntity.createAttributes().build());
        event.put(GHOST_CREEPER.get(), Creeper.createAttributes().build());
        event.put(GHOST_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(GHOST_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(GLITCH_VILLAGER.get(), Villager.createAttributes().build());
        event.put(GHOST_STEVE.get(), GhostSteveEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        // [修改] 使用 WORLD_SURFACE 而不是 MOTION_BLOCKING_NO_LEAVES，以确保在地面生成
        event.register(GHOST_STEVE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.WORLD_SURFACE,
                GhostSteveEntity::checkGhostSteveSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
