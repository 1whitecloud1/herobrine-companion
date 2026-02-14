package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.GlitchVillagerSpawner;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroQuestHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroSpawner;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    // 创建一个静态的生成器实例
    private static final HeroSpawner spawner = new HeroSpawner();
    private static final GlitchVillagerSpawner glitchVillagerSpawner = new GlitchVillagerSpawner();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        // 只在服务端运行，且只针对 ServerLevel
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            // 调用你的生成器逻辑
            spawner.tick(serverLevel);
            glitchVillagerSpawner.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            Player player = event.player;
            if (player.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                // 持续给予夜视效果，时间短一点避免闪烁，但要足够长不至于断掉
                // 220 tick = 11秒，只要大于 tick 间隔即可
                // showIcon = false, visible = false (不显示粒子和图标)
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
            }
            
            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }

                // 持续一段时间强制恢复飞行状态，确保客户端同步
                if (player.getPersistentData().getBoolean("herobrine_companion.should_restore_flying")) {
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    
                    // 20 tick 后移除标记，给予足够的时间同步
                    if (player.tickCount > 20) {
                        player.getPersistentData().remove("herobrine_companion.should_restore_flying");
                    }
                }
            }

            if (player instanceof ServerPlayer serverPlayer) {
                HeroQuestHandler.tickPacifyQuest(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HeroQuestHandler.onEndermanInteract(player, event.getTarget(), event.getItemStack());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
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

                CompoundTag data = player.getPersistentData();
                data.put("SoulBoundInventory", inventoryTag);
                data.putFloat("SoulBoundXP", player.experienceProgress);
                data.putInt("SoulBoundLevel", player.experienceLevel);
                data.putInt("SoulBoundTotalXP", player.totalExperience);
                
                // 清空物品栏，防止掉落
                player.getInventory().clearContent();
            }
            
            // 保存飞行状态
            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                player.getPersistentData().putBoolean("herobrine_companion.transcendence_permit_flying", player.getAbilities().flying);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true); // 取消掉落物实体的生成
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true); // 取消经验球生成
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player original = event.getOriginal();
            Player newPlayer = event.getEntity();

            // 恢复 Abyssal Gaze 状态
            if (original.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                newPlayer.addTag("herobrine_companion.abyssal_gaze_active");
            }
            
            // 恢复 Transcendence Permit 状态
            // 使用 PersistentData 检查旧玩家状态
            if (original.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                // 将状态复制到新玩家的 PersistentData
                newPlayer.getPersistentData().putBoolean("herobrine_companion.transcendence_permit_active", true);
                newPlayer.getAbilities().mayfly = true;
                
                // 恢复飞行状态
                if (original.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_flying")) {
                    newPlayer.getPersistentData().putBoolean("herobrine_companion.should_restore_flying", true);
                }

                newPlayer.onUpdateAbilities();
            }

            if (original.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                // 恢复 Tag
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