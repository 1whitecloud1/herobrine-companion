package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.AgentTaskContext;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.ReportTask;
import com.whitecloud233.herobrine_companion.world.structure.UnstableZoneRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常方块扫描 / 净化助手（P4）：把 {@code HeroFixAnomalyGoal} 的"找异常方块 + AOE 清除"逻辑
 * 抽成共享静态工具，供修复 goal 与 agent 任务（{@code RepairAreaTask}）复用。
 *
 * <p><b>单一职责</b>：只做"扫描 + 净化"，不参与 goal 状态机、不参与任务调度。</p>
 */
public final class HeroAnomalySupport {

    /** 扫描半径（与 HeroFixAnomalyGoal 原实现一致：32×32×32）。 */
    private static final int SCAN_RADIUS = 32;

    private HeroAnomalySupport() {
    }

    /** 找第一个异常方块（原 HeroFixAnomalyGoal.findGlitchBlock）。 */
    public static BlockPos findGlitchBlock(HeroEntity hero) {
        List<BlockPos> found = findGlitchBlocks(hero, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 找最多 max 个异常方块（供探查任务汇报）。
     *
     * <p><b>性能说明（P0 优化）</b>：旧实现遍历 65×65×65=274,625 个坐标逐个
     * {@code getBlockState} 再查询 {@code isTracked}，由 {@code HeroFixAnomalyGoal.canUse()}
     * 每 10 tick 触发一次，实测占服务端有效计算时间的 3.7%。</p>
     *
     * <p>新实现改用 {@link UnstableZoneRuntime#collectTrackedAnomalyBlocks} 做集合反查：
     * 因为 {@code isTracked} 本身就是「坐标是否在这个集合里」，所以反向枚举集合
     * （通常几十到几百个元素）并按立方体范围过滤，结果与全量扫描完全一致，
     * 但复杂度从 O(274625) 降到 O(已追踪方块数)。</p>
     *
     * <p>返回的位置保证 {@code isGlitchBlock} 为真，并按「距 Hero 的平方距离」由近到远排序
     * （集合本身是无序哈希集，直接取前 N 个会让 Hero 在异常方块间无规律跳动）。</p>
     */
    public static List<BlockPos> findGlitchBlocks(HeroEntity hero, int max) {
        if (hero == null || hero.level().isClientSide || max <= 0) {
            return List.of();
        }
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        // 没有任何被追踪的异常方块时直接返回，连范围过滤都省掉。
        if (UnstableZoneRuntime.hasNoTrackedAnomalyBlocks(serverLevel)) {
            return List.of();
        }
        BlockPos heroPos = hero.blockPosition();
        List<BlockPos> candidates = UnstableZoneRuntime.collectTrackedAnomalyBlocks(
                serverLevel, heroPos, SCAN_RADIUS, max);
        if (candidates.isEmpty()) {
            return List.of();
        }
        // 集合里登记过、但方块可能已被替换/破坏，仍要校验一次世界状态（每次最多 max 次采样）。
        List<BlockPos> found = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            BlockState state = serverLevel.getBlockState(pos);
            if (isGlitchBlock(serverLevel, pos, state)) {
                found.add(pos);
                if (found.size() >= max) {
                    break;
                }
            }
        }
        return found;
    }

    public static boolean isGlitchBlock(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return UnstableZoneRuntime.isTrackedAnomalyBlock(serverLevel, pos, state);
    }

    /**
     * 该位置是否落在玩家用 {@code hero_remember} 明确标记的区域内（交接 E3 的消费者）。
     *
     * <p>"这里是我家，以后别动它"必须真的改变行为，否则只是一条存起来的字符串。
     * 这里让区域偏好拦截净化——玩家划定的地方不被地形改写。</p>
     */
    public static boolean isProtectedByOwnerPreference(HeroEntity hero, net.minecraft.world.level.Level level, BlockPos pos) {
        if (hero == null || pos == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return hero.getHeroAgent().memory().landmarks()
                .isInsideArea(serverLevel.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** 以 center 为中心 AOE 净化（原 HeroFixAnomalyGoal.performAreaCleanse）。 */
    public static void performAreaCleanse(HeroEntity hero, BlockPos center) {
        if (hero == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 玩家明确声明为"我家"的区域，不在这里破坏方块（交接 E3 的实际消费点）。
        if (isProtectedByOwnerPreference(hero, serverLevel, center)) {
            ReportTask.send(new AgentTaskContext(hero), "这一带是你标记过的地方，我没有动手", false);
            return;
        }
        serverLevel.playSound(null, center, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.5F, 1.5F);

        int radius = 8;
        for (int i = 0; i < 10; i++) {
            int x = center.getX() + hero.getRandom().nextInt(radius * 2) - radius;
            int z = center.getZ() + hero.getRandom().nextInt(radius * 2) - radius;
            int y = serverLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(x, y, z);
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }
        }

        serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 5, () -> {
            List<BlockPos> removedBlocks = new ArrayList<>();
            // [P0 优化] 旧实现遍历 (2*radius+1)^3=4913 个坐标逐个 getBlockState + isTracked，
            // 命中后再逐格 destroyBlock（每格都要触一次方块更新与客户端同步）。
            // 改为集合反查：只遍历已登记的异常方块，再校验范围与世界状态。
            List<BlockPos> candidates = UnstableZoneRuntime.collectTrackedAnomalyBlocks(
                    serverLevel, center, radius, Integer.MAX_VALUE);
            for (BlockPos p : candidates) {
                BlockState state = serverLevel.getBlockState(p);
                if (!isGlitchBlock(serverLevel, p, state)) {
                    continue;
                }
                if (!UnstableZoneRuntime.isInUnstableZone(serverLevel, p)) {
                    continue;
                }
                serverLevel.destroyBlock(p, false);
                removedBlocks.add(p);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        5, 0.5, 0.5, 0.5, 0.05);
            }
            UnstableZoneRuntime.removeTrackedBlocks(serverLevel, removedBlocks);

            if (hero.isCompanionMode() && hero.getOwnerUUID() != null) {
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner instanceof ServerPlayer serverPlayer) {
                    HeroDialogueHandler.onCleanseArea(hero, serverPlayer);
                }
            }
        }));
    }
}
