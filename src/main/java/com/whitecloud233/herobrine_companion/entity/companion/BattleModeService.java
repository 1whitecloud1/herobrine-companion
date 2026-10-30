package com.whitecloud233.herobrine_companion.entity.companion;

import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.fight.ChallengeSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * 战斗模式玩家请求的唯一入口：策略判定 + 周期记账。
 *
 * <p>周期（持续时长/冷却）只挂在本服务写入的会话上：开启时记起始刻与到期刻，
 * 收刀（手动或 {@link BattleModeRuntime} 到时收刀）时清会话并记冷却截止。
 * 内部直调 {@code setBattleModeActive} 的路径（技能桥、存档恢复等）不进周期，
 * 避免被误计时或误触发冷却。</p>
 */
public final class BattleModeService {
    static final String TAG_START = "HeroBattleModeStartTick";
    static final String TAG_UNTIL = "HeroBattleModeUntilTick";
    static final String TAG_NEXT_ALLOWED = "HeroBattleModeNextAllowedTick";

    private BattleModeService() {
    }

    /** requester 必须来自服务端收包上下文或内部 Agent 上下文，不能取自客户端载荷。 */
    public static BattleModePolicy.Result changeMode(UUID requester, HeroEntity hero, boolean desired) {
        if (hero == null || requester == null
                || !(hero.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return BattleModePolicy.Result.UNAVAILABLE;
        }

        BattleModePolicy.Result result = BattleModePolicy.evaluate(
                new BattleModePolicy.State(hero.getOwnerUUID(), requester, hero.getTrustLevel(), trustThreshold(),
                        hero.isBattleModeActive(), hero.isAlive() && !hero.isRemoved(),
                        ChallengeSession.isHeroInChallenge(hero), cooldownRemainingTicks(hero)),
                desired);
        if (result != BattleModePolicy.Result.CHANGED) return result;

        long now = level.getGameTime();
        if (desired) {
            CompoundTag data = hero.getPersistentData();
            data.putLong(TAG_START, now);
            int durationTicks = durationTicks();
            if (durationTicks > 0) data.putLong(TAG_UNTIL, now + durationTicks);
            else data.remove(TAG_UNTIL);
            data.remove(TAG_NEXT_ALLOWED);
            hero.setBattleModeActiveFrom("BattleModeService.changeMode", true);
        } else {
            endSession(hero, now);
            hero.setBattleModeActiveFrom("BattleModeService.changeMode", false);
        }
        return result;
    }

    /** 收刀：清掉本次会话标记并记录冷却截止（冷却为 0 时不记录）。 */
    static void endSession(HeroEntity hero, long now) {
        CompoundTag data = hero.getPersistentData();
        data.remove(TAG_START);
        data.remove(TAG_UNTIL);
        int cooldownTicks = cooldownTicks();
        if (cooldownTicks > 0) data.putLong(TAG_NEXT_ALLOWED, now + cooldownTicks);
        else data.remove(TAG_NEXT_ALLOWED);
    }

    public static int trustThreshold() {
        return ServerConfigAccess.snapshot().value(ServerRule.BATTLE_MODE_TRUST_THRESHOLD);
    }

    /** 单次战斗姿态的时长上限（tick），0 表示不限时。 */
    public static int durationTicks() {
        return ServerConfigAccess.snapshot().value(ServerRule.BATTLE_MODE_DURATION_SECONDS) * 20;
    }

    /** 收刀后的冷却时长（tick），0 表示无冷却。 */
    public static int cooldownTicks() {
        return ServerConfigAccess.snapshot().value(ServerRule.BATTLE_MODE_COOLDOWN_SECONDS) * 20;
    }

    public static long cooldownRemainingTicks(HeroEntity hero) {
        if (hero == null) return 0L;
        return Math.max(0L, hero.getPersistentData().getLong(TAG_NEXT_ALLOWED) - hero.level().getGameTime());
    }
}
