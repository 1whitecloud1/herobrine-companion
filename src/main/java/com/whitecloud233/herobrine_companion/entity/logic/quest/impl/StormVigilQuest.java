package com.whitecloud233.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.herobrine_companion.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 委托 8「雷声中的守望」：雷暴时站上 Hero 标记的高处，被闪电选中并存活。
 * 闪电落下而未死，即证明世界还记得你；被劈死则委托失败。
 * 奖励幽邃之视 x1 + 信任 +20。
 */
public class StormVigilQuest implements HeroQuest {

    /** 标志位：闪电已落下，等待裁决。 */
    private static final int FLAG_STRUCK = 0;

    private static final double MARK_RANGE_XZ = 4.0;
    private static final double MARK_RANGE_Y = 6.0;

    /** 玩家在标记处累积的“被选中几率”tick，离开标记即清零。 */
    private final Map<UUID, Integer> exposureTicks = new HashMap<>();

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        BlockPos mark = findPeak((ServerLevel) player.level(), player.blockPosition());
        progress.setPositions(new int[]{mark.getX(), mark.getY(), mark.getZ()});
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_8"));
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_storm_mark", mark.getX(), mark.getY(), mark.getZ()));
    }

    @Override
    public void onTick(ServerPlayer player, QuestProgress progress) {
        // 闪电已落下：存活即完成（死亡分支由 onPlayerDeath 处理）
        if (progress.flag(FLAG_STRUCK)) {
            if (player.isAlive()) {
                exposureTicks.remove(player.getUUID());
                HeroQuestManager.completeQuest(player);
            }
            return;
        }

        if (!player.level().isThundering()) {
            exposureTicks.put(player.getUUID(), 0);
            return;
        }

        int[] mark = progress.positions();
        if (mark.length < 3) return;

        boolean nearMark = Math.abs(player.getX() - mark[0]) < MARK_RANGE_XZ
                && Math.abs(player.getZ() - mark[2]) < MARK_RANGE_XZ
                && Math.abs(player.getY() - mark[1]) < MARK_RANGE_Y;

        int exposure = exposureTicks.getOrDefault(player.getUUID(), 0);
        if (!nearMark) {
            exposureTicks.put(player.getUUID(), 0);
            return;
        }

        exposure++;
        // 站够 4 秒后开始高概率被选中（约 5 秒内触发）
        if (exposure > 80 && player.level().random.nextFloat() < 0.2F) {
            strike(player);
            progress.setFlag(FLAG_STRUCK, true);
            exposureTicks.put(player.getUUID(), 0);
            return;
        }
        exposureTicks.put(player.getUUID(), exposure);

        // 提示：雷声近了
        if (player.tickCount % 200 == 0) {
            player.displayClientMessage(Component.translatable("message.herobrine_companion.quest_storm_hint"), true);
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayer player, QuestProgress progress) {
        // 只有“闪电刚落下”状态下的死亡才算被世界拒绝
        if (progress.flag(FLAG_STRUCK)) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_storm_died");
        }
    }

    @Override
    public void onCancel(ServerPlayer player, QuestProgress progress) {
        exposureTicks.remove(player.getUUID());
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_8"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.ABYSSAL_GAZE.get(), 1));
        HeroQuestManager.addTrust(player, 20);
    }

    private static void strike(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(player.getX(), player.getY(), player.getZ());
                bolt.setVisualOnly(false);
                serverLevel.addFreshEntity(bolt);
            }
        }
    }

    /** 在玩家周围 32 格内寻找最高的地表点作为守望标记。 */
    private static BlockPos findPeak(ServerLevel level, BlockPos center) {
        BlockPos best = center;
        int bestY = -64;
        for (int dx = -32; dx <= 32; dx += 2) {
            for (int dz = -32; dz <= 32; dz += 2) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz);
                if (y > bestY) {
                    bestY = y;
                    best = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                }
            }
        }
        return best;
    }
}