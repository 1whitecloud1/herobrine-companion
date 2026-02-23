package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HeroIdleActionGoal extends Goal {
    private final HeroEntity hero;
    private int idleTime;
    private IdleAction currentAction;
    private boolean wasFloating; // To restore state
    private boolean wasInvisible; // To restore state
    private float originalYRot; // Restore rotation after glitch
    private float originalXRot;
    
    // [新增] 记录故障开始时的原始位置，用于在范围内闪现
    private double startX;
    private double startY;
    private double startZ;
    
    // [新增] 间歇性故障控制
    private int glitchBurstTimer = 0; // 当前这一波故障还剩多少时间
    private int glitchCooldownTimer = 0; // 距离下一波故障还有多少时间

    private enum IdleAction {
        NONE,
        MEDITATE,
        GLITCH,
        INSPECT_SCYTHE,
        DEBUG_INTERFACE, 
        THUNDER_CAST // [修改] 替换 STATIC_CHARGE 为 THUNDER_CAST
    }

    public HeroIdleActionGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. 如果有攻击目标，绝对不执行
        if (this.hero.getTarget() != null) {
            return false;
        }
        
        // 2. 如果在水中，不执行
        if (this.hero.isInWater()) {
            return false;
        }

        // 3. 跟随模式下的特殊检查
        if (this.hero.isCompanionMode()) {
             if (this.hero.getOwnerUUID() != null) {
                 if (this.hero.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
                     return false;
                 }
             }
        }

        // [新增] 如果处于邀请状态 (InvitedPos 不为空)，绝对不执行待机动作
        if (this.hero.getInvitedPos() != null) {
            return false;
        }
        
        // 触发概率：
        return this.hero.getRandom().nextInt(3000) == 0;
    }

    @Override
    public void start() {
        this.wasFloating = this.hero.isFloating();
        this.wasInvisible = this.hero.isInvisible();
        this.originalYRot = this.hero.getYRot();
        this.originalXRot = this.hero.getXRot();
        
        // [新增] 记录初始位置
        this.startX = this.hero.getX();
        this.startY = this.hero.getY();
        this.startZ = this.hero.getZ();
        
        // [修改] 调整动作权重，大幅提高抚摸镰刀的概率

        int roll = this.hero.getRandom().nextInt(100);

        if (roll < 30) {
            this.currentAction = IdleAction.INSPECT_SCYTHE;
            // [修改] 延长至 160 tick (8秒)
            this.idleTime = 160;
            this.hero.playScytheInspectAnim();
        } else if (roll < 40) {
            this.currentAction = IdleAction.MEDITATE;
            this.idleTime = 120 + this.hero.getRandom().nextInt(80);
        } else if (roll < 60) {
            this.currentAction = IdleAction.GLITCH;
            this.idleTime = 60 + this.hero.getRandom().nextInt(40);
            // [新增] 开启故障渲染
            this.hero.setGlitching(true);
            // [新增] 初始化间歇性故障计时器
            this.glitchBurstTimer = 6; // 初始爆发 0.3 秒
            this.glitchCooldownTimer = 0;
        } else if (roll < 80) {
            this.currentAction = IdleAction.DEBUG_INTERFACE;
            this.idleTime = 100; // 5秒
            this.hero.playDebugAnim(); // 触发实体动画和粒子
        } else {
            // 40-99 (60%) 概率触发雷电召唤
            this.currentAction = IdleAction.THUNDER_CAST;
            this.idleTime = 100; // 3秒
            this.hero.castThunder(); // 触发雷电召唤动画
        }

        // 强制停止当前的移动
        this.hero.getNavigation().stop();
        this.hero.setDeltaMovement(0, 0, 0);
        
        // [调试] 播放声音提示
        if (this.hero.getTags().contains("brain_debug")) {
            this.hero.playSound(SoundEvents.UI_TOAST_IN, 1.0f, 1.0f);
            this.hero.setCustomName(Component.literal("Idle: " + this.currentAction.name()));
            this.hero.setCustomNameVisible(true);
        }
    }

    @Override
    public boolean canContinueToUse() {
        // [新增] 如果中途进入邀请状态，立即停止
        if (this.hero.getInvitedPos() != null) {
            return false;
        }
        return this.idleTime > 0;
    }

    @Override
    public void tick() {
        this.idleTime--;
        
        // 再次强制停止移动
        if (!this.hero.getNavigation().isDone()) {
             this.hero.getNavigation().stop();
        }
        
        switch (this.currentAction) {
            case MEDITATE:
                tickMeditate();
                break;
            case GLITCH:
                tickGlitch();
                break;
            case INSPECT_SCYTHE:
                // 动画逻辑由 Entity/Model 处理
                break;
            case DEBUG_INTERFACE:
                // 动画逻辑由 Entity/Model 处理
                break;
            case THUNDER_CAST:
                // 动画逻辑由 Entity/Model 处理
                break;
        }
    }
    
    @Override
    public void stop() {
        // 恢复状态
        if (this.currentAction == IdleAction.MEDITATE) {
            if (!this.wasFloating) {
                this.hero.setFloating(false);
                this.hero.setNoGravity(false); 
                
                // 防止陷地
                this.hero.setPos(this.hero.getX(), this.hero.getY() + 0.5, this.hero.getZ());
                this.hero.setDeltaMovement(this.hero.getDeltaMovement().multiply(1, 0, 1));
            }
        } else if (this.currentAction == IdleAction.GLITCH) {
            // 恢复隐身状态和朝向
            if (!this.wasInvisible) {
                this.hero.setInvisible(false);
            }
            this.hero.setYRot(this.originalYRot);
            this.hero.setXRot(this.originalXRot);
            // [新增] 关闭故障渲染
            this.hero.setGlitching(false);
            
            // [新增] 恢复到初始位置，避免卡墙
            this.hero.setPos(this.startX, this.startY, this.startZ);
        }
        
        // [调试] 检查是否异常中断
        if (this.idleTime > 5 && this.hero.getTags().contains("brain_debug")) {
             this.hero.setCustomName(Component.literal("Interrupted! T:" + this.idleTime));
        } else if (this.hero.getTags().contains("brain_debug")) {
            this.hero.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
        }
        
        this.currentAction = IdleAction.NONE;
    }

    private void tickMeditate() {
        if (!this.hero.isFloating()) this.hero.setFloating(true);
        if (!this.hero.isNoGravity()) this.hero.setNoGravity(true);
        
        // 缓慢的上下浮动
        double yMotion = Math.sin(this.hero.tickCount * 0.1) * 0.02;
        this.hero.setDeltaMovement(0, yMotion, 0);
        
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            // [增强] 1. 底部光环：龙息粒子
            if (this.hero.tickCount % 2 == 0) {
                double radius = 1.0;
                double angle = (this.hero.tickCount * 0.2) % (Math.PI * 2);
                double x = this.hero.getX() + Math.cos(angle) * radius;
                double z = this.hero.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, x, this.hero.getY(), z, 1, 0, 0, 0, 0.01);
            }
            
            // [增强] 2. 聚集能量：传送门粒子 (自动飞向实体)
            if (this.hero.tickCount % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, 
                    this.hero.getX() + (this.hero.getRandom().nextDouble() - 0.5) * 2.0,
                    this.hero.getY() + this.hero.getRandom().nextDouble() * 2.0,
                    this.hero.getZ() + (this.hero.getRandom().nextDouble() - 0.5) * 2.0,
                    2, 0, 0, 0, 1.0 // speed 1.0 for portal particles means they move towards origin
                );
            }
            
            // [增强] 3. 偶尔的附魔符文
            if (this.hero.tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT, 
                    this.hero.getX(), this.hero.getY() + 1.5, this.hero.getZ(), 
                    5, 0.5, 0.5, 0.5, 0.1
                );
            }
        }
    }

    private void tickGlitch() {
        // [新增] 间歇性故障逻辑
        if (this.glitchBurstTimer > 0) {
            // --- 处于爆发期：疯狂闪现 ---
            this.glitchBurstTimer--;
            
            // 确保渲染状态开启
            if (!this.hero.isGlitching()) this.hero.setGlitching(true);

            // 每 tick 都瞬移
            double range = 0.5; // 闪现半径
            double targetX = this.startX + (this.hero.getRandom().nextDouble() - 0.5) * 1.0 * range;
            double targetY = this.startY + (this.hero.getRandom().nextDouble() - 0.5) * 0; 
            double targetZ = this.startZ + (this.hero.getRandom().nextDouble() - 0.5) * 1.0 * range;
            
            this.hero.setPos(targetX, targetY, targetZ);
            
            float rotJitter = (this.hero.getRandom().nextFloat() - 0.5f) * 180.0f;
            this.hero.setYRot(this.hero.getYRot() + rotJitter);
            this.hero.setXRot((this.hero.getRandom().nextFloat() - 0.5f) * 60.0f);
            
            if (this.hero.getRandom().nextInt(3) == 0) {
                this.hero.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.1f, 1.5f + this.hero.getRandom().nextFloat());
            }
            
            if (this.hero.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, 
                    this.hero.getX() + (this.hero.getRandom().nextDouble() - 0.5),
                    this.hero.getY() + this.hero.getRandom().nextDouble() * 2,
                    this.hero.getZ() + (this.hero.getRandom().nextDouble() - 0.5),
                    3, 0, 0, 0, 0.1
                );
            }
            
            // 爆发结束时，设置冷却时间
            if (this.glitchBurstTimer == 0) {
                // 冷却 0.2 ~ 0.8 秒 (4 ~ 16 ticks)
                this.glitchCooldownTimer = 4 + this.hero.getRandom().nextInt(12);
                // 恢复位置和状态
                this.hero.setPos(this.startX, this.startY, this.startZ);
                this.hero.setYRot(this.originalYRot);
                this.hero.setXRot(this.originalXRot);
                this.hero.setGlitching(false); // 关闭渲染特效
            }
            
        } else if (this.glitchCooldownTimer > 0) {
            // --- 处于冷却期：正常待机 ---
            this.glitchCooldownTimer--;
            
            // 确保位置稳定
            this.hero.setPos(this.startX, this.startY, this.startZ);
            
            // 冷却结束时，开启下一波爆发
            if (this.glitchCooldownTimer == 0) {
                // 爆发 0.15 ~ 0.4 秒 (3 ~ 8 ticks)
                this.glitchBurstTimer = 3 + this.hero.getRandom().nextInt(5);
            }
        } else {
            // 初始状态或异常状态，启动爆发
            this.glitchBurstTimer = 6;
        }
        
        // 隐身闪烁只在爆发期生效
        if (this.glitchBurstTimer > 0 && this.hero.tickCount % 2 == 0) {
            this.hero.setInvisible(!this.hero.isInvisible());
        } else if (this.glitchBurstTimer == 0) {
            this.hero.setInvisible(false);
        }
    }
}