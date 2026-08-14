package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
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

    /** 找最多 max 个异常方块（供探查任务汇报）。 */
    public static List<BlockPos> findGlitchBlocks(HeroEntity hero, int max) {
        List<BlockPos> found = new ArrayList<>();
        if (hero == null || hero.level().isClientSide || max <= 0) {
            return found;
        }
        BlockPos heroPos = hero.blockPosition();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos p = heroPos.offset(x, y, z);
                    BlockState state = hero.level().getBlockState(p);
                    if (isGlitchBlock(hero.level(), p, state)) {
                        found.add(p.immutable());
                        if (found.size() >= max) {
                            return found;
                        }
                    }
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

    /** 以 center 为中心 AOE 净化（原 HeroFixAnomalyGoal.performAreaCleanse）。 */
    public static void performAreaCleanse(HeroEntity hero, BlockPos center) {
        if (hero == null || !(hero.level() instanceof ServerLevel serverLevel)) {
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
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos p = center.offset(x, y, z);
                        BlockState state = serverLevel.getBlockState(p);
                        if (isGlitchBlock(serverLevel, p, state) && UnstableZoneRuntime.isInUnstableZone(serverLevel, p)) {
                            serverLevel.destroyBlock(p, false);
                            removedBlocks.add(p.immutable());
                            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 0.05);
                        }
                    }
                }
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
