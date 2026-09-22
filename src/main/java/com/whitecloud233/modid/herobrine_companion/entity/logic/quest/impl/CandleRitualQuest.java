package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 委托 5「烛火仪式」：午夜时在 Hero 身边 5 格内点亮 4 支蜡烛。
 * 亮起第 5 支会引来不该来的东西——仪式失败（并赐予一阵黑暗）。
 * 奖励故障碎片 x2 + 信任 +12。
 */
public class CandleRitualQuest implements HeroQuest {

    private static final int RADIUS = 5;
    private static final int TARGET_CANDLES = 4;

    /** 每位玩家上次向客户端播报的烛火数，避免每 tick 刷屏。 */
    private final Map<UUID, Integer> lastShown = new HashMap<>();

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_5"));
    }

    @Override
    public void onTick(ServerPlayer player, QuestProgress progress) {
        if (player.tickCount % 10 != 0) return;

        HeroEntity hero = resolveHero(player, progress);
        if (hero == null) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_candle_hero_gone");
            return;
        }
        // 烛火只在午夜生效
        if (!hero.level().isNight()) return;

        int lit = countLitCandlesAround(hero);
        if (lit > TARGET_CANDLES) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, false));
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_candle_fifth");
            lastShown.remove(player.getUUID());
            return;
        }

        int previous = lastShown.getOrDefault(player.getUUID(), 0);
        if (lit > previous && lit > 0) {
            player.displayClientMessage(Component.translatable("message.herobrine_companion.quest_candle_progress", lit, TARGET_CANDLES), true);
        }
        lastShown.put(player.getUUID(), lit);

        if (lit >= TARGET_CANDLES) {
            HeroQuestManager.completeQuest(player);
            lastShown.remove(player.getUUID());
        }
    }

    @Override
    public void onCancel(ServerPlayer player, QuestProgress progress) {
        lastShown.remove(player.getUUID());
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_5"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.GLITCH_FRAGMENT.get(), 2));
        HeroQuestManager.addTrust(player, 12);
    }

    private static HeroEntity resolveHero(ServerPlayer player, QuestProgress progress) {
        UUID heroUuid = progress.heroUuid();
        if (heroUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(heroUuid);
        return entity instanceof HeroEntity hero && hero.isAlive() && !hero.isRemoved() ? hero : null;
    }

    private static int countLitCandlesAround(HeroEntity hero) {
        BlockPos center = hero.blockPosition();
        int count = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockState state = hero.level().getBlockState(center.offset(dx, dy, dz));
                    if (state.getBlock() instanceof CandleBlock && Boolean.TRUE.equals(state.getValue(CandleBlock.LIT))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}