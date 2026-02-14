package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.GlitchEchoEntity;
import com.whitecloud233.herobrine_companion.entity.GlitchVillagerEntity;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.VoidRiftEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
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
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class ModEvents {

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

    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(HERO.get(), HeroEntity.createAttributes().build());
        event.put(GHOST_CREEPER.get(), Creeper.createAttributes().build());
        event.put(GHOST_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(GHOST_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(GHOST_STEVE.get(), GhostSteveEntity.createAttributes().build());
        event.put(GLITCH_VILLAGER.get(), Villager.createAttributes().build());
        // Glitch Echo and Void Rift don't need attributes as they are just Entities, not LivingEntities
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GHOST_CREEPER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        
        event.register(GHOST_ZOMBIE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        
        event.register(GHOST_SKELETON.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);

        // [修改] 使用 WORLD_SURFACE 而不是 MOTION_BLOCKING_NO_LEAVES，以确保在地面生成
        event.register(GHOST_STEVE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.WORLD_SURFACE,
                GhostSteveEntity::checkGhostSteveSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        // Logic removed
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            // 检查玩家是否拥有特定的 Tag
            if (player.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                if (!player.hasEffect(MobEffects.NIGHT_VISION) || player.getEffect(MobEffects.NIGHT_VISION).getDuration() < 220) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
                }
            }
            
            // 检查飞行权限
            if (player.getTags().contains("herobrine_companion.transcendence_permit_active")) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                // 在死亡瞬间（物品栏被清空前）保存物品栏数据到 NBT
                ListTag inventoryTag = new ListTag();
                player.getInventory().save(inventoryTag);
                
                // 假设 getPersistentData() 可用（根据 grep 结果）
                // 如果不可用，需要使用其他方式存储，例如 DataAttachments
                CompoundTag data = player.getPersistentData();
                data.put("SoulBoundInventory", inventoryTag);
                data.putFloat("SoulBoundXP", player.experienceProgress);
                data.putInt("SoulBoundLevel", player.experienceLevel);
                data.putInt("SoulBoundTotalXP", player.totalExperience);
            }
        }
    }
    
    @SubscribeEvent
    public static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true); // 取消掉落物实体的生成
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player original = event.getOriginal();
            Player newPlayer = event.getEntity();
            
            // 恢复幽邃之视状态
            if (original.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                newPlayer.addTag("herobrine_companion.abyssal_gaze_active");
            }

            // 恢复飞行权限状态
            if (original.getTags().contains("herobrine_companion.transcendence_permit_active")) {
                newPlayer.addTag("herobrine_companion.transcendence_permit_active");
            }

            if (original.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                // 恢复灵魂绑定状态
                newPlayer.addTag("herobrine_companion.soul_bound_pact_active");

                // 从旧玩家的 NBT 中恢复数据
                CompoundTag data = original.getPersistentData();
                if (data.contains("SoulBoundInventory")) {
                    ListTag inventoryTag = data.getList("SoulBoundInventory", 10);
                    newPlayer.getInventory().load(inventoryTag);
                    
                    // 恢复经验
                    if (data.contains("SoulBoundXP")) {
                        newPlayer.experienceProgress = data.getFloat("SoulBoundXP");
                        newPlayer.experienceLevel = data.getInt("SoulBoundLevel");
                        newPlayer.totalExperience = data.getInt("SoulBoundTotalXP");
                    }
                    
                    // 清除保存的数据，防止重复
                    data.remove("SoulBoundInventory");
                    data.remove("SoulBoundXP");
                    data.remove("SoulBoundLevel");
                    data.remove("SoulBoundTotalXP");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        // 检查爆炸源是否为 RealmBreakerLightningEntity
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof RealmBreakerLightningEntity lightningEntity) {
            // 从受影响的实体列表中移除 HeroEntity
            event.getAffectedEntities().removeIf(entity -> entity instanceof HeroEntity);
            
            // 从受影响的实体列表中移除发射者 (Owner)
            Entity owner = lightningEntity.getOwner();
            if (owner != null) {
                event.getAffectedEntities().remove(owner);
            }
        }
    }
}
