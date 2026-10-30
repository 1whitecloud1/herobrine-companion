package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroHostileTargetPolicy;
import com.whitecloud233.herobrine_companion.entity.companion.BattleModeRuntime;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.visual.HeroVisualState;
import com.whitecloud233.herobrine_companion.entity.logic.*;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.ObserverStateDefinition;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroEntityPersistence;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroOwnerProfile;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroPersistenceState;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroTrackingSync;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCombatProtection;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCombatTimeline;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroTradeHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroGunAdapter;
import com.whitecloud233.herobrine_companion.event.HeroVisuals;
import com.whitecloud233.herobrine_companion.fight.HeroAfterimage;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {
    private static final String[] VIRTUAL_BULLET_KEYWORDS = new String[] {
            "bullet", "ammo", "round", "cartridge", "shell", "slug", "musket_ball", "musketball"
    };
    public static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_GROUND_WALKING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> CUSTOM_SKIN_NAME = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    /** 拉磨中被石磨接管：位移、朝向都由石磨决定，客户端据此切换推磨姿态。 */
    public static final EntityDataAccessor<Boolean> MILLSTONE_BOUND = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    // 👇 [新增] 挑战模式专用同步通道 (仅作为数据桥梁，不含逻辑)
    public static final EntityDataAccessor<Boolean> IS_CHALLENGE_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> CHALLENGE_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> BATTLE_MODE_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 无 Epic Fight 战斗模式的自动镰刀：主手为空时由战斗 AI 置位，作为"手持终末之诗"的
     * 虚拟等价（驱动镰刀普攻裁剪与手持渲染），不写入真实装备——玩家随时给的武器优先。
     */
    public static final EntityDataAccessor<Boolean> AUTO_BATTLE_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_COMBO_STEP = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_SERIAL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_POEM_MOVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<ItemStack> VISUAL_MAIN_HAND_ITEM = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> VISUAL_OFF_HAND_ITEM = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Boolean> VISUAL_EATING_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    // 赠礼手势:服务端下发 clipId/起始 tick/时长/token,客户端连续播放(见 entity.visual.HeroGestureLibrary)
    public static final EntityDataAccessor<Integer> OFFER_GESTURE_ID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> OFFER_GESTURE_START_TICK = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> OFFER_GESTURE_DURATION_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> OFFER_GESTURE_TOKEN = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    public static final int BATTLE_ACTION_IDLE = 0;
    public static final int BATTLE_ACTION_APPROACH = 1;
    public static final int BATTLE_ACTION_LIGHT_COMBO_1 = 2;
    public static final int BATTLE_ACTION_LIGHT_COMBO_2 = 3;
    public static final int BATTLE_ACTION_HEAVY_HOLD = 4;
    public static final int BATTLE_ACTION_HEAVY_RELEASE = 5;

    public static final int BATTLE_BUFFER_NONE = 0;
    public static final int BATTLE_BUFFER_APPROACH = 1;
    public static final int BATTLE_BUFFER_LIGHT = 2;
    public static final int BATTLE_BUFFER_DASH = 3;
    public static final int BATTLE_BUFFER_AIR = 4;
    public static final int BATTLE_BUFFER_SKILL = 5;

    private final HeroPersistenceState persistenceState = new HeroPersistenceState();

    public HeroPersistenceState getPersistenceState() { return persistenceState; }
    public void markEquipmentDirty() { persistenceState.markEquipmentDirty(); }
    public void markAppearanceDirty() { persistenceState.markAppearanceDirty(); }
    // --- 心智状态"注视目标"续瞄（运行时字段，不写入 NBT 存档）---
    // 状态系统每 10 tick 才一次性 setLookAt，而原版 LookControl 的 2-tick 冷却结束后
    // 头部会以 10°/tick 摆回 yBodyRot，形成"快甩慢回"的锯齿摆动。
    // 这里记录状态系统最近一次指定的注视目标，由 HeroStateGoals 逐 tick 续瞄（10°/tick 平滑）。
    @Nullable public Vec3 mindLookTarget;
    public int mindLookTargetTick;
    private final HeroOwnerProfile ownerProfile = new HeroOwnerProfile(this);
    @Nullable private HeroVisualState visualState;
    public int patrolTimer = 2400;
    private long lastSummonedTime = 0;
    private boolean isLoadedFromDisk = false;
    private boolean handlingHeroHurt = false;
    private final HeroMovementState movementState;
    private final HeroCombatTimeline combatTimeline = new HeroCombatTimeline();
    private java.util.List<HeroCombatPlanner.ActionProfile> battleComboProfiles = java.util.List.of();
    @Nullable private HeroCombatPlanner.ActionProfile battleDashProfile;
    @Nullable private HeroCombatPlanner.ActionProfile battleAirProfile;
    @Nullable private HeroCombatPlanner.ActionProfile currentBattleActionProfile;
    // --- 姿势编辑器专用数据 ---
    public boolean isPoseEditing = false;
    // 0=头, 1=身体, 2=右上臂, 3=右小臂, 4=左上臂, 5=左小臂, 6=右大腿, 7=右小腿, 8=左大腿, 9=左小腿
    public float[][] customPoseAngles = new float[10][3];

    private final HeroChallengePresentation challengePresentation = new HeroChallengePresentation();
    @Nullable private Player tradingPlayer;
    private final HeroTradeHandler tradeHandler = new HeroTradeHandler();
    private final HeroBrain brain;
    private final com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent agent;
    /** Agent 任务移动控制凭据（阶段 B：任务经 GoalSelector 取得 MOVE/LOOK，不直接抢导航）。 */
    private final com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskControl taskControl =
            new com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskControl();
    /** 任务是否正在借用移动控制权。 */
    private boolean taskMovementActive;

    public int scytheAnimTick = 0;
    public int debugAnimTick = 0;
    public int thunderTicks = 0;
    public int shockTicks = 0;
    public static final int MAX_THUNDER_TICKS = 60;
    public static final int MAX_SHOCK_TICKS = 60;

    public static final int SKIN_HEROBRINE = 0;
    public static final int SKIN_HERO = 1;
    public static final int SKIN_HEROBRINE2 = 2;
    public static final int SKIN_CUSTOM = 999;
    /** 新玩家/新实体的默认皮肤；已有存档保存过的 SkinVariant 值不受影响。 */
    public static final int DEFAULT_SKIN_VARIANT = SKIN_HEROBRINE2;

    public HeroEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.moveControl = new HeroMoveControl(this);
        this.brain = new HeroBrain(this);
        this.agent = new com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent();
        this.movementState = new HeroMovementState(this, this.navigation);
        this.refreshNavigationMode();
    }

    @Override
    protected void registerGoals() {
        // 👇 [核心修改] 检查硬盘 NBT 数据中的挑战标记
        // 使用 getPersistentData() 是因为它能跨越服务器重启持久化保存
        if (ChallengeSession.isHeroInChallenge(this)) {
            // 1. 清空所有可能存在的日常 AI
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);

            // 2. 重新装载挑战阶段 AI (无需传入 target，Goal 内部会自动寻找)
            // 这样即使区块重新加载，Boss 也会立刻进入战斗姿态并寻找最近的挑战玩家
            this.goalSelector.addGoal(1, new com.whitecloud233.herobrine_companion.fight.goal.HeroPhase1Goal(this));
        } else {
            // 3. 如果没有挑战，则装载原本的日常 AI 系统
            HeroAI.registerGoals(this);
        }
    }
    @Override
    public void remove(RemovalReason reason) {
        HeroLifecycleHandler.beforeRemove(this, reason);
        super.remove(reason);
        HeroLifecycleHandler.afterRemove(this);
    }
    @Override
    protected PathNavigation createNavigation(Level level) {
        return HeroMovementState.createFlyingNavigation(this, level);
    }

    @Override
    public PathNavigation getNavigation() {
        return this.navigation;
    }

    // ---- Agent 任务移动控制（交接阶段 B） ----

    /** 任务是否正在借用移动控制权（由 {@code TaskControl} 置位）。 */
    public boolean isTaskMovementActive() {
        return this.taskMovementActive;
    }

    /** 只由任务控制凭据调用：标记 / 清除"任务正在驱动移动"。 */
    public void setTaskMovementActive(boolean active) {
        this.taskMovementActive = active;
    }

    /** 任务强制收回移动控制：停导航、清标记（取消 / 暂停 / 换目标时使用）。 */
    public void releaseTaskMovement() {
        this.taskMovementActive = false;
        this.taskControl.release(this);
    }

    /** 任务移动控制目标（只读快照；任务登记目标，Goal 据此推进导航）。 */
    public com.whitecloud233.herobrine_companion.entity.ai.agent.task.TaskControl taskControl() {
        return this.taskControl;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (INSPECTING_SCYTHE.equals(key) && this.entityData.get(INSPECTING_SCYTHE)) this.scytheAnimTick = 160;
        if (IS_DEBUGGING.equals(key) && this.entityData.get(IS_DEBUGGING)) this.debugAnimTick = 100;
        if (IS_CASTING_THUNDER.equals(key)) {
            if (this.entityData.get(IS_CASTING_THUNDER)) this.thunderTicks = MAX_THUNDER_TICKS;
            else this.shockTicks = MAX_SHOCK_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) return;
        this.challengePresentation.tickAfterimages();
        movementState.syncGroundWalking();
// 👇 【核心修改】：强制清空受伤无敌时间渲染，取消挑战模式下的闪红效果
        if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            this.hurtTime = 0;
        }

        boolean thunderWasActive = this.thunderTicks > 0;
        if (this.level().isClientSide) HeroVisuals.tickClientAnimations(this);
        HeroActionTiming.tick(this);
        if (this.level().isClientSide) HeroVisuals.tickClientActionParticles(this, thunderWasActive);

        if (!this.level().isClientSide) {
            // 重置服务端动画标记
            if (this.scytheAnimTick == 0 && this.entityData.get(INSPECTING_SCYTHE)) this.entityData.set(INSPECTING_SCYTHE, false);
            if (this.debugAnimTick == 0 && this.entityData.get(IS_DEBUGGING)) this.entityData.set(IS_DEBUGGING, false);
            if (this.thunderTicks == 0 && this.entityData.get(IS_CASTING_THUNDER)) this.entityData.set(IS_CASTING_THUNDER, false);
            if (this.getEntityData().get(IS_CHALLENGE_ACTIVE) || this.isInspectingScythe() || this.isDebugAnim() || this.isCastingThunder()) {
                this.resetBattleCombatState();
            }  // 👇 【新增】：呼叫挑战状态管理器，守护底层物理状态
            com.whitecloud233.herobrine_companion.fight.HeroChallengeState.tick(this);

            // 战斗模式限时会话到期收刀（无到期标记时即刻返回，开销可忽略）。
            if (this.isBattleModeActive()) BattleModeRuntime.tick(this);

            this.challengePresentation.tickBossBar(this);

            if (!HeroServerTick.tickBehavior(this)) return;
        }

        HeroOtherProtection.tick(this);

        if (!this.level().isClientSide && this.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (this.isCompanionMode()) this.setCompanionMode(false);
        }

        if (!this.level().isClientSide && this.isCompanionMode() && !this.isBattleModeActive()) {
            movementState.maintainCompanion();
        }

        if (this.level().isClientSide) {
            HeroVisuals.tickClientLocomotion(this);
            HeroVisuals.tickClientAmbient(this);
        }
    }

    @Override
    public void aiStep() {
        // 必须在 super.aiStep()（跑目标选择器与 Epic Fight 的 AnimatedAttackGoal）之前清理，
        // 否则一 tick 内 AI 仍会先拿旧目标行动一次。
        if (!this.level().isClientSide && HeroHostileTargetPolicy.isProtectedFromBattle(this, this.getTarget())) {
            this.setTarget(null);
        }
        super.aiStep();
        movementState.afterAiStep();
    }

    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = this.getVehicle();
        if (vehicle != null) this.yBodyRot = vehicle.getYRot();
    }

    public void alignHeadToBody() {
        float bodyYaw = this.getYRot();
        this.setYHeadRot(bodyYaw);
        this.yHeadRot = bodyYaw;
        this.yHeadRotO = bodyYaw;
    }

    public boolean shouldSuppressFreeLookWhileMoving() {
        if (this.isBattleModeActive() || this.getEntityData().get(IS_CHALLENGE_ACTIVE) || this.getTarget() != null) {
            return false;
        }
        return this.getDeltaMovement().horizontalDistanceSqr() > 0.01D;
    }

    public void lookAtEntityIfStable(Entity target, float yMaxRot, float xMaxRot) {
        if (target == null) {
            return;
        }
        if (this.shouldSuppressFreeLookWhileMoving()) {
            this.alignHeadToBody();
            return;
        }
        this.getLookControl().setLookAt(target, yMaxRot, xMaxRot);
    }

    public void lookAtPositionIfStable(double x, double y, double z, float yMaxRot, float xMaxRot) {
        if (this.shouldSuppressFreeLookWhileMoving()) {
            this.alignHeadToBody();
            return;
        }
        this.getLookControl().setLookAt(x, y, z, yMaxRot, xMaxRot);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 【新增】：核心权限拦截逻辑
        UUID owner = this.getOwnerUUID();
        // 如果这个 Hero 已经绑定了主人，且正在右键的玩家不是主人
        if (owner != null && !owner.equals(player.getUUID())) {
            if (!this.level().isClientSide) {
                // 给企图交互的玩家发送一条仅他可见的红色提示
                player.sendSystemMessage(this.createNotYourHeroMessage());
            }
            // 拒绝交互
            return InteractionResult.FAIL;
        }
        if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = HeroLogic.onInteract(this, player, hand);
        return result != InteractionResult.PASS ? result : super.mobInteract(player, hand);
    }
    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof Player) {
            return false;
        }
        // 战斗模式的目标边界：选目标、以及走 canAttack 的伤害路径都在这里收口。
        // 直接 hurt()/doHurtTarget() 的特效不经过本方法，由 HeroProtectionEvents 的事件层兜底。
        if (HeroHostileTargetPolicy.isProtectedFromBattle(this, target)) {
            return false;
        }
        return !this.shouldPreventFriendlyFire(target) && super.canAttack(target);
    }

    public boolean shouldPreventFriendlyFire(@Nullable Entity target) {
        return HeroCombatProtection.shouldPreventFriendlyFire(this, target);
    }

    /** 战斗模式的目标边界：原版生物与觉醒怪物不受 Herobrine 的战斗伤害。 */
    public boolean shouldPreventProtectedBattleHit(@Nullable Entity target) {
        return HeroCombatProtection.shouldPreventProtectedBattleHit(this, target);
    }

    public boolean hasCompanionFriendlyFireProtection() {
        return HeroCombatProtection.hasCompanionFriendlyFireProtection(this);
    }

    public boolean isChallengeActiveState() {
        return this.getEntityData().get(IS_CHALLENGE_ACTIVE)
                || ChallengeSession.isHeroInChallenge(this);
    }

    /**
     * 是否正在追击战斗敌人（陪伴模式据此让位）。
     *
     * <p>只看「有没有活着的可攻击目标」，不看是否处于战斗模式：战斗模式下清完场，
     * 陪伴跟随要立刻接回来。</p>
     */
    public boolean isChasingBattleEnemy() {
        return this.isBattleModeActive()
                && com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatTargeting
                        .hasEngagedTarget(this);
    }

    @Nullable
    public Player getOwnerPlayer() {
        UUID ownerUUID = this.getOwnerUUID();
        return ownerUUID == null ? null : this.level().getPlayerByUUID(ownerUUID);
    }
    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        ItemStack projectile = super.getProjectile(weaponStack);
        if (!projectile.isEmpty()) {
            return projectile;
        }
        return HeroGunAdapter.getProjectile(this, weaponStack);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false;
        }

        if (this.handlingHeroHurt) {
            return false;
        }

        boolean isChallenge = this.isChallengeActiveState();

        this.handlingHeroHurt = true;
        try {
            HeroLogic.onHurt(this, source, amount);

            if (!isChallenge) {
                this.handleNonChallengeRetaliation(source);
                this.hurtTime = 0;
                this.invulnerableTime = Math.max(this.invulnerableTime, 4);
                if (this.getHealth() < this.getMaxHealth()) {
                    super.setHealth(this.getMaxHealth());
                }
                return false;
            }

            boolean wasHurt = super.hurt(source, amount);

            // 【配合防红】：即便在受伤处理的方法里也强行锁一次 0
            this.hurtTime = 0;

            return wasHurt;
        } finally {
            this.handlingHeroHurt = false;
        }
    }

    private void handleNonChallengeRetaliation(DamageSource source) {
        HeroCombatProtection.handleNonChallengeRetaliation(this, source);
    }

    @Override
    public void setHealth(float health) {
        if (this.handlingHeroHurt && !this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            super.setHealth(this.getMaxHealth());
            return;
        }
        if (!this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            // 平时强制满血无敌
            super.setHealth(this.getMaxHealth());
        } else {
            // 挑战模式下正常扣血，不要在这里调用 endChallenge！
            super.setHealth(health);
        }
    }
    // 👇【新增】：监听所有的装备穿脱和手持物品变化，一旦改变立即触发脏标记
    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        ItemStack previousStack = this.getItemBySlot(slot).copy();
        boolean handSlotChanged = slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND
                && !ItemStack.matches(previousStack, stack);
        if (handSlotChanged) {
            this.stopUsingItem();
        }
        super.setItemSlot(slot, stack);
        if (handSlotChanged) {
            if (!this.level().isClientSide) {
                this.getNavigation().stop();
            }
            this.resetBattleCombatState();
        }
        this.markEquipmentDirty();
    }
    @Override
    public void die(DamageSource damageSource) {
        HeroLifecycleHandler.onDeath(this);
        super.die(damageSource);
    }
    // 👇 【新增】：追踪玩家逻辑，保证玩家能看到屏幕上方的 Boss 进度条
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.challengePresentation.startTracking(player);
        HeroTrackingSync.sendSnapshot(this, player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.challengePresentation.stopTracking(player);
    }
    @Override public boolean canBeAffected(MobEffectInstance instance) { return HeroOtherProtection.canBeAffected(this, instance) && super.canBeAffected(instance); }

    // 1.21: 牵引判定取消了 Player 参数
    @Override public boolean canBeLeashed() { return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed(); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D)
                // 攻速对齐 1.20.1（未设置=默认 4.0）：5.0 会让 EFN auto 动画播放更快、压缩连段窗口
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // 1.21.1: 使用 SynchedEntityData.Builder 注册
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_FLOATING, false);
        builder.define(IS_GROUND_WALKING, false);
        builder.define(TRUST_LEVEL, 0);
        builder.define(IS_COMPANION_MODE, false);
        builder.define(SKIN_VARIANT, DEFAULT_SKIN_VARIANT);
        builder.define(CUSTOM_SKIN_NAME, "");
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(INVITED_POS, Optional.empty());
        builder.define(INVITED_ACTION, 0);
        builder.define(MILLSTONE_BOUND, false);
        builder.define(MIND_STATE, 0);
        builder.define(INSPECTING_SCYTHE, false);
        builder.define(IS_GLITCHING, false);
        builder.define(IS_DEBUGGING, false);
        builder.define(IS_CASTING_THUNDER, false);
        builder.define(IS_CHALLENGE_ACTIVE, false);
        builder.define(CHALLENGE_TICKS, 0);
        builder.define(BATTLE_MODE_ACTIVE, false);
        builder.define(AUTO_BATTLE_SCYTHE, false);
        builder.define(BATTLE_ACTION_STATE, BATTLE_ACTION_IDLE);
        builder.define(BATTLE_ACTION_TICKS, 0);
        builder.define(BATTLE_COMBO_STEP, 0);
        builder.define(BATTLE_ACTION_SERIAL, 0);
        builder.define(BATTLE_POEM_MOVE, -1);
        builder.define(VISUAL_MAIN_HAND_ITEM, ItemStack.EMPTY);
        builder.define(VISUAL_OFF_HAND_ITEM, ItemStack.EMPTY);
        builder.define(VISUAL_EATING_ACTIVE, false);
        builder.define(OFFER_GESTURE_ID, 0);
        builder.define(OFFER_GESTURE_START_TICK, 0);
        builder.define(OFFER_GESTURE_DURATION_TICKS, 0);
        builder.define(OFFER_GESTURE_TOKEN, 0);
    }

    // 移除 HolderLookup.Provider 参数，恢复为 1 个参数
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        HeroEntityPersistence.save(this, compound);
    }

    // 移除 HolderLookup.Provider 参数，恢复为 1 个参数
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isLoadedFromDisk = true;
        HeroEntityPersistence.load(this, compound);
    }

    // 委托装备与NBT处理
    public ListTag getArmorItemsTag() { return HeroEquipment.getArmorItemsTag(this); }
    public ListTag getHandItemsTag() { return HeroEquipment.getHandItemsTag(this); }
    public void loadEquipmentFromTag(ListTag armor, ListTag hands) { HeroEquipment.loadEquipmentFromTag(this, armor, hands); }
    public CompoundTag getCuriosBackItemTag() { return HeroEquipment.getCuriosBackItemTag(this); }
    public void setCuriosBackItemFromTag(CompoundTag tag) { HeroEquipment.setCuriosBackItemFromTag(this, tag); }
    public boolean isCuriosBackSlotEmpty() { return HeroEquipment.isCuriosBackSlotEmpty(this); }
    public CompoundTag getAccessoriesDataTag() { return HeroEquipment.getAccessoriesDataTag(this); }
    public void setAccessoriesDataFromTag(CompoundTag tag) { HeroEquipment.setAccessoriesDataFromTag(this, tag); }

    // Getters & Setters
    public boolean isLoadedFromDisk() { return this.isLoadedFromDisk; }
    public boolean isFloating() { return entityData.get(IS_FLOATING); }
    public boolean isGroundWalking() { return entityData.get(IS_GROUND_WALKING); }
    public HeroVisualState getVisualState() {
        if (this.visualState == null) this.visualState = new HeroVisualState();
        return this.visualState;
    }
    public float getImportedWalkAnimationTime(float partialTick) {
        return this.visualState == null ? 0.0F : this.visualState.getImportedWalkAnimationTime(partialTick);
    }
    public void setFloating(boolean floating) {
        entityData.set(IS_FLOATING, floating);
        this.refreshNavigationMode();
    }

    public int getTrustLevel() { return entityData.get(TRUST_LEVEL); }
    public void setTrustLevel(int level) {
        this.ownerProfile.setTrustLevel(level);
    }
    public void increaseTrust(int amount) { setTrustLevel(getTrustLevel() + amount); }
    public boolean isCompanionMode() { return entityData.get(IS_COMPANION_MODE); }
    public void setCompanionMode(boolean active) {
        movementState.setCompanionMode(active);
    }

    public boolean isBattleModeActive() { return entityData.get(BATTLE_MODE_ACTIVE); }
    public boolean isAutoBattleScythe() { return entityData.get(AUTO_BATTLE_SCYTHE); }
    public void setAutoBattleScythe(boolean active) { entityData.set(AUTO_BATTLE_SCYTHE, active); }
    public void setBattleModeActive(boolean active) {
        combatTimeline.applyBattleMode(this, "direct", active);
    }
    /** 带调用方标记的战斗模式切换（服务/巡检走这里，便于调试日志定位来源）。 */
    public void setBattleModeActiveFrom(@Nullable String callerTag, boolean active) {
        combatTimeline.applyBattleMode(this, callerTag != null ? callerTag : "direct", active);
    }
    public int getBattleActionState() { return entityData.get(BATTLE_ACTION_STATE); }
    public void setBattleActionState(int state) { entityData.set(BATTLE_ACTION_STATE, state); }
    public int getBattleActionTicks() { return entityData.get(BATTLE_ACTION_TICKS); }
    public void setBattleActionTicks(int ticks) { entityData.set(BATTLE_ACTION_TICKS, ticks); }
    public int getBattleComboStep() { return entityData.get(BATTLE_COMBO_STEP); }
    public void setBattleComboStep(int step) { entityData.set(BATTLE_COMBO_STEP, step); }
    public int getBattleActionSerial() { return entityData.get(BATTLE_ACTION_SERIAL); }
    public int getBattlePoemMove() { return entityData.get(BATTLE_POEM_MOVE); }
    public void setBattlePoemMove(int move) { entityData.set(BATTLE_POEM_MOVE, move); }
    public void setBattleActionSerial(int serial) { entityData.set(BATTLE_ACTION_SERIAL, serial); }
    public void setBattleDynamicProfiles(java.util.List<HeroCombatPlanner.ActionProfile> comboProfiles,
                                         @Nullable HeroCombatPlanner.ActionProfile dashProfile,
                                         @Nullable HeroCombatPlanner.ActionProfile airProfile) {
        this.battleComboProfiles = comboProfiles != null ? java.util.List.copyOf(comboProfiles) : java.util.List.of();
        this.battleDashProfile = dashProfile;
        this.battleAirProfile = airProfile;
    }
    @Nullable
    public HeroCombatPlanner.ActionProfile getBattleComboProfile(int comboIndex) {
        if (this.battleComboProfiles == null || this.battleComboProfiles.isEmpty()) {
            return null;
        }
        int normalizedIndex = Math.floorMod(comboIndex, this.battleComboProfiles.size());
        return this.battleComboProfiles.get(normalizedIndex);
    }
    @Nullable
    public HeroCombatPlanner.ActionProfile getBattleDashProfile() { return this.battleDashProfile; }
    @Nullable
    public HeroCombatPlanner.ActionProfile getBattleAirProfile() { return this.battleAirProfile; }
    @Nullable
    public HeroCombatPlanner.ActionProfile getCurrentBattleActionProfile() { return this.currentBattleActionProfile; }
    public void setCurrentBattleActionProfile(@Nullable HeroCombatPlanner.ActionProfile profile) { this.currentBattleActionProfile = profile; }
    public void beginBattleAction(int state) {
        this.beginBattleAction(state, null);
    }
    public void beginBattleAction(int state, @Nullable HeroCombatPlanner.ActionProfile profile) {
        combatTimeline.beginBattleAction(this, state, profile);
    }
    public boolean isBattleTapAction() {
        int state = this.getBattleActionState();
        return state == BATTLE_ACTION_LIGHT_COMBO_1 || state == BATTLE_ACTION_LIGHT_COMBO_2;
    }
    public boolean isBattleHoldAction() {
        return this.getBattleActionState() == BATTLE_ACTION_HEAVY_HOLD || this.isCastingThunder();
    }
    public boolean isBattleReleaseAction() {
        return this.getBattleActionState() == BATTLE_ACTION_HEAVY_RELEASE || this.shockTicks > 0;
    }
    public int getBattleBufferedAction() {
        return this.combatTimeline.getBattleBufferedAction();
    }
    public int getBattleBufferTicks() {
        return this.combatTimeline.getBattleBufferTicks();
    }
    public boolean hasBattleBufferedAction() {
        return this.combatTimeline.hasBattleBufferedAction();
    }
    public void queueBattleBufferedAction(int bufferedAction, int ticks) {
        this.combatTimeline.queueBattleBufferedAction(this, bufferedAction, ticks);
    }
    public void tickBattleBufferedAction() {
        this.combatTimeline.tickBattleBufferedAction();
    }
    public void clearBattleBufferedAction() {
        this.combatTimeline.clearBattleBufferedAction();
    }
    public void resetBattleActionTimeline() {
        this.combatTimeline.resetBattleActionTimeline(this);
    }
    public void resetBattleCombatState() {
        this.combatTimeline.resetBattleCombatState(this);
    }
    private void refreshNavigationMode() {
        if (movementState != null) this.navigation = movementState.selectNavigation(this.navigation);
    }

    public int getSkinVariant() { return entityData.get(SKIN_VARIANT); }
    public void setSkinVariant(int variant) {
        this.ownerProfile.setSkinVariant(variant);
    }
    public String getCustomSkinName() { return entityData.get(CUSTOM_SKIN_NAME); }
    public void setCustomSkinName(String name) {
        this.ownerProfile.setCustomSkinName(name);
    }
    @Nullable public UUID getOwnerUUID() { return this.entityData.get(OWNER_UUID).orElse(null); }

    /** 归属切换时保存旧主人的记忆并清理排队请求。 */
    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerProfile.setOwnerUUID(uuid);
    }
    public Component getOwnerDisplayNameComponent() {
        return this.ownerProfile.getOwnerDisplayNameComponent();
    }
    public Component createNotYourHeroMessage() {
        return this.ownerProfile.createNotYourHeroMessage();
    }
    @Nullable public BlockPos getInvitedPos() { return this.entityData.get(INVITED_POS).orElse(null); }
    public void setInvitedPos(@Nullable BlockPos pos) { this.entityData.set(INVITED_POS, Optional.ofNullable(pos)); }
    public int getInvitedAction() { return this.entityData.get(INVITED_ACTION); }
    public void setInvitedAction(int action) { this.entityData.set(INVITED_ACTION, action); }
    public boolean isMillstoneBound() { return this.entityData.get(MILLSTONE_BOUND); }
    public void setMillstoneBound(boolean bound) {
        if (this.entityData.get(MILLSTONE_BOUND) == bound) return;
        this.entityData.set(MILLSTONE_BOUND, bound);
    }

    /**
     * 是否正在被石磨占着（拉磨中，或刚被邀请过去）。
     * 拉磨期间石磨每 tick 会直接摆位，跟随、惊吓传送这类目标必须让位，否则两边会互相拽。
     */
    public boolean isMillstoneHarnessed() {
        return this.isMillstoneBound()
                || this.getInvitedAction() == com.whitecloud233.herobrine_companion.entity.cooking.CookingRules.INVITED_ACTION_MILLSTONE;
    }
    public ItemStack getVisualMainHandItem() { return this.entityData.get(VISUAL_MAIN_HAND_ITEM); }
    public void setVisualMainHandItem(ItemStack stack) {
        this.entityData.set(VISUAL_MAIN_HAND_ITEM, sanitizeVisualItem(stack));
    }
    public ItemStack getVisualOffHandItem() { return this.entityData.get(VISUAL_OFF_HAND_ITEM); }
    public void setVisualOffHandItem(ItemStack stack) {
        this.entityData.set(VISUAL_OFF_HAND_ITEM, sanitizeVisualItem(stack));
    }
    public boolean isVisualEatingActive() { return this.entityData.get(VISUAL_EATING_ACTIVE); }
    public void setVisualEatingActive(boolean active) { this.entityData.set(VISUAL_EATING_ACTIVE, active); }
    public void clearVisualHeldItems() {
        this.setVisualMainHandItem(ItemStack.EMPTY);
        this.setVisualOffHandItem(ItemStack.EMPTY);
        this.setVisualEatingActive(false);
    }
    public void setLastSummonedTime(long time) { this.lastSummonedTime = time; }
    public long getLastSummonedTime() { return this.lastSummonedTime; }

    /**
     * 传送后抑制跟随的截止 tick。
     * <p>
     * 对齐基岩版同步修复中的 {@code _tpFollowHoldUntilTick} / {@code TP_FOLLOW_HOLD_TICKS}：
     * 创世神的庇护传送/召回、观察者（Observer）背后现身等传送后，5 秒内禁止
     * 陪伴跟随 AI 重新下发移动目标，防止 Hero 被立刻"平移"拉回传送前的位置。
     * 该字段为运行期状态，不写入 NBT 存档。
     */
    public static final int TP_FOLLOW_HOLD_TICKS = 100;

    public void markTeleportFollowHold() {
        movementState.markTeleportFollowHold();
    }

    public boolean isTeleportFollowHoldActive() {
        return movementState.isTeleportFollowHoldActive();
    }
    public GoalSelector getGoalSelector() { return this.goalSelector; }

    /**
     * 替换实际参与 {@code Mob.aiStep} 的 MoveControl（继承自 {@code Mob.moveControl}）。
     * <p><b>关键</b>：切勿在子类里重声明同名字段去"覆盖"它——Java 字段是隐藏而非重写，
     * {@code Mob.aiStep()} 直接访问 {@code this.moveControl}（Mob 的字段），重声明的字段永远不会被 tick。
     * 需要替换移动控制时一律走本方法。</p>
     */
    public void setMoveControl(MoveControl control) { this.moveControl = control; }
    public void setFallDistance(float distance) { this.fallDistance = distance; }
    public HeroBrain getHeroBrain() { return this.brain; }
    public com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent getHeroAgent() { return this.agent; }

    public void addChallengeAfterimage(HeroAfterimage afterimage) {
        this.challengePresentation.addAfterimage(this, afterimage);
    }

    public java.util.List<HeroAfterimage> getChallengeAfterimages() {
        return this.challengePresentation.afterimages();
    }

    public void clearChallengeAfterimages() {
        this.challengePresentation.clearAfterimages();
    }

    public SimpleNeuralNetwork.MindState getMindState() {
        int index = this.entityData.get(MIND_STATE);
        SimpleNeuralNetwork.MindState[] states = SimpleNeuralNetwork.MindState.values();
        return (index >= 0 && index < states.length) ? states[index] : SimpleNeuralNetwork.MindState.OBSERVER;
    }
    public void setMindState(SimpleNeuralNetwork.MindState state) {
        SimpleNeuralNetwork.MindState previousState = this.getMindState();
        if (previousState == SimpleNeuralNetwork.MindState.OBSERVER && state != SimpleNeuralNetwork.MindState.OBSERVER) {
            ObserverStateDefinition.clearObserverInvisibility(this);
        }
        this.entityData.set(MIND_STATE, state.ordinal());
    }
    private static ItemStack sanitizeVisualItem(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public boolean hasClaimedReward(int id) {
        return this.ownerProfile.hasClaimedReward(id);
    }

    public void claimReward(int id) {
        this.ownerProfile.claimReward(id);
    }

    public void applyClaimedRewardsSnapshot(java.util.Set<Integer> rewards) {
        this.ownerProfile.applyClaimedRewardsSnapshot(rewards);
    }

    public long getClaimedRewardsRevision() {
        return this.ownerProfile.getClaimedRewardsRevision();
    }

    public java.util.Set<Integer> getClaimedRewards() {
        return this.ownerProfile.getClaimedRewards();
    }

    @Override public boolean isNoGravity() { return (level().dimension() == ModStructures.END_RING_DIMENSION_KEY && !ChallengeSession.isHeroInChallenge(this)) || isFloating(); }
    public float getFloatingAmount(float partialTick) {
        return this.visualState == null ? 0.0F : this.visualState.getFloatingAmount(partialTick);
    }
    public float getThunderProgress(float partialTick) {
        if (this.thunderTicks <= 0) return 0.0F;
        return Mth.clamp(((MAX_THUNDER_TICKS - this.thunderTicks) + partialTick) / (float)MAX_THUNDER_TICKS, 0.0F, 1.0F);
    }

    @Override public boolean shouldShowName() { return false; }
    @Override public boolean isCustomNameVisible() { return false; }
    @Override
    public boolean isInvulnerable() {
        if (this.entityData.get(IS_CHALLENGE_ACTIVE)) { // [修改] 使用同步通道判断
            return false;
        }
        return true;
    }
    @Override public boolean isClientSide() { return this.level().isClientSide; }
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }

    // 动画控制
    public void playScytheInspectAnim() { this.scytheAnimTick = 160; this.entityData.set(INSPECTING_SCYTHE, true); this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.5F); }
    public boolean isInspectingScythe() { return this.scytheAnimTick > 0; }
    public void setGlitching(boolean glitching) { this.entityData.set(IS_GLITCHING, glitching); }
    public boolean isGlitching() { return this.entityData.get(IS_GLITCHING); }
    public void playDebugAnim() { this.debugAnimTick = 100; this.entityData.set(IS_DEBUGGING, true); this.playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 2.0F); }
    public boolean isDebugAnim() { return this.debugAnimTick > 0; }
    public void castThunder() {
        this.thunderTicks = MAX_THUNDER_TICKS;
        this.entityData.set(IS_CASTING_THUNDER, true);
        this.playSound(SoundEvents.TRIDENT_THUNDER.value(), 5.0F, 0.8F); // 加上 .value()
    }
    public boolean isCastingThunder() { return this.thunderTicks > 0; }

    // --- 赠礼手势与本地台词(Bedrock hero_offer 控制器移植)---

    /**
     * 服务端权威触发:设置手势 clip 与播放窗口,经 SynchedEntityData 同步给客户端。
     *
     * <p>OFFER_GESTURE_START_TICK 记录的是服务端 tickCount,仅供服务端收尾使用;
     * 客户端播放进度基于 HeroVisualState 的本地起点,因为两端 tickCount
     * 起点不同(客户端实体在客户端世界创建时从 0 起算)。
     */
    public void playOfferGesture(int clipId) {
        HeroGestureController.start(this, clipId);
    }

    /** 由 clip 时长换算播放窗口(tick),尾部留 8 tick 缓冲。 */
    public static int offerGestureDurationTicks(int clipId) {
        return HeroGestureController.durationTicks(clipId);
    }

    /** 客户端播放过期清理(本地数据,不回传服务端)。 */
    public void clearOfferGestureClient() {
        HeroGestureController.clear(this);
    }

    /** 服务端收尾清理:手势窗口结束(服务端时间基)后清 ID 与展示手持物。 */
    public void clearOfferGestureServer() {
        HeroGestureController.clear(this);
    }

    /**
     * 本地赠礼台词:直接上气泡。
     *
     * <p>用于同一次交互的引导/附加句(冷却提示、模糊线索、生日第二句等),
     * 不经 LLM 链路,因此不占 API 配额、不受任何频率闸影响。
     */
    public void showGiftLine(String text) {
        HeroGestureController.showGiftLine(this, text);
    }

    public int getOfferGestureId() { return this.entityData.get(OFFER_GESTURE_ID); }
    public int getOfferGestureStartTick() { return this.entityData.get(OFFER_GESTURE_START_TICK); }
    public int getOfferGestureDurationTicks() { return this.entityData.get(OFFER_GESTURE_DURATION_TICKS); }
    public int getOfferGestureToken() { return this.entityData.get(OFFER_GESTURE_TOKEN); }
    public boolean isOfferGestureActive() { return this.entityData.get(OFFER_GESTURE_ID) != 0; }

    /** 手势混合权重(渲染热路径,带 partial tick 插值)。 */
    public float getOfferBlend(float partialTick) {
        return this.visualState == null ? 0.0F : this.visualState.getOfferBlend(partialTick);
    }

    /** 待机变体混合权重(渲染热路径,带 partial tick 插值)。 */
    public float getVariantBlend(float partialTick) {
        return this.visualState == null ? 0.0F : this.visualState.getVariantBlend(partialTick);
    }

    /**
     * 赠礼手势锁:手势窗口内英雄站定不动、不做其它表现动作。
     *
     * <p>等价 Bedrock {@code variable.hero_offer_allowed}
     * (需要 {@code query.is_on_ground && !query.has_target && !query.is_using_item})的门控语义。
     */
    public boolean isGiftGestureHolding() {
        return isOfferGestureActive();
    }

    /**
     * 送东西时把英雄按住:清掉 AI 本 tick 给出的走位意图、残留速度,并让抢姿势的表现类小动作让位。
     *
     * <p>服务端在每个 tick 的手势窗口内调用(见 {@link #tick()}),因此即使 Goal 每 tick 重新
     * 寻路,也不会把它拖走;凝视镰刀/调试姿态是纯表现动作,直接中断,技能类(雷击)只压姿态不改逻辑。
     */
    public void holdForGiftGesture() {
        HeroGestureController.hold(this);
    }

    /** 待机变体门控:双手空闲(Bedrock variable.hero_idle_hands_free 对应)。 */
    public boolean isHandsFreeForIdleVisual() {
        return getMainHandItem().isEmpty() && getOffhandItem().isEmpty();
    }

    /** 待机变体门控:当前是否处于会与变体冲突的高优先级表现状态。 */
    public boolean isBusyForIdleVisual() {
        return HeroGestureController.isBusy(this);
    }

    // 交易系统 (Merchant)
    @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
    @Nullable @Override public Player getTradingPlayer() { return this.tradingPlayer; }
    @Override public MerchantOffers getOffers() { return this.tradeHandler.getOffers(this); }
    @Override public void notifyTrade(MerchantOffer offer) { this.tradeHandler.notifyTrade(this, offer); }
    public void resetOffers() { this.tradeHandler.resetOffers(this); }
    @Override public void overrideOffers(@Nullable MerchantOffers offers) { this.tradeHandler.overrideOffers(offers); }
    @Override public void notifyTradeUpdated(ItemStack stack) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) {}
    @Override public boolean showProgressBar() { return false; }
    @Override public net.minecraft.sounds.SoundEvent getNotifyTradeSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_YES; }
}
