package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.logic.*;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.event.HeroVisuals;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {

    public static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> CUSTOM_SKIN_NAME = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    // 👇 [新增] 挑战模式专用同步通道 (仅作为数据桥梁，不含逻辑)
    public static final EntityDataAccessor<Boolean> IS_CHALLENGE_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> CHALLENGE_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    private final Set<Integer> claimedRewards = new HashSet<>();
    public float clientFloatingAmount;
    public float clientFloatingAmountO;
    public boolean clientSideSetupDone = false;
    public int patrolTimer = 2400;
    public MoveControl moveControl;
    private int outOfWaterTimer = 0;
    private long lastSummonedTime = 0;
    private boolean isLoadedFromDisk = false;
    // --- 姿势编辑器专用数据 ---
    public boolean isPoseEditing = false;
    // 0=头, 1=身体, 2=右上臂, 3=右小臂, 4=左上臂, 5=左小臂, 6=右大腿, 7=右小腿, 8=左大腿, 9=左小腿
    public float[][] customPoseAngles = new float[10][3];

    // 👇 【新增】：创建一个纯白色的原版 Boss 进度条
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.herobrine_companion.hero"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS
    );
    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;
    private final HeroBrain brain;
    private final GroundPathNavigation groundNavigation;

    public int scytheAnimTick = 0;
    public int debugAnimTick = 0;
    public int thunderTicks = 0;
    public int shockTicks = 0;
    public static final int MAX_THUNDER_TICKS = 60;
    public static final int MAX_SHOCK_TICKS = 60;

    public static final int SKIN_HEROBRINE = 0;
    public static final int SKIN_HERO = 1;
    public static final int SKIN_CUSTOM = 999;

    public HeroEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.moveControl = new HeroMoveControl(this);
        this.brain = new HeroBrain(this);
        this.groundNavigation = new GroundPathNavigation(this, level);
        this.groundNavigation.setCanFloat(false);
        // 👇 【新增】：在实体诞生时，强制让 Boss 血条默认保持隐藏
        this.bossEvent.setVisible(false);
    }

    @Override
    protected void registerGoals() {
        // 👇 [核心修改] 检查硬盘 NBT 数据中的挑战标记
        // 使用 getPersistentData() 是因为它能跨越服务器重启持久化保存
        if (this.getPersistentData().getBoolean("IsChallengeActive")) {
            // 1. 清空所有可能存在的日常 AI
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);

            // 2. 重新装载挑战阶段 AI (无需传入 target，Goal 内部会自动寻找)
            // 这样即使区块重新加载，Boss 也会立刻进入战斗姿态并寻找最近的挑战玩家
            this.goalSelector.addGoal(1, new com.whitecloud233.herobrine_companion.client.fight.goal.HeroPhase1Goal(this));
        } else {
            // 3. 如果没有挑战，则装载原本的日常 AI 系统
            HeroAI.registerGoals(this);
        }
    }
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public PathNavigation getNavigation() {
        return this.isFloating() ? this.navigation : this.groundNavigation;
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
// 👇 【核心修改】：强制清空受伤无敌时间渲染，取消挑战模式下的闪红效果
        if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            this.hurtTime = 0;
        }

        // 委托视觉层处理客户端动画
        HeroVisuals.tickClientAnimations(this);

        if (!this.level().isClientSide) {
            // 重置服务端动画标记
            if (this.scytheAnimTick == 0 && this.entityData.get(INSPECTING_SCYTHE)) this.entityData.set(INSPECTING_SCYTHE, false);
            if (this.debugAnimTick == 0 && this.entityData.get(IS_DEBUGGING)) this.entityData.set(IS_DEBUGGING, false);
            if (this.thunderTicks == 0 && this.entityData.get(IS_CASTING_THUNDER)) this.entityData.set(IS_CASTING_THUNDER, false);
            // 👇 【新增】：呼叫挑战状态管理器，守护底层物理状态
            com.whitecloud233.herobrine_companion.client.fight.HeroChallengeState.tick(this);
            // 👇 【新增】：同步血条逻辑
            if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
                this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
                if (!this.bossEvent.isVisible()) {
                    this.bossEvent.setVisible(true);
                }
            } else {
                if (this.bossEvent.isVisible()) {
                    this.bossEvent.setVisible(false);
                }
            }
            if (this.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                HeroLogic.tick(this);
                if (this.isAlive()) {
                    this.brain.tick();
                    if (this.getMindState() != this.brain.getState()) this.setMindState(this.brain.getState());

                    if (this.level() instanceof ServerLevel serverLevel) {
                        // 委托服务端层处理同步与防伪验证
                        if (!HeroServerTick.handleTick(this, serverLevel)) return;
                    }
                }
            } else {
                HeroDimensionHandler.handleVoidProtection(this);
            }
        }

        HeroOtherProtection.tick(this);

        if (!this.level().isClientSide && this.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (this.isCompanionMode()) this.setCompanionMode(false);
        }

        if (this.level().isClientSide) HeroVisuals.tickClientAmbient(this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.isCompanionMode()) {
            boolean inIntro = level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence");
            if (!inIntro) {
                if (this.isInWater()) {
                    this.outOfWaterTimer = 40;
                    if (!this.isFloating()) this.setFloating(true);
                    if (!this.isNoGravity()) this.setNoGravity(true);
                    if (this.getDeltaMovement().y < 0.1) this.setDeltaMovement(this.getDeltaMovement().add(0, 0.05, 0));
                } else if (this.outOfWaterTimer > 0) {
                    this.outOfWaterTimer--;
                    if (!this.isFloating()) this.setFloating(true);
                    if (!this.isNoGravity()) this.setNoGravity(true);
                }
            }
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = this.getVehicle();
        if (vehicle != null) this.yBodyRot = vehicle.getYRot();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 👇 [新增] 如果处于挑战模式，直接拦截所有右键交互
        if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = HeroLogic.onInteract(this, player, hand);
        return result != InteractionResult.PASS ? result : super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false;
        }

        boolean wasHurt = HeroLogic.onHurt(this, source, amount) || super.hurt(source, amount);

        // 【配合防红】：即便在受伤处理的方法里也强行锁一次 0
        if (wasHurt && this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            this.hurtTime = 0;
        }

        return wasHurt;
    }

    @Override
    public void setHealth(float health) {
        if (!this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            // 平时强制满血无敌
            super.setHealth(this.getMaxHealth());
        } else {
            // 挑战模式下正常扣血，不要在这里调用 endChallenge！
            super.setHealth(health);
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            if (this.getUUID().equals(data.getActiveHeroUUID())) {
                data.setActiveHeroUUID(null);
                data.setLastKnownHeroPos(null);
            }
        }
        super.die(damageSource);
    }
    // 👇 【新增】：追踪玩家逻辑，保证玩家能看到屏幕上方的 Boss 进度条
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        // 👇 【核心修复】：当玩家进入视距或进入存档时，服务端强制把存好的姿势同步给该玩家客户端
        if (this.isPoseEditing) {
            com.whitecloud233.herobrine_companion.network.PacketHandler.sendToPlayer(
                    new com.whitecloud233.herobrine_companion.network.SavePosePacket(this.getId(), this.isPoseEditing, this.customPoseAngles),
                    player
            );
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }
    @Override public boolean canBeAffected(MobEffectInstance instance) { return HeroOtherProtection.canBeAffected(this, instance) && super.canBeAffected(instance); }

    // 1.21: 牵引判定取消了 Player 参数
    @Override public boolean canBeLeashed() { return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed(); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D);
    }

    // 1.21.1: 使用 SynchedEntityData.Builder 注册
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_FLOATING, true);
        builder.define(TRUST_LEVEL, 0);
        builder.define(IS_COMPANION_MODE, false);
        builder.define(SKIN_VARIANT, SKIN_HEROBRINE);
        builder.define(CUSTOM_SKIN_NAME, "");
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(INVITED_POS, Optional.empty());
        builder.define(INVITED_ACTION, 0);
        builder.define(MIND_STATE, 0);
        builder.define(INSPECTING_SCYTHE, false);
        builder.define(IS_GLITCHING, false);
        builder.define(IS_DEBUGGING, false);
        builder.define(IS_CASTING_THUNDER, false);
        builder.define(IS_CHALLENGE_ACTIVE, false);
        builder.define(CHALLENGE_TICKS, 0);
    }

    // 移除 HolderLookup.Provider 参数，恢复为 1 个参数
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TrustLevel", getTrustLevel());
        compound.putInt("PatrolTimer", patrolTimer);
        compound.putBoolean("CompanionMode", isCompanionMode());
        compound.putInt("SkinVariant", getSkinVariant());
        compound.putString("CustomSkinName", getCustomSkinName());
        if (getOwnerUUID() != null) {
            compound.putUUID("OwnerUUID", getOwnerUUID());
            compound.putString("OwnerUUID_String", getOwnerUUID().toString());
        }
        // 👇 [新增] 保存姿势编辑器数据
        compound.putBoolean("IsPoseEditing", this.isPoseEditing);
        if (this.isPoseEditing) {
            ListTag poseList = new ListTag();
            // 遍历 10 个部位
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 3; j++) {
                    poseList.add(net.minecraft.nbt.FloatTag.valueOf(this.customPoseAngles[i][j]));
                }
            }
            compound.put("CustomPoseAngles", poseList);
        }
    }

    // 移除 HolderLookup.Provider 参数，恢复为 1 个参数
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isLoadedFromDisk = true;
        // 👇 【修改】：调用专门的方法处理挑战断点恢复，把你原来写在这里的恢复逻辑删掉，保持代码整洁
        com.whitecloud233.herobrine_companion.client.fight.HeroChallengeState.onRestoreFromDisk(this);

        if (compound.contains("TrustLevel")) setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("CompanionMode")) setCompanionMode(compound.getBoolean("CompanionMode"));
