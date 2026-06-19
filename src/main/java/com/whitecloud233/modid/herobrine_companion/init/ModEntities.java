package com.whitecloud233.modid.herobrine_companion.init;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import com.whitecloud233.modid.herobrine_companion.entity.*;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.CleaveBladeEntity;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.VoidRiftEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HerobrineCompanion.MODID);

    public static final RegistryObject<EntityType<HeroEntity>> HERO = ENTITY_TYPES.register("hero",
            () -> EntityType.Builder.of(HeroEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .build("hero"));

    public static final RegistryObject<EntityType<DestructionGodHerobrineEntity>> DESTRUCTION_GOD_HEROBRINE = ENTITY_TYPES.register("destruction_god_herobrine",
            () -> EntityType.Builder.of(DestructionGodHerobrineEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("destruction_god_herobrine"));

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

    public static final RegistryObject<EntityType<CleaveBladeEntity>> CLEAVE_BLADE = ENTITY_TYPES.register("cleave_blade",
            () -> EntityType.Builder.<CleaveBladeEntity>of(CleaveBladeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(80) // 80格追踪范围，确保飞远了客户端也能看见
                    .updateInterval(1) // 每tick更新，确保运动平滑
                    .build("cleave_blade"));

    public static final RegistryObject<EntityType<GlitchVillagerEntity>> GLITCH_VILLAGER = ENTITY_TYPES.register("glitch_villager",
            () -> EntityType.Builder.of(GlitchVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("glitch_villager"));
}
