package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.CleaveBladeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "herobrine_companion", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CleaveTickManager {

    private static final List<CleaveTask> activeCleaves = new ArrayList<>();

    public static class CleaveTask {
        public final ServerLevel level;
        public final double startX, startZ;
        public final double dirX, dirZ;
        public final int halfWidth;
        public final int startY, minY;
        public final double maxLength;
        public double currentLength;
        public Integer targetY = null;

        public CleaveTask(ServerLevel level, double startX, double startZ, double dirX, double dirZ, int halfWidth, int startY, int minY, double maxLength) {
            this.level = level;
            this.startX = startX;
            this.startZ = startZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.halfWidth = halfWidth;
            this.startY = startY;
            this.minY = minY;
            this.maxLength = maxLength;
            this.currentLength = 1.0;
        }
    }

    public static void startCleave(CleaveTask task) {
        activeCleaves.add(task);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || activeCleaves.isEmpty()) return;
        if (!(event.level instanceof ServerLevel)) return;
        ServerLevel currentLevel = (ServerLevel) event.level;

        Iterator<CleaveTask> iterator = activeCleaves.iterator();
        while (iterator.hasNext()) {
            CleaveTask task = iterator.next();
            if (task.level != currentLevel) continue;

            // 1. 目标高度初始化
            if (task.targetY == null) {
                double endX = task.startX + task.dirX * task.maxLength;
                double endZ = task.startZ + task.dirZ * task.maxLength;
                int endBlockX = Mth.floor(endX);
                int endBlockZ = Mth.floor(endZ);

                if (task.level.isLoaded(new BlockPos(endBlockX, 0, endBlockZ))) {
                    task.targetY = task.level.getHeight(Heightmap.Types.WORLD_SURFACE, endBlockX, endBlockZ);
                } else {
                    double nearX = task.startX + task.dirX * 5.0;
                    double nearZ = task.startZ + task.dirZ * 5.0;
                    task.targetY = task.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(nearX), Mth.floor(nearZ));
                }
                if (task.targetY < task.minY + 5) task.targetY = 64;
            }

            int stepsPerTick = 2;
            double stepSize = 0.5;

            for (int s = 0; s < stepsPerTick; s++) {
                // 判断是否到达终点
                if (task.currentLength > task.maxLength) {
                    triggerAftermath(task);
                    iterator.remove();
                    break;
                }

                double centerX = task.startX + task.dirX * task.currentLength;
                double centerZ = task.startZ + task.dirZ * task.currentLength;
                int cX = Mth.floor(centerX);
                int cZ = Mth.floor(centerZ);

                // 区块未加载保护
                if (!task.level.isLoaded(new BlockPos(cX, 0, cZ))) {
                    triggerAftermath(task);
                    iterator.remove();
                    break;
                }

                // ==========================================
                // 2. 同步湮灭实体 (无情抹除范围内的所有东西)
                // ==========================================
                AABB sliceBox = new AABB(
                        cX - task.halfWidth - 1, task.minY, cZ - task.halfWidth - 1,
                        cX + task.halfWidth + 2, task.startY + 2, cZ + task.halfWidth + 2
                );
                List<Entity> entitiesToErase = task.level.getEntities(null, sliceBox);
                for (Entity e : entitiesToErase) {
                    // 【终极修复】：除了玩家和刀光，绝对不能抹杀创世神！
                    if (!(e instanceof Player) && !(e instanceof CleaveBladeEntity) && !(e instanceof HeroEntity)) {
                        e.discard();
                    }
                }
                double currentBladeBaseY = task.minY;

                // ==========================================
                // 3. 撕裂地形与阶梯化切削
                // ==========================================
                for (int dx = -task.halfWidth; dx <= task.halfWidth; dx++) {
                    for (int dz = -task.halfWidth; dz <= task.halfWidth; dz++) {
                        int blockX = cX + dx;
                        int blockZ = cZ + dz;

                        double vx = (blockX + 0.5) - task.startX;
                        double vz = (blockZ + 0.5) - task.startZ;
                        double t = vx * task.dirX + vz * task.dirZ;
                        double remaining = task.maxLength - t;
                        int blockMinY = task.minY;

                        int depth = task.targetY - task.minY;
                        double slopeLength = Math.max(20.0, depth * 2);

                        if (remaining <= slopeLength && remaining >= 0) {
                            double progress = (slopeLength - remaining) / slopeLength;
                            blockMinY = task.minY + (int) ((task.targetY - task.minY) * progress);
                        } else if (remaining < 0) {
                            blockMinY = task.targetY;
                        }

                        currentBladeBaseY = blockMinY;

                        int surfaceY = task.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                        int actualStartY = Math.min(task.startY, surfaceY + 2);

                        if (blockMinY > actualStartY) continue;

                        for (int y = actualStartY; y >= blockMinY; y--) {
                            BlockPos pos = new BlockPos(blockX, y, blockZ);
                            BlockState state = task.level.getBlockState(pos);
                            if (!state.isAir()) {
                                task.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                            }
                        }
                    }
                }

                // ==========================================
                // 4. 仅保留撕裂音效 (方便配合你自己的贴图实体)
                // ==========================================
                double pyBase = currentBladeBaseY + 1.0;
                if (s == 0) {
                    task.level.playSound(null, centerX, pyBase, centerZ, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.AMBIENT, 3.0F, 1.5F);
                    task.level.playSound(null, centerX, pyBase, centerZ, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 1.5F, 0.5F);
                }

                task.currentLength += stepSize;
            }
        }
    }

    private static void triggerAftermath(CleaveTask task) {
        ServerLevel level = task.level;
        double endX = task.startX + task.dirX * task.currentLength;
        double endZ = task.startZ + task.dirZ * task.currentLength;
        int endY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(endX), Mth.floor(endZ));

        // 尽头的毁灭音效
        level.playSound(null, endX, endY, endZ, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 8.0F, 0.6F);
        level.playSound(null, endX, endY, endZ, SoundEvents.TRIDENT_THUNDER, SoundSource.AMBIENT, 6.0F, 0.5F);

        // 物理冲击波与余波伤害
        AABB bounds = new AABB(endX - 20, endY - 10, endZ - 20, endX + 20, endY + 15, endZ + 20);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, bounds);

        for (LivingEntity target : entities) {
            double dx = target.getX() - endX;
            double dz = target.getZ() - endZ;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < 20.0 && distance > 0) {
                double pushMultiplier = (20.0 - distance) / 20.0;
                target.setDeltaMovement(
                        target.getDeltaMovement().add(
                                (dx / distance) * 2.5 * pushMultiplier,
                                1.5 * pushMultiplier,
                                (dz / distance) * 2.5 * pushMultiplier
                        )
                );
                target.hurt(level.damageSources().magic(), 20.0F);
            }
        }
    }
}