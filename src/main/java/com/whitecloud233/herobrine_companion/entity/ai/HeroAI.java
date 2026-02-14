package com.whitecloud233.herobrine_companion.entity.ai;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroFloatingFlyGoal;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroGodlyCompanionGoal;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroIdleActionGoal;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroKingAuraGoal;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroSwitchStateGoal;
import com.whitecloud233.herobrine_companion.entity.ai.learning.*;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class HeroAI {

    public static void registerGoals(HeroEntity hero) {
        // 0. 基础生存
        hero.getGoalSelector().addGoal(0, new FloatGoal(hero));
        // [新增] 王者光环：让周围怪物臣服 (提升至最高优先级 0，被动生效)
        hero.getGoalSelector().addGoal(0, new HeroKingAuraGoal(hero));
        
        hero.getGoalSelector().addGoal(1, new HeroTeleportToPlayerGoal(hero));
        // 0.5 [新增] 玩家邀请互动 (最高优先级之一，响应玩家指令)
        hero.getGoalSelector().addGoal(1, new HeroInvitedActionGoal(hero));
        // 1. [Lore] 修复异常 (最高优先级)
        hero.getGoalSelector().addGoal(1, new HeroFixAnomalyGoal(hero));

        // 2. [Lore] 陪伴跟随 AI (优先级 2)
        // 使用新的 GodlyCompanionGoal，速度设为 1.2D
        hero.getGoalSelector().addGoal(2, new HeroGodlyCompanionGoal(hero, 1.2D));
        
        // [修改] 将待机动作优先级提升至 2，与跟随同级
        // 当跟随停止时（距离近），或者非跟随模式下，它将优先于乱逛(4)和巡视(3)
        hero.getGoalSelector().addGoal(2, new HeroIdleActionGoal(hero));
        
        // 2.1 [Lore] 保护玩家 (安抚怪物)
        hero.getGoalSelector().addGoal(2, new HeroPacifyAttackerGoal(hero));
        // 2.2 [Lore] 赠送礼物 (优先级 2，与跟随并行，但执行时会短暂停留)
        hero.getGoalSelector().addGoal(2, new HeroGiftPlayerGoal(hero));

        // 2.3 [Lore] 巡视世界 (优先级 3，空闲时触发)
        hero.getGoalSelector().addGoal(3, new HeroInspectBlockGoal(hero));

        // 2.4 [Lore] 恶作剧：熄灭火把 (优先级 3，极低概率)
        hero.getGoalSelector().addGoal(3, new HeroExtinguishTorchGoal(hero));
        
        // 3. 切换状态 AI (互斥限制)
        hero.getGoalSelector().addGoal(3, new HeroSwitchStateGoal(hero) {
            @Override
            public boolean canUse() {
                return !hero.isCompanionMode() && super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                return !hero.isCompanionMode() && super.canContinueToUse();
            }
        });

        // 4. 浮空 AI (互斥限制)
        hero.getGoalSelector().addGoal(4, new HeroFloatingFlyGoal(hero) {
            @Override public boolean canUse() {
                return !hero.isCompanionMode() && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return !hero.isCompanionMode() && super.canContinueToUse();
            }
        });

        // 5. 地面乱逛 AI (互斥限制)
        hero.getGoalSelector().addGoal(4, new WaterAvoidingRandomStrollGoal(hero, 1.0D) {
            @Override
            public boolean canUse() {
                return !hero.isCompanionMode() && !hero.isFloating() && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return !hero.isCompanionMode() && super.canContinueToUse();
            }
        });

        // 6. 观察
        // [修改] 降低 LookAtPlayerGoal 的优先级，或者移除它，因为它会强制头部看向玩家
        // 如果玩家在下方，Hero 就会一直低头
        // 我们可以保留 RandomLookAroundGoal，但 LookAtPlayerGoal 可能是罪魁祸首
        // 尝试将 LookAtPlayerGoal 的优先级调低，或者只在特定状态下启用

        // 暂时注释掉 LookAtPlayerGoal，看看是否解决“强迫向下看”的问题
        // hero.getGoalSelector().addGoal(5, new LookAtPlayerGoal(hero, Player.class, 8.0F));

        // 保留随机看周围，这比较自然
        hero.getGoalSelector().addGoal(6, new RandomLookAroundGoal(hero));
    }
}