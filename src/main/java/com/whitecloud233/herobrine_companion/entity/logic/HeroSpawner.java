package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler; // 确保导入这个处理器
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class HeroSpawner {

    public void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 0) return;

        // 1.21.1 查找实体逻辑
        boolean heroExists = false;
        
        // [修复] 检查全局活跃列表，而不仅仅是当前维度的实体列表
        // 这样可以防止 Hero 在其他维度时，主世界又生成一个新的
        for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
            if (hero.isAlive()) {
                heroExists = true;
                break;
            }
        }

        // [新增] 终极防线：检查 WorldData 中记录的 ActiveHeroUUID
        // 如果 WorldData 说有一个活跃的 Hero，但它不在 ACTIVE_HEROES 里（说明它在未加载的区块），
        // 那么我们也不应该生成新的，而是应该等待 CommonEvents 里的强制召回逻辑把它拉回来。
        if (!heroExists) {
            HeroWorldData data = HeroWorldData.get(level);
            UUID activeUUID = data.getActiveHeroUUID();
            if (activeUUID != null) {
                // 尝试在当前 level 找一下（虽然 ACTIVE_HEROES 没找到，可能刚加载？）
                // 或者更激进一点：只要有记录，就认为它存在（在某个未加载的区块）
                // 除非我们确认它真的消失了（比如被 /kill）
                
                // 但这里有个问题：如果服务器重启，ACTIVE_HEROES 会清空，但 WorldData 还在。
                // 此时如果不生成，Hero 就永远回不来了。
                
                // 解决方案：
                // 如果 ACTIVE_HEROES 为空，但 WorldData 有 UUID，说明 Hero 在未加载区块。
                // 此时我们应该生成吗？
                // 不应该。因为 CommonEvents 会尝试拉取。
                // 但是 CommonEvents 只能拉取 ACTIVE_HEROES 里的实体。
                
                // 悖论：
                // 1. Hero 在未加载区块 -> ACTIVE_HEROES 为空。
                // 2. CommonEvents 遍历 ACTIVE_HEROES -> 找不到 Hero -> 无法拉取。
                // 3. HeroSpawner 发现 ACTIVE_HEROES 为空 -> 生成新的 Hero。
                // 结果：重复生成。
                
                // 正确逻辑：
                // Hero 必须始终保持在 ACTIVE_HEROES 里吗？不，实体卸载后对象就失效了。
                // 所以，当 Hero 卸载时，我们需要一种机制来“重新加载”他，而不是“重新生成”他。
                
                // 实际上，Minecraft 的实体加载机制是：
                // 玩家靠近区块 -> 区块加载 -> 实体从磁盘读取 -> 加入世界 -> 触发 EntityJoinLevelEvent -> 加入 ACTIVE_HEROES。
                
                // 如果玩家传送到很远，原区块卸载，Hero 被保存到磁盘。
                // 此时 ACTIVE_HEROES 为空。
                // 玩家在几千格之外。
                // HeroSpawner 运行 -> 发现 ACTIVE_HEROES 为空 -> 生成新的 Hero。
                // 结果：原区块里有一个沉睡的 Hero，新区块里有一个新的 Hero。
                // 当玩家回到原区块，两个 Hero 见面。
                
                // 修复方案：
                // 我们需要利用 WorldData 记录的 UUID 来判断“是否已经有一个 Hero 存在于世界的某个角落”。
                // 如果有，我们不能生成新的，而是应该尝试“加载”那个旧的。
                // 但 Minecraft 没有直接“加载远方实体”的 API（除非加载区块）。
                
                // 妥协方案：
                // 如果 WorldData 记录了 UUID，说明有一个 Hero 存在。
                // 我们检查这个 UUID 对应的实体是否在 ACTIVE_HEROES 里。
                // 如果在 -> 正常，不生成。
                // 如果不在 -> 说明他在未加载区块。
                // 此时，我们应该**强制加载那个区块**吗？太耗性能。
                // 或者，我们可以认为他“丢失”了，生成一个新的，但**标记旧的为待删除**？
                
                // 更好的方案：
                // 既然我们已经有了“强制召回”逻辑（CommonEvents），但它依赖 ACTIVE_HEROES。
                // 我们可以修改“强制召回”逻辑，让它不依赖 ACTIVE_HEROES，而是依赖 WorldData？
                // 不行，没有实体对象，无法操作。
                
                // 最终方案：
                // 允许生成新的 Hero。
                // 但是，在 Hero 加载（readAdditionalSaveData）或者加入世界（onAddedToWorld）时，
                // 检查 WorldData 里的 UUID。
                // 如果发现自己不是那个“正统”的 Hero（UUID 不匹配），且那个“正统”Hero 处于活跃状态，则自杀。
                // 或者，更简单：
                // 每次生成新 Hero 时，更新 WorldData 里的 UUID 为新的。
                // 这样旧的 Hero 加载时，发现自己是“旧皇”，自动退位（消失）。
                
                // 让我们实施这个“新皇登基，旧皇退位”的策略。
                // 1. HeroSpawner 生成新 Hero -> 更新 WorldData.ActiveHeroUUID = 新 UUID。
                // 2. 旧 Hero 所在的区块被加载 -> 旧 Hero 激活 -> 检查 WorldData。
                // 3. 发现 ActiveHeroUUID != 自己的 UUID -> 自杀 (discard)。
                
                // 这样就解决了重复问题，而且不需要复杂的区块加载逻辑。
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

                        // [新增] 登记为“正统” Hero
                        HeroWorldData data = HeroWorldData.get(level);
                        data.setActiveHeroUUID(hero.getUUID());

                        level.addFreshEntity(hero);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}