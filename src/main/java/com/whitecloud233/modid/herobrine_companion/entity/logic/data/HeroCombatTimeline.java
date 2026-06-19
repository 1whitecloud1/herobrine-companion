package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightDebugLog;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;

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
            HeroEpicFightDebugLog.event(hero, "HeroEntity.resetBattleActionTimeline", 
                    "stateBefore=" + HeroEpicFightDebugLog.heroCoreState(hero) 
                    + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
        }
        this.clearBattleBufferedAction();
        hero.setBattleActionState(HeroEntity.BATTLE_ACTION_IDLE);
        hero.setBattleActionTicks(0);
    }

    public void resetBattleCombatState(HeroEntity hero) {
        if (hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_IDLE || hero.getBattleActionTicks() != 0 || hero.getBattleComboStep() != 0 || this.hasBattleBufferedAction()) {
            HeroEpicFightDebugLog.event(hero, "HeroEntity.resetBattleCombatState", 
                    "stateBefore=" + HeroEpicFightDebugLog.heroCoreState(hero) 
                    + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
        }
        this.resetBattleActionTimeline(hero);
        hero.setBattleComboStep(0);
        HeroEpicFightDebugLog.transition(hero, "HeroEntity.resetBattleCombatState.end", 
                HeroEpicFightDebugLog.heroCoreState(hero), "state=" + HeroEpicFightDebugLog.heroCoreState(hero));
    }
}
