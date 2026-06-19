package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

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
        this.clearBattleBufferedAction();
        hero.setBattleActionState(HeroEntity.BATTLE_ACTION_IDLE);
        hero.setBattleActionTicks(0);
        hero.setCurrentBattleActionProfile(null);
    }

    public void resetBattleCombatState(HeroEntity hero) {
        this.resetBattleActionTimeline(hero);
        hero.setBattleComboStep(0);
    }
}
