package com.whitecloud233.herobrine_companion.init;

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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, HerobrineCompanion.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<HeroEntity>> HERO = ENTITY_TYPES.register("hero",
            () -> EntityType.Builder.of(HeroEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F) // Normal player size
                    .build("hero"));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostCreeperEntity>> GHOST_CREEPER = ENTITY_TYPES.register("ghost_creeper",
            () -> EntityType.Builder.of(GhostCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .build("ghost_creeper"));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostZombieEntity>> GHOST_ZOMBIE = ENTITY_TYPES.register("ghost_zombie",
            () -> EntityType.Builder.of(GhostZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .build("ghost_zombie"));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostSkeletonEntity>> GHOST_SKELETON = ENTITY_TYPES.register("ghost_skeleton",
            () -> EntityType.Builder.of(GhostSkeletonEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .build("ghost_skeleton"));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostSteveEntity>> GHOST_STEVE = ENTITY_TYPES.register("ghost_steve",
            () -> EntityType.Builder.of(GhostSteveEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .build("ghost_steve"));

    public static final DeferredHolder<EntityType<?>, EntityType<GlitchEchoEntity>> GLITCH_ECHO = ENTITY_TYPES.register("glitch_echo",
            () -> EntityType.Builder.of(GlitchEchoEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F) // [关键修改] 缩小碰撞箱，使其更容易通过狭窄通道
                    .fireImmune()
                    .noSummon()
                    .build("glitch_echo"));

    public static final DeferredHolder<EntityType<?>, EntityType<RealmBreakerLightningEntity>> REALM_BREAKER_LIGHTNING = ENTITY_TYPES.register("realm_breaker_lightning",
            () -> EntityType.Builder.<RealmBreakerLightningEntity>of(RealmBreakerLightningEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("realm_breaker_lightning"));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidRiftEntity>> VOID_RIFT = ENTITY_TYPES.register("void_rift",
            () -> EntityType.Builder.<VoidRiftEntity>of(VoidRiftEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F) // [修改] 增大碰撞箱以匹配视觉
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("void_rift"));

    public static final DeferredHolder<EntityType<?>, EntityType<GlitchVillagerEntity>> GLITCH_VILLAGER = ENTITY_TYPES.register("glitch_villager",
            () -> EntityType.Builder.of(GlitchVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("glitch_villager"));

    public static final DeferredHolder<EntityType<?>, EntityType<CleaveBladeEntity>> CLEAVE_BLADE = ENTITY_TYPES.register("cleave_blade",
            () -> EntityType.Builder.<CleaveBladeEntity>of(CleaveBladeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(80) // 80格追踪范围，确保飞远了客户端也能看见
                    .updateInterval(1) // 每tick更新，确保运动平滑
                    .build("cleave_blade"));

    public static final DeferredHolder<EntityType<?>, EntityType<DestructionGodHerobrineEntity>> DESTRUCTION_GOD_HEROBRINE = ENTITY_TYPES.register("destruction_god_herobrine",
            () -> EntityType.Builder.of(DestructionGodHerobrineEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("destruction_god_herobrine"));
}
