package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.api.compat.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatTargeting;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroHostileTargetPolicy;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 无 Epic Fight 时的近战战斗 AI。
 *
 * <p>攻击段完全由终末之诗的同步动作库驱动（{@link HeroPoemCombat}：动作、判定、
 * 位移与连段一次性对齐）。此前那套按固定帧表推进、并按主手武器选择程序化挥砍姿势的
 * 「默认回退攻击」已废弃删除——主手换成别的武器时动作依旧是终末之诗这一套。</p>
 */
public class HeroBattleStanceGoal extends Goal {
    private static final double SEARCH_RANGE = 24.0D;
    /**
     * 陪伴模式没敌人时让位给跟随 AI 的冷却（tick）。
     * 两个 Goal 同优先级：让位期间战斗 Goal 不再参评，跟随才能稳定拿到 MOVE/LOOK。
     */
    private static final int REENGAGE_INTERVAL_TICKS = 10;

    private final HeroEntity hero;
    private final HeroPoemCombat poemCombat;
    private LivingEntity target;
    private int retargetCooldown;
    private int reengageAtTick;

    public HeroBattleStanceGoal(HeroEntity hero) {
        this.hero = hero;
        this.poemCombat = new HeroPoemCombat(hero);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // Attack/contact/link times are game ticks, just like the client's animation clock.
        return true;
    }

    @Override
    public boolean canUse() {
        if (!isBattleModeActive()) {
            return false;
        }
        // 非陪伴模式：战斗模式独占，行为与之前一致。
        if (!this.hero.isCompanionMode()) {
            return true;
        }
        // 陪伴 + 战斗：刚让位给跟随的这段时间不抢控制权，避免两个 Goal 每 tick 互相打断。
        if (this.hero.tickCount < this.reengageAtTick) {
            return false;
        }
        // 目标由 targetSelector 定期挑选，这里只看手上有没有活着的可攻击目标，
        // 免得 Goal 评估阶段每 tick 做一次 AABB 扫描。
        return HeroCombatTargeting.hasEngagedTarget(this.hero);
    }

    @Override
    public boolean canContinueToUse() {
        if (!isBattleModeActive()) {
            return false;
        }
        if (!this.hero.isCompanionMode()) {
            return true;
        }
        // 陪伴 + 战斗同时开启：还有敌人就继续杀；附近清空了就把 MOVE/LOOK 交回跟随 AI。
        // 让位冷却在 stop() 里记账（canContinueToUse 只做判断，不写状态）。
        return HeroCombatTargeting.hasEngagedTarget(this.hero);
    }

    @Override
    public void start() {
        this.retargetCooldown = 0;
        this.hero.setFloating(false);
        this.hero.setNoGravity(false);
        this.hero.setAggressive(true);
        this.target = pickBattleTarget();
        this.hero.resetBattleCombatState();
    }

    @Override
    public void stop() {
        this.poemCombat.stop();
        this.target = null;
        this.hero.setTarget(null);
        this.hero.getNavigation().stop();
        this.hero.setSprinting(false);
        if (this.hero.isCompanionMode() && this.hero.isBattleModeActive()) {
            // 只是让位给陪伴跟随：战斗模式仍然开着，不收刀、不摘虚拟镰刀，
            // 只清掉这一段的攻击时间线，冷却结束后再接敌。
            this.reengageAtTick = this.hero.tickCount + REENGAGE_INTERVAL_TICKS;
            this.hero.resetBattleActionTimeline();
            return;
        }
        this.hero.setAutoBattleScythe(false);
        this.hero.setAggressive(false);
        this.hero.resetBattleCombatState();
    }

    @Override
    public void tick() {
        // 自动镰刀：主手为空才启用（虚拟持镰，不占装备栏位）；玩家给的任何物品都优先。
        boolean autoScythe = this.hero.getMainHandItem().isEmpty();
        if (this.hero.isAutoBattleScythe() != autoScythe) {
            this.hero.setAutoBattleScythe(autoScythe);
        }

        this.hero.tickBattleBufferedAction();

        LivingEntity prioritizedTarget = pickBattleTarget();
        if (prioritizedTarget != null) {
            this.target = prioritizedTarget;
        }

        if (this.retargetCooldown > 0) {
            this.retargetCooldown--;
        }

        if (!isValidTarget(this.target) || this.hero.distanceToSqr(this.target) > SEARCH_RANGE * SEARCH_RANGE) {
            this.target = findNearestHostile();
            this.retargetCooldown = 10;
        } else if (this.retargetCooldown <= 0) {
            LivingEntity nearbyBetterTarget = findNearestHostile();
            if (nearbyBetterTarget != null && nearbyBetterTarget != this.target
                    && this.hero.distanceToSqr(nearbyBetterTarget) + 4.0D < this.hero.distanceToSqr(this.target)) {
                this.target = nearbyBetterTarget;
            }
            this.retargetCooldown = 10;
        }

        this.hero.setTarget(this.target);
        clearSubmission(this.target);

        if (this.target == null) {
            this.poemCombat.stop();
            this.hero.getNavigation().stop();
            this.hero.resetBattleActionTimeline();
            return;
        }

        this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        this.poemCombat.tick(this.target, getAttackReachSqr(this.target));
    }

    private boolean isBattleModeActive() {
        return this.hero.isBattleModeActive()
                && !this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && !HeroEpicFightCompat.shouldUseEpicFightCombatAI(this.hero)
                && !HeroCombatWeaponHelper.isRangedLoadout(this.hero);
    }

    private LivingEntity pickBattleTarget() {
        return HeroCombatTargeting.pickBattleTarget(this.hero, this.target);
    }

    /** 目标获取已收敛到 {@link HeroCombatTargeting}（近战/远程共用同一份）。 */
    private LivingEntity findNearestHostile() {
        return HeroCombatTargeting.findNearestHostile(this.hero, this.target);
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return HeroCombatTargeting.canHeroAttackTarget(this.hero, candidate);
    }

    private void clearSubmission(LivingEntity candidate) {
        HeroCombatTargeting.clearSubmission(candidate);
    }

    /**
     * 战斗模式的攻击白名单闸门：所有选目标/结算伤害的路径都收敛到这里，
     * 避免换个 Goal 就绕过「不打原版怪、不打觉醒怪」的规则。
     * 原版生物与觉醒怪物的判定见 {@link HeroHostileTargetPolicy}。
     *
     * <p>实现已移到 {@link HeroCombatTargeting}（近战/远程共用一份）；本方法保留为
     * 转发，因为 {@code HeroAI} 与 {@code HeroCombatProtection} 仍在调用这个公开入口。</p>
     */
    public static boolean canHeroAttackTarget(HeroEntity hero, LivingEntity candidate) {
        return HeroCombatTargeting.canHeroAttackTarget(hero, candidate);
    }

    private double getAttackReachSqr(LivingEntity target) {
        double reach = Math.max(2.6D, (this.hero.getBbWidth() * 2.0F) + target.getBbWidth());
        return reach * reach;
    }
}
