package com.whitecloud233.herobrine_companion.entity.companion;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.fight.ChallengeSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 战斗模式服务端逐刻巡检：玩家开启的限时会话到时自动收刀，并通知主人。
 *
 * <p>没有 {@link BattleModeService#TAG_UNTIL} 标记（不限时配置或内部技能桥开启）时直接跳过。
 * 挑战期间不强制收刀，避免打断挑战演出——挑战本身已经双向锁定玩家切换。</p>
 */
public final class BattleModeRuntime {
    private BattleModeRuntime() {
    }

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide || !hero.isBattleModeActive()) return;
        if (!hero.getPersistentData().contains(BattleModeService.TAG_UNTIL)) return;

        long now = hero.level().getGameTime();
        if (now < hero.getPersistentData().getLong(BattleModeService.TAG_UNTIL)) return;
        if (ChallengeSession.isHeroInChallenge(hero)) return;

        BattleModeService.endSession(hero, now);
        hero.setBattleModeActiveFrom("BattleModeRuntime.durationExpired", false);

        if (hero.getOwnerUUID() != null && hero.getServer() != null) {
            ServerPlayer owner = hero.getServer().getPlayerList().getPlayer(hero.getOwnerUUID());
            if (owner != null) {
                long cooldownSeconds = BattleModeService.cooldownRemainingTicks(hero) / 20L;
                owner.sendSystemMessage(cooldownSeconds > 0
                        ? Component.translatable("message.herobrine_companion.battle_mode_expired_cooldown", cooldownSeconds)
                        : Component.translatable("message.herobrine_companion.battle_mode_expired"));
            }
        }
    }
}
