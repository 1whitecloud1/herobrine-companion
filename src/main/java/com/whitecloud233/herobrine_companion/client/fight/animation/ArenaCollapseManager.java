package com.whitecloud233.herobrine_companion.client.fight.animation;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ArenaCollapseManager {

    public static void tickCollapse(HeroEntity hero, ServerLevel level, int timer) {
        // 只在 60 到 135 帧执行崩坏
        if (timer < 60 || timer > 135) return;

        // 【核心】：寻找玩家的实时坐标，让世界向着玩家脚底收敛坍塌！
        ServerPlayer targetPlayer = null;
        for (ServerPlayer p : level.players()) {
            if (p.getPersistentData().getBoolean("IsChallengeActive")) {
                targetPlayer = p;
                break;
            }
        }

        // 如果找不到玩家，默认使用擂台中心 (0, 0)
        int centerX = targetPlayer != null ? (int) targetPlayer.getX() : 0;
        int centerZ = targetPlayer != null ? (int) targetPlayer.getZ() : 0;
        int cy = 100; // 擂台基础高度

        // 坍塌进度 (0 到 75)
        int progress = timer - 60;

        // 计算当前正在剥落的环形半径（从 150 缩减到 0）
        int outerR = 150 - progress * 2;
        int innerR = outerR - 4; // 一次拆宽一点，防止因为玩家移动导致遗漏

        if (outerR < 0) return;

        // 【性能保护】：每帧最多生成 60 个实体方块，防止成千上万个实体直接炸服
        int maxFlyingBlocksPerTick = 60;
        int spawnedBlocks = 0;

        for (int x = centerX - outerR - 2; x <= centerX + outerR + 2; x++) {
            for (int z = centerZ - outerR - 2; z <= centerZ + outerR + 2; z++) {
                double distSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);

                // 扫描处于当前坍塌边缘的方块
                if (distSq <= outerR * outerR && distSq > innerR * innerR) {
                    // 连同上下厚度一起消除
                    for (int y = cy; y <= cy + 3; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);

                        if (!state.isAir()) {
                            // 1. 将原方块抹除为空气 (2代表同步给客户端)
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);

                            // 2. 视觉演出：方块乱飞！(控制概率生成实体，大场面且不卡)
                            if (spawnedBlocks < maxFlyingBlocksPerTick && level.random.nextFloat() < 0.15f) {
                                spawnedBlocks++;

                                // 生成原版掉落沙/铁砧类型的方块实体
                                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, pos, state);
                                fallingBlock.dropItem = false; // 掉入虚空不掉落物品

                                // 计算背离玩家的向外爆炸向量
                                double dx = x - centerX;
                                double dz = z - centerZ;
                                if (dx == 0 && dz == 0) {
                                    dx = level.random.nextDouble() - 0.5;
                                    dz = level.random.nextDouble() - 0.5;
                                }
                                Vec3 dir = new Vec3(dx, 0, dz).normalize();

                                // 赋予极具冲击力的爆炸初速度 (向外、向上飞出)
                                double power = 0.6 + level.random.nextDouble() * 0.8;
                                double vx = dir.x * power;
                                double vy = 0.5 + level.random.nextDouble() * 0.8; // 向上抛起
                                double vz = dir.z * power;

                                fallingBlock.setDeltaMovement(vx, vy, vz);
                            }
                            // 3. 没变成实体的方块，生成浓密的方块破碎粒子填补视觉
                            else if (level.random.nextFloat() < 0.25f) {
                                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                                        x + 0.5, y + 0.5, z + 0.5,
                                        4, 0.3, 0.3, 0.3, 0.15);
                            }
                        }
                    }
                }
            }
        }

        // 音效：连绵不绝的剧烈爆炸和石头碎裂声
        if (timer % 5 == 0) {
            level.playSound(null, centerX, cy, centerZ, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 6.0f, 0.5f);
            level.playSound(null, centerX, cy, centerZ, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.8f);
        }
    }
}