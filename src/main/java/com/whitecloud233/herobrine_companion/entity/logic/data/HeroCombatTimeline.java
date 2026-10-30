package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.combat.HeroCombatDebugLog;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPower;
import com.whitecloud233.herobrine_companion.util.NbtKeys;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import static com.whitecloud233.herobrine_companion.entity.HeroEntity.*;

public class HeroCombatTimeline {

    private int battleBufferedAction = 0; // BATTLE_BUFFER_NONE
    private int battleBufferTicks = 0;

    public int getBattleBufferedAction() {
        return this.battleBufferedAction;
    }

    public int getBattleBufferTicks() {
        return this.battleBufferTicks;
    }

    public boolean hasBattleBufferedAction() {
        return this.battleBufferedAction != 0 && this.battleBufferTicks > 0;
    }

    public void queueBattleBufferedAction(HeroEntity hero, int bufferedAction, int ticks) {
        if (!hero.isBattleModeActive() || bufferedAction == 0 || ticks <= 0) {
            this.clearBattleBufferedAction();
            return;
        }

        this.battleBufferedAction = bufferedAction;
        this.battleBufferTicks = Math.min(20, ticks);
    }

    public void tickBattleBufferedAction() {
        if (!this.hasBattleBufferedAction()) {
            this.clearBattleBufferedAction();
            return;
        }

        this.battleBufferTicks--;
        if (this.battleBufferTicks <= 0) {
            this.clearBattleBufferedAction();
        }
    }

    public void clearBattleBufferedAction() {
        this.battleBufferedAction = 0;
        this.battleBufferTicks = 0;
    }

    public void resetBattleActionTimeline(HeroEntity hero) {
        if (hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_IDLE || hero.getBattleActionTicks() != 0 || this.hasBattleBufferedAction()) {
            if (HeroCombatDebugLog.enabled()) {
                HeroCombatDebugLog.event(hero, "HeroEntity.resetBattleActionTimeline", 
                        "stateBefore=" + HeroCombatDebugLog.heroCoreState(hero) 
                        + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
            }
        }
        this.clearBattleBufferedAction();
        hero.setBattleActionState(HeroEntity.BATTLE_ACTION_IDLE);
        hero.setBattleActionTicks(0);
        hero.setBattlePoemMove(-1);
    }

    public void resetBattleCombatState(HeroEntity hero) {
        if (hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_IDLE || hero.getBattleActionTicks() != 0 || hero.getBattleComboStep() != 0 || this.hasBattleBufferedAction()) {
            if (HeroCombatDebugLog.enabled()) {
                HeroCombatDebugLog.event(hero, "HeroEntity.resetBattleCombatState", 
                        "stateBefore=" + HeroCombatDebugLog.heroCoreState(hero) 
                        + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
            }
        }
        this.resetBattleActionTimeline(hero);
        hero.setBattleComboStep(0);
        if (HeroCombatDebugLog.enabled()) {
            HeroCombatDebugLog.transition(hero, "HeroEntity.resetBattleCombatState.end", 
                    HeroCombatDebugLog.heroCoreState(hero), "state=" + HeroCombatDebugLog.heroCoreState(hero));
        }
    }

    public void applyBattleMode(HeroEntity hero, String callerTag, boolean active) {
        boolean previous = hero.isBattleModeActive();
        boolean debug = HeroCombatDebugLog.enabled();
        String entityIdentity = debug ? HeroCombatDebugLog.heroIdentity(hero) : "";
        String trace = debug ? HeroCombatDebugLog.captureCallerTrace(5) : "";
        if (previous == active) {
            if (debug) {
                HeroCombatDebugLog.repeatedEvent(hero, "HeroEntity.setBattleModeActive.noop",
                        callerTag + "|" + active + "|" + entityIdentity,
                        "caller=" + callerTag + ",prev=" + previous + ",next=" + active
                                + ",entity=" + entityIdentity + ",state=" + HeroCombatDebugLog.heroCoreState(hero)
                                + ",trace=" + trace);
            }
            return;
        }
        if (debug) {
            HeroCombatDebugLog.event(hero, "HeroEntity.setBattleModeActive.begin",
                    "caller=" + callerTag + ",prev=" + previous + ",next=" + active
                            + ",entity=" + entityIdentity + ",state=" + HeroCombatDebugLog.heroCoreState(hero)
                            + ",trace=" + trace);
        }
        hero.getEntityData().set(BATTLE_MODE_ACTIVE, active);
        if (active) {
            clearNearbySubmissionEffects(hero);
            hero.setFloating(false);
            hero.setNoGravity(false);
            hero.resetBattleCombatState();
        } else {
            hero.setTarget(null);
            hero.getNavigation().stop();
            hero.resetBattleCombatState();
            hero.setAutoBattleScythe(false);
        }
        // 战斗攻击力固定继承终末之诗（默认值 + 信任度加成）：开战即生效，收刀即归还。
        if (active) HeroCombatPower.applyTo(hero);
        else HeroCombatPower.clearFrom(hero);
        if (debug) {
            HeroCombatDebugLog.transition(hero, "HeroEntity.setBattleModeActive.end",
                    callerTag + "|" + active + "|" + HeroCombatDebugLog.heroCoreState(hero),
                    "caller=" + callerTag + ",entity=" + HeroCombatDebugLog.heroIdentity(hero)
                            + ",state=" + HeroCombatDebugLog.heroCoreState(hero));
        }
    }

    public void beginBattleAction(HeroEntity hero, int state) {
        this.beginBattleAction(hero, state, null);
    }

    public void beginBattleAction(HeroEntity hero, int state,
                                  @javax.annotation.Nullable com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner.ActionProfile profile) {
        if (HeroCombatDebugLog.enabled()) {
            HeroCombatDebugLog.event(hero, "HeroEntity.beginBattleAction", "nextAction=" + state + ",stateBefore=" + HeroCombatDebugLog.heroCoreState(hero));
        }
        hero.setCurrentBattleActionProfile(profile);
        hero.clearBattleBufferedAction();
        hero.setBattleActionState(state);
        hero.setBattlePoemMove(-1);
        hero.setBattleActionTicks(0);
        if (state == BATTLE_ACTION_LIGHT_COMBO_1 || state == BATTLE_ACTION_LIGHT_COMBO_2) {
            hero.setBattleActionSerial(hero.getBattleActionSerial() + 1);
        }
        if (HeroCombatDebugLog.enabled()) {
            HeroCombatDebugLog.transition(hero, "HeroEntity.beginBattleAction.end", state + "|" + hero.getBattleActionSerial() + "|" + hero.getBattleComboStep(), "state=" + HeroCombatDebugLog.heroCoreState(hero) + ",serial=" + hero.getBattleActionSerial());
        }
    }

    private static void clearNearbySubmissionEffects(HeroEntity hero) {
        if (hero.level() == null) {
            return;
        }

        double clearRange = Math.max(24.0D, hero.getAttributeValue(Attributes.FOLLOW_RANGE));
        for (Mob mob : hero.level().getEntitiesOfClass(Mob.class, hero.getBoundingBox().inflate(clearRange), mob -> mob.isAlive() && mob != hero)) {
            mob.getPersistentData().putBoolean(NbtKeys.MOB_SUBMISSION, false);
            mob.getPersistentData().remove(NbtKeys.MOB_SUBMISSION_YAW);
            if (mob.isSilent()) {
                mob.setSilent(false);
            }
        }
    }
}
