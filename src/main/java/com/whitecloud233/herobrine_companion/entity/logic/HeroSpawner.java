package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler; // 确保导入这个处理器
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HeroSpawner {

    public void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 0) return;

        // 1.21.1 查找实体逻辑
        boolean heroExists = false;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity && entity.isAlive()) {
                heroExists = true;
                break;
            }
        }

        if (!heroExists) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) return;

            RandomSource random = level.getRandom();
            for (ServerPlayer player : players) {
                // 基础概率 10%
                if (random.nextFloat() < 0.1F) {
                    attemptSpawnSmart(level, player, random);
                }
            }
        }
    }

    private boolean attemptSpawnSmart(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 10; i++) {
            // 基础范围：8 ~ 48 格
            double angle = random.nextDouble() * Math.PI * 2;
            double rawDistance = 8.0 + random.nextDouble() * 40.0;

            int x = (int) (player.getX() + Math.cos(angle) * rawDistance);
            int z = (int) (player.getZ() + Math.sin(angle) * rawDistance);

            int startY = (int) player.getY();

            BlockPos targetPos = null;
            for (int dy = 5; dy >= -5; dy--) {
                BlockPos testPos = new BlockPos(x, startY + dy, z);
                if (level.isEmptyBlock(testPos) && level.isEmptyBlock(testPos.above()) && level.getBlockState(testPos.below()).canOcclude()) {
                    targetPos = testPos;
                    break;
                }
            }

            if (targetPos != null) {
                // 1.21 使用 getMaxLocalRawBrightness
                int brightness = level.getMaxLocalRawBrightness(targetPos);
                boolean isDark = brightness <= 7;
                double distanceSq = player.distanceToSqr(targetPos.getX(), targetPos.getY(), targetPos.getZ());

                if (isDark) {
                    if (distanceSq < 8.0 * 8.0) continue;
                } else {
                    if (distanceSq < 24.0 * 24.0) continue;
                    // 亮处生成概率大幅降低
                    if (random.nextFloat() > 0.2F) continue;
                }

                // 1.21.1 AABB 检查
                AABB aabb = ModEvents.HERO.get().getDimensions().makeBoundingBox(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                if (level.noCollision(aabb)) {
                    HeroEntity hero = ModEvents.HERO.get().create(level);
                    if (hero != null) {
                        hero.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, random.nextFloat() * 360F, 0);

                        // [BUG 修复核心] 生成瞬间立即绑定主人
                        hero.setOwnerUUID(player.getUUID());

                        // [BUG 修复核心] 立即从全局存档恢复数据 (防止 Trust=0 进入世界)
                        // 这会把 WorldData 里的 50 信任度读取到 Entity 身上
                        HeroDataHandler.restoreTrustFromPlayer(hero);

                        level.addFreshEntity(hero);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}