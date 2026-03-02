package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public class HeroOtherProtection {

    public static boolean canBeAffected(HeroEntity hero, MobEffectInstance instance) {
        return false;
    }

    public static boolean canBeLeashed(HeroEntity hero) {
        return false;
    }

    // 检查并移除火焰，以及虚空保护
    public static void tick(HeroEntity hero) {
        if (hero.isOnFire()) {
            hero.clearFire();
        }

        Level level = hero.level();
        // 确保传送逻辑只在服务端执行
        if (!level.isClientSide) {
            // 👉 就是这一行！必须在这里调用，方法才算被“使用”
            checkAutoFly(hero, level);
            // 当 Herobrine 掉落到世界最低建筑高度以下 10 格时触发
            if (hero.getY() < level.getMinBuildHeight() - 10) {
                rescueFromVoid(hero, (ServerLevel) level);
            }
        }
    }
    // [新增方法] 检测脚底是否悬空并自动切换飞行状态
    private static void checkAutoFly(HeroEntity hero, Level level) {
        // 获取脚下1格和2格的方块位置
        BlockPos posBelow = hero.blockPosition().below();
        BlockPos posBelow2 = posBelow.below();

        // 检查脚下两格是否都是空气（往下多看一格可以防止下楼梯或走半砖时频繁触发起飞）
        boolean isAirBelow = level.getBlockState(posBelow).isAir() && level.getBlockState(posBelow2).isAir();

        if (!hero.onGround() && isAirBelow) {
            // 如果不在地面且脚下是悬空的，强制开启飞行
            if (!hero.isFloating()) {
                hero.setFloating(true); // ⚠️注意：这要求你的 HeroEntity 类中有 setFloating(boolean) 方法
            }
        }
        // [可选逻辑] 如果落地了，且脚下有方块，自动取消飞行恢复行走动画
        else if (hero.onGround() && !isAirBelow) {
            if (hero.isFloating()) {
                hero.setFloating(false);
            }
        }
    }
    private static void rescueFromVoid(HeroEntity hero, ServerLevel level) {
        boolean rescued = false;

        // 方案 1：优先瞬移回主人身边 (前提是主人在同一个维度且存活)
        if (hero.getOwnerUUID() != null) {
            Player owner = level.getPlayerByUUID(hero.getOwnerUUID());
            if (owner != null && owner.isAlive()) {
                hero.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                rescued = true;
            }
        }

        // 方案 2：螺旋扫描附近的区块寻找陆地
        if (!rescued) {
            BlockPos safePos = findSafeLandNearby(level, hero.blockPosition());
            if (safePos != null) {
                // X 和 Z 加上 0.5 让他站在方块正中心，防止卡在边缘又掉下去
                hero.teleportTo(safePos.getX() + 0.5, safePos.getY() + 1.0, safePos.getZ() + 0.5);
                rescued = true;
            }
        }

        // 方案 3：如果附近全是虚空（例如末地深空），强制传回当前维度的世界重生点
        if (!rescued) {
            BlockPos spawnPos = level.getSharedSpawnPos();
            BlockPos safeSpawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos);

            // 【神之兜底】：如果连重生点都没有方块（比如极限空岛被炸光了）
            // 创世神不需要陆地，他自己创造陆地
            if (safeSpawn.getY() <= level.getMinBuildHeight()) {
                safeSpawn = new BlockPos(spawnPos.getX(), 64, spawnPos.getZ()); // 设定在 Y=64 的安全高度
                level.setBlock(safeSpawn, Blocks.BEDROCK.defaultBlockState(), 3); // 强行放置一块基岩
            }

            hero.teleportTo(safeSpawn.getX() + 0.5, safeSpawn.getY() + 1.0, safeSpawn.getZ() + 0.5);
        }

        // 演出效果：重制跌落伤害，播放末影瞬移音效和大量粒子
        hero.fallDistance = 0.0F;
        level.playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, hero.getX(), hero.getY() + 1.0, hero.getZ(), 80, 0.5, 1.0, 0.5, 0.1);
    }

    // 辅助方法：在周围寻找有效的非虚空坐标
    private static BlockPos findSafeLandNearby(ServerLevel level, BlockPos center) {
        // 先检查正上方
        BlockPos directUp = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        if (directUp.getY() > level.getMinBuildHeight()) {
            return directUp;
        }

        // 简单的十字/井字扫描（半径 16格 到 32格）寻找最近的岛屿边缘
        int[] offsets = {128, -128, 128, -128};
        for (int dx : offsets) {
            for (int dz : offsets) {
                BlockPos testPos = new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
                BlockPos highestHere = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);

                // 如果找到的最高点大于世界的最低高度，说明这里有实实在在的方块
                if (highestHere.getY() > level.getMinBuildHeight()) {
                    return highestHere;
                }
            }
        }

        return null; // 附近什么都没有，全是虚空
    }
}
