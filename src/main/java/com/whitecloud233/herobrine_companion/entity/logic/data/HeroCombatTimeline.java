package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;

public class HeroCombatTimeline {
   private int battleBufferedAction = 0;
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
      if (hero.isBattleModeActive() && bufferedAction != 0 && ticks > 0) {
         this.battleBufferedAction = bufferedAction;
         this.battleBufferTicks = Math.min(20, ticks);
      } else {
         this.clearBattleBufferedAction();
      }
   }

   public void tickBattleBufferedAction() {
      if (!this.hasBattleBufferedAction()) {
         this.clearBattleBufferedAction();
      } else {
         --this.battleBufferTicks;
         if (this.battleBufferTicks <= 0) {
            this.clearBattleBufferedAction();
         }

      }
   }

   public void clearBattleBufferedAction() {
      this.battleBufferedAction = 0;
      this.battleBufferTicks = 0;
   }

   public void resetBattleActionTimeline(HeroEntity hero) {
      this.clearBattleBufferedAction();
      hero.setBattleActionState(0);
      hero.setBattleActionTicks(0);
      hero.setCurrentBattleActionProfile((HeroCombatPlanner.ActionProfile)null);
   }

   public void resetBattleCombatState(HeroEntity hero) {
      this.resetBattleActionTimeline(hero);
      hero.setBattleComboStep(0);
   }
}
