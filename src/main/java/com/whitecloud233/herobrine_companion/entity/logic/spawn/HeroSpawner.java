package com.whitecloud233.herobrine_companion.entity.logic.spawn;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class HeroSpawner {

    // 在 HeroSpawner.java 中修改 tick 方法：
    public void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 0) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        RandomSource random = level.getRandom();
        for (ServerPlayer player : players) {
            UUID playerUUID = player.getUUID();

            // [修改] 只检查当前这个玩家是否已经拥有存活的 Hero
            boolean heroExistsForPlayer = false;
            for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
                if (hero.isAlive() && playerUUID.equals(hero.getOwnerUUID())) {
                    heroExistsForPlayer = true;
                    break;
                }
            }

            if (!heroExistsForPlayer) {
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

                        // 1. 生成瞬间立即绑定主人，确立主权
                        hero.setOwnerUUID(player.getUUID());

                        // 👇👇👇 【核心数据防丢失与恢复逻辑】 👇👇👇

                        // 2. 恢复硬盘中存储的满级装备、皮肤、姿势
                        com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager.restoreFromGlobal(hero, player);

                        // 3. 恢复信任度和任务/奖励状态
                        com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler.syncGlobalTrust(hero);

                        // 4. 恢复大脑记忆 (神经网络数据)，防止性格和学习进度被洗白
                        com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData worldData =
                                com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData.get(level);
                        net.minecraft.nbt.CompoundTag brainData = worldData.getTempBrainData(player.getUUID());
                        if (brainData != null && !brainData.isEmpty()) {
                            hero.getHeroBrain().load(brainData);
                        }

                        // 👆👆👆 ============================== 👆👆👆

                        level.addFreshEntity(hero);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}