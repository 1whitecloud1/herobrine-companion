package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.GlitchVillagerSpawner;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroQuestHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroSpawner;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

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

        // [新增] 检查爆炸范围内是否有正在执行守卫任务的 Hero
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (affectedBlocks.isEmpty()) return;

        // 2. [修复] 获取爆炸中心 (1.20.1 没有 center() 方法，需直接读取 x, y, z)
        Explosion explosion = event.getExplosion();

// 尝试使用 getPosition() 获取 Vec3 对象
// 注意：在你的映射表中，这个方法应该返回 Vec3
        Vec3 centerPos = explosion.getPosition();

        double ex = centerPos.x;
        double ey = centerPos.y;
        double ez = centerPos.z;

        // 3. 查找附近的 Hero (使用 AABB)
        // 范围设为以爆炸点为中心，半径 20 格的立方体
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class,
                new AABB(ex - 20, ey - 20, ez - 20, ex + 20, ey + 20, ez + 20));

        // 4. 遍历检测守卫逻辑
        for (HeroEntity hero : heroes) {
            // 检查 Hero 是否正在守卫 (Action 3) 且有合法的守卫点
            BlockPos guardPos = hero.getInvitedPos();
            if (hero.getInvitedAction() == 3 && guardPos != null) {

                // 计算爆炸点到守卫点的距离平方
                // 使用 BlockPos.containing 转换 double 坐标到 BlockPos，比强制转 int 更准确
                double distSqr = guardPos.distSqr(BlockPos.containing(ex, ey, ez));

                // 检查爆炸中心是否在守卫范围内 (例如 15格半径 = 225平方)
                // 你原本写的是 100 (10格)，这里可以根据需要调整
                if (distSqr < 225) {

                    // [核心逻辑] 移除受保护区域内的方块破坏
                    // 保护半径设为 10 格 (100平方)
                    affectedBlocks.removeIf(pos -> pos.distSqr(guardPos) < 100);

                    // 视觉反馈：在守卫点生成粒子 (仅服务端执行)
                    if (!event.getLevel().isClientSide && event.getLevel() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                guardPos.getX() + 0.5, guardPos.getY() + 1.0, guardPos.getZ() + 0.5,
                                20, 0.5, 0.5, 0.5, 0.1);
                    }

                    // 只要有一个 Hero 进行了保护，就跳出循环，避免重复计算
                    break;
                }
            }
        }
    }
}