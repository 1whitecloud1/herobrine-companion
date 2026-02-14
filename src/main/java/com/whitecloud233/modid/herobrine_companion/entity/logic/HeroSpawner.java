package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;


import java.util.List;

public class HeroSpawner {

    public void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 0) return;

        // 只有当 Hero 不存在时才尝试生成
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
                // [修改] 基础概率设为 10% (0.1F)，具体能否生成取决于光照和距离
                if (random.nextFloat() < 0.1F) {
                    attemptSpawnSmart(level, player, random);
                }
            }
        }
    }

    // [新增] 智能生成逻辑：根据光照调整距离和概率
    private boolean attemptSpawnSmart(ServerLevel level, ServerPlayer player, RandomSource random) {
        // 尝试 10 次寻找合适的位置
        for (int i = 0; i < 10; i++) {
            // 1. 随机生成一个距离和角度
            // 基础范围：8 ~ 48 格
            double angle = random.nextDouble() * Math.PI * 2;
            double rawDistance = 8.0 + random.nextDouble() * 40.0;

            int x = (int) (player.getX() + Math.cos(angle) * rawDistance);
            int z = (int) (player.getZ() + Math.sin(angle) * rawDistance);

            // 优先尝试玩家所在高度
            int startY = (int) player.getY();

            // 寻找最近的地面 (上下 5 格)
            BlockPos targetPos = null;
            for (int dy = 5; dy >= -5; dy--) {
                BlockPos testPos = new BlockPos(x, startY + dy, z);
                // 检查空间：脚下有方块，脚和头是空气
                if (level.isEmptyBlock(testPos) && level.isEmptyBlock(testPos.above()) && level.getBlockState(testPos.below()).canOcclude()) {
                    targetPos = testPos;
                    break;
                }
            }

            if (targetPos != null) {
                int brightness = level.getMaxLocalRawBrightness(targetPos);
                boolean isDark = brightness <= 7;
                double distanceSq = player.distanceToSqr(targetPos.getX(), targetPos.getY(), targetPos.getZ());

                // [核心逻辑] 区分光照条件
                if (isDark) {
                    // --- 黑暗环境 ---
                    // 允许近距离生成 (8格以上)
                    // 概率高 (这里已经是 30% 的基础概率进入循环，只要位置合法就生成)
                    if (distanceSq < 8.0 * 8.0) continue; // 太近了不生成
                } else {
                    // --- 明亮环境 ---
                    // 必须远距离生成 (24格以上)
                    if (distanceSq < 24.0 * 24.0) continue;

                    // 概率降低：额外增加 80% 的失败率 (即只有 20% 的概率在亮处生成)
                    // 综合概率 = 0.3 * 0.2 = 0.06 (6%)
                    if (random.nextFloat() > 0.2F) continue;
                }

                // 检查是否有足够空间 (AABB)
                if (level.noCollision(ModEvents.HERO.get().getDimensions().makeBoundingBox(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5))) {
                    HeroEntity hero = ModEvents.HERO.get().create(level);
                    if (hero != null) {
                        hero.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
                        level.addFreshEntity(hero);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
