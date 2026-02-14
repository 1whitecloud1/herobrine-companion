package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroQuestHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class HeroPacifyAttackerGoal extends Goal {
    private final HeroEntity hero;
    private LivingEntity targetMonster;
    private int cooldown;

    public HeroPacifyAttackerGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        
        if (this.hero.getOwnerUUID() == null) return false;
        Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        if (owner == null) return false;

        // [深度学习] 检查心智状态
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        
        // 怪物之王：拒绝安抚，甚至可能在旁边看戏
        if (state == SimpleNeuralNetwork.MindState.MONSTER_KING) return false;
        // 审判者：拒绝安抚，让玩家受苦
        if (state == SimpleNeuralNetwork.MindState.JUDGE) return false;
        // 追忆者：抑郁中，不理世事
        if (state == SimpleNeuralNetwork.MindState.REMINISCING) return false;

        // [新增] 检查是否在任务模式
        boolean isQuesting = HeroQuestHandler.isPlayerDoingQuest(owner);

        List<Mob> list = this.hero.level().getEntitiesOfClass(Mob.class, this.hero.getBoundingBox().inflate(10.0D),
                e -> {
                    // 基础条件：攻击主人的活着的非Hero生物
                    if (e.getTarget() != owner || !e.isAlive() || e instanceof HeroEntity) return false;

                    // [修改] 如果在任务模式，使用 HeroQuestHandler 判断是否忽略任务目标怪物
                    if (isQuesting) {
                        if (HeroQuestHandler.shouldIgnoreTarget(e)) {
                            return false; // 不要安抚它们，让玩家去打
                        }
                    }
                    return true;
                });
        
        if (list.isEmpty()) return false;
        
        this.targetMonster = list.get(0);
        return true;
    }

    @Override
    public void start() {
        this.hero.getLookControl().setLookAt(this.targetMonster, 30.0F, 30.0F);
    }

    @Override
    public void tick() {
        if (this.targetMonster != null && this.targetMonster.isAlive()) {
            this.hero.getLookControl().setLookAt(this.targetMonster, 30.0F, 30.0F);
            
            if (this.hero.distanceToSqr(this.targetMonster) > 4.0D) {
                this.hero.getNavigation().moveTo(this.targetMonster, 1.5D);
            } else {
                if (this.targetMonster instanceof Mob mob) {
                    mob.setTarget(null);
                }
                if (this.targetMonster instanceof NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
                
                Vec3 vec3 = this.targetMonster.position().subtract(this.hero.position()).normalize().scale(0.5);
                this.targetMonster.push(vec3.x, 0.2, vec3.z);
                
                if (this.hero.tickCount % 5 == 0) {
                    this.hero.level().addParticle(ParticleTypes.WITCH, this.targetMonster.getX(), this.targetMonster.getY() + 1, this.targetMonster.getZ(), 0, 0, 0);
                }
            }
        }
    }
    
    @Override
    public boolean canContinueToUse() {
        if (this.targetMonster == null || !this.targetMonster.isAlive()) return false;
        if (this.hero.getOwnerUUID() == null) return false;
        Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        if (owner == null) return false;
        
        if (this.targetMonster instanceof Mob mob) {
            return mob.getTarget() == owner;
        }
        return false;
    }
    
    @Override
    public void stop() {
        if (this.targetMonster != null && this.hero.getOwnerUUID() != null) {
            Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
            if (owner instanceof ServerPlayer serverPlayer) {
                HeroDialogueHandler.onPacifyMonster(this.hero, serverPlayer);
            }
        }
        this.targetMonster = null;
        this.cooldown = 20;
    }
}