// 👇 [核心修复] 恢复挑战模式的同步状态
        if (this.getPersistentData().getBoolean("IsChallengeActive")) {
            this.entityData.set(IS_CHALLENGE_ACTIVE, true);

            // 恢复同步通道中的进度 tick，确保客户端动画同步
            int savedTicks = this.getPersistentData().getInt("ChallengePhaseTicks");
            this.entityData.set(CHALLENGE_TICKS, savedTicks);
        }
        int localSkin = SKIN_HEROBRINE;
        if (compound.contains("SkinVariant")) localSkin = compound.getInt("SkinVariant");
        else if (compound.contains("UseHerobrineSkin")) localSkin = compound.getBoolean("UseHerobrineSkin") ? SKIN_HEROBRINE : SKIN_HERO;
        this.entityData.set(SKIN_VARIANT, localSkin);

        if (compound.contains("CustomSkinName")) this.entityData.set(CUSTOM_SKIN_NAME, compound.getString("CustomSkinName"));

        UUID ownerUUID = null;
        if (compound.hasUUID("OwnerUUID")) ownerUUID = compound.getUUID("OwnerUUID");
        else if (compound.contains("OwnerUUID_String")) {
            try { ownerUUID = UUID.fromString(compound.getString("OwnerUUID_String")); } catch (Exception ignored) {}
        }
        if (ownerUUID != null) setOwnerUUID(ownerUUID);
        // 👇 [新增] 读取姿势编辑器数据
        if (compound.contains("IsPoseEditing")) {
            this.isPoseEditing = compound.getBoolean("IsPoseEditing");
            // 检查 Tag 类型是否为 List (ID 为 9)
            if (this.isPoseEditing && compound.contains("CustomPoseAngles", 9)) {
                // 读取 FloatTag (ID 为 5) 的列表
                ListTag poseList = compound.getList("CustomPoseAngles", 5);
                // 确保数据完整 (10 * 3 = 30)
                if (poseList.size() == 30) {
                    int index = 0;
                    for (int i = 0; i < 10; i++) {
                        for (int j = 0; j < 3; j++) {
                            this.customPoseAngles[i][j] = poseList.getFloat(index++);
                        }
                    }
                } else {
                    // 数据损坏则重置
                    this.isPoseEditing = false;
                }
            }
        }
    }

    // 委托装备与NBT处理
    public ListTag getArmorItemsTag() { return HeroEquipment.getArmorItemsTag(this); }
    public ListTag getHandItemsTag() { return HeroEquipment.getHandItemsTag(this); }
    public void loadEquipmentFromTag(ListTag armor, ListTag hands) { HeroEquipment.loadEquipmentFromTag(this, armor, hands); }
    public CompoundTag getCuriosBackItemTag() { return HeroEquipment.getCuriosBackItemTag(this); }
    public void setCuriosBackItemFromTag(CompoundTag tag) { HeroEquipment.setCuriosBackItemFromTag(this, tag); }
    public boolean isCuriosBackSlotEmpty() { return HeroEquipment.isCuriosBackSlotEmpty(this); }

    // Getters & Setters
    public boolean isLoadedFromDisk() { return this.isLoadedFromDisk; }
    public boolean isFloating() { return entityData.get(IS_FLOATING); }
    public void setFloating(boolean floating) { entityData.set(IS_FLOATING, floating); }
    public int getTrustLevel() { return entityData.get(TRUST_LEVEL); }
    public void setTrustLevel(int level) {
        entityData.set(TRUST_LEVEL, level);
        if (!this.level().isClientSide) HeroDataHandler.updateGlobalTrust(this);
    }
    public void increaseTrust(int amount) { setTrustLevel(getTrustLevel() + amount); }
    public boolean isCompanionMode() { return entityData.get(IS_COMPANION_MODE); }
    public void setCompanionMode(boolean active) { entityData.set(IS_COMPANION_MODE, active); }
    public int getSkinVariant() { return entityData.get(SKIN_VARIANT); }
    public void setSkinVariant(int variant) {
        entityData.set(SKIN_VARIANT, variant);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) HeroWorldData.get(serverLevel).setSkinVariant(variant);
    }
    public String getCustomSkinName() { return entityData.get(CUSTOM_SKIN_NAME); }
    public void setCustomSkinName(String name) {
        entityData.set(CUSTOM_SKIN_NAME, name);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) HeroWorldData.get(serverLevel).setCustomSkinName(name);
    }
    @Nullable public UUID getOwnerUUID() { return this.entityData.get(OWNER_UUID).orElse(null); }
    public void setOwnerUUID(@Nullable UUID uuid) { this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid)); }
    @Nullable public BlockPos getInvitedPos() { return this.entityData.get(INVITED_POS).orElse(null); }
    public void setInvitedPos(@Nullable BlockPos pos) { this.entityData.set(INVITED_POS, Optional.ofNullable(pos)); }
    public int getInvitedAction() { return this.entityData.get(INVITED_ACTION); }
    public void setInvitedAction(int action) { this.entityData.set(INVITED_ACTION, action); }
    public void setLastSummonedTime(long time) { this.lastSummonedTime = time; }
    public long getLastSummonedTime() { return this.lastSummonedTime; }
    public GoalSelector getGoalSelector() { return this.goalSelector; }
    public void setFallDistance(float distance) { this.fallDistance = distance; }
    public HeroBrain getHeroBrain() { return this.brain; }

    public SimpleNeuralNetwork.MindState getMindState() {
        int index = this.entityData.get(MIND_STATE);
        SimpleNeuralNetwork.MindState[] states = SimpleNeuralNetwork.MindState.values();
        return (index >= 0 && index < states.length) ? states[index] : SimpleNeuralNetwork.MindState.OBSERVER;
    }
    public void setMindState(SimpleNeuralNetwork.MindState state) { this.entityData.set(MIND_STATE, state.ordinal()); }

    public boolean hasClaimedReward(int id) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel && getOwnerUUID() != null) {
            return HeroWorldData.get(serverLevel).isRewardClaimed(getOwnerUUID(), id);
        }
        return claimedRewards.contains(id);
    }

    public void claimReward(int id) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel && getOwnerUUID() != null) {
            HeroWorldData.get(serverLevel).setRewardClaimed(getOwnerUUID(), id, true);
        }
        claimedRewards.add(id);
    }

    @Override public boolean isNoGravity() { return (level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence")) || isFloating(); }
    public float getFloatingAmount(float partialTick) { return net.minecraft.util.Mth.lerp(partialTick, clientFloatingAmountO, clientFloatingAmount); }
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

    // 交易系统 (Merchant)
    @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
    @Nullable @Override public Player getTradingPlayer() { return this.tradingPlayer; }
    @Override public MerchantOffers getOffers() { if (this.offers == null) this.offers = HeroTrades.getOffers(this); return this.offers; }
    @Override public void notifyTrade(MerchantOffer offer) { this.ambientSoundTime = -this.getAmbientSoundInterval(); HeroTrades.onTrade(this, offer); }
    public void resetOffers() { this.offers = null; if (this.tradingPlayer != null) this.tradingPlayer.sendMerchantOffers(getContainerId(), getOffers(), 0, getVillagerXp(), showProgressBar(), canRestock()); }
    @Override public void overrideOffers(@Nullable MerchantOffers offers) { this.offers = offers; }
    @Override public void notifyTradeUpdated(ItemStack stack) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) {}
    @Override public boolean showProgressBar() { return false; }
    @Override public net.minecraft.sounds.SoundEvent getNotifyTradeSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_YES; }
    private int getContainerId() { return (this.tradingPlayer != null && this.tradingPlayer.containerMenu != null) ? this.tradingPlayer.containerMenu.containerId : 0; }

}