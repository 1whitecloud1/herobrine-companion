package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 强制技能会话追踪（P6）：当 {@code hero_use_skill} 工具<b>自动进入战斗态</b>施放技能时，
 * 记录一个会话；动画结束后若 Hero 无战斗目标，则把战斗态退出，避免"打完技能永久留在战斗姿态"。
 *
 * <p><b>单一职责</b>：只做"开始/推进/结束会话"；判定推进由 {@code HeroNightfallSkillTicker}
 * 每 tick 调用 {@link #tick}。</p>
 */
public final class HeroSkillSessionTracker {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private HeroSkillSessionTracker() {
    }

    private record Session(HeroNightfallSkillSeries series, boolean autoEnteredBattleMode) {
    }

    /** 记录一个强制技能会话。 */
    public static void begin(HeroEntity hero, HeroNightfallSkillSeries series, boolean autoEnteredBattleMode) {
        if (hero == null || series == null) {
            return;
        }
        SESSIONS.put(hero.getUUID(), new Session(series, autoEnteredBattleMode));
    }

    /**
     * 每 tick 推进：当前动画仍匹配该系列 → 施放中；否则会话结束，
     * 且若是工具自动进的战斗态且 Hero 无目标 → 退出战斗态。
     */
    public static void tick(HeroEntity hero, @Nullable AssetAccessor<? extends StaticAnimation> currentAnimation) {
        Session session = hero == null ? null : SESSIONS.get(hero.getUUID());
        if (session == null) {
            return;
        }
        if (currentAnimation != null && session.series != null && session.series.matches(currentAnimation)) {
            return; // 仍在施放
        }
        endSession(hero, session);
    }

    /** 强制结束（武器更换 / 调试）。 */
    public static void clear(HeroEntity hero) {
        if (hero != null) {
            SESSIONS.remove(hero.getUUID());
        }
    }

    private static void endSession(HeroEntity hero, Session session) {
        SESSIONS.remove(hero.getUUID());
        if (session.autoEnteredBattleMode() && hero.isAlive() && hero.getTarget() == null) {
            hero.setBattleModeActiveFrom("HeroSkillSessionTracker.endSession", false);
        }
    }
}
