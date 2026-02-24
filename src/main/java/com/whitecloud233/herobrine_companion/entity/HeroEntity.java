package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroLogic;
import com.whitecloud233.herobrine_companion.entity.logic.HeroOtherProtection;
import com.whitecloud233.herobrine_companion.entity.logic.HeroTrades;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDimensionHandler;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {

    // --- 数据参数定义 ---
    private static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    // 主人 UUID 同步数据
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    // 邀请状态同步数据
    private static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT); // 0=None, 1=Inspect, 2=Rest, 3=Guard

    // 皮肤变体: 0=Auto, 1=Hero, 2=Herobrine
    private static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 奖励领取状态位掩码 (支持 32 个奖励)
    private static final EntityDataAccessor<Integer> CLAIMED_REWARDS_FLAGS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 大脑状态同步 (用于客户端渲染和交互)
    private static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 抚摸镰刀动画状态同步
    private static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    // 故障动画状态同步
    private static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    // 调试动画状态同步
    private static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    // 雷电召唤动画状态同步
    private static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int SKIN_AUTO = 0;
    public static final int SKIN_HERO = 1;
    public static final int SKIN_HEROBRINE = 2;

    // --- 状态变量 ---
    public float clientFloatingAmount;
    public float clientFloatingAmountO;
    public boolean clientSideSetupDone = false;
    public int patrolTimer = 2400;
    private int outOfWaterTimer = 0;

    // [修复关键] 标记实体是否从磁盘加载，防止 Update 后逻辑误判
    private boolean loadedFromDisk = false;

    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;

    // 深度学习大脑
    private final HeroBrain brain;

    // 寻路器切换
    private final GroundPathNavigation groundNavigation;

    // 抚摸镰刀动画 (客户端倒计时)
    public int scytheAnimTick = 0;

    // 调试动画计时器
    public int debugAnimTick = 0;

    // 雷电召唤动画计时器
    public int thunderTicks = 0;
    public static final int MAX_THUNDER_TICKS = 60; // 3秒总时长

    // 被雷劈后的带电状态计时器 (用于渲染电弧)
    public int shockTicks = 0;
    public static final int MAX_SHOCK_TICKS = 60; // 电弧持续3秒

    public HeroEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.moveControl = new HeroMoveControl(this);
        this.brain = new HeroBrain(this);

        // 初始化两种寻路器
        this.groundNavigation = new GroundPathNavigation(this, level);
        this.groundNavigation.setCanFloat(false);
    }

    // --- 1. AI 与 逻辑转发 ---
    @Override
    protected void registerGoals() {
        HeroAI.registerGoals(this);
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
        if (this.isFloating()) {
            return this.navigation;
        } else {
            return this.groundNavigation;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_FLOATING, true);
        builder.define(TRUST_LEVEL, 0);
        builder.define(IS_COMPANION_MODE, false);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(INVITED_POS, Optional.empty());
        builder.define(INVITED_ACTION, 0);
        builder.define(SKIN_VARIANT, SKIN_AUTO);
        builder.define(CLAIMED_REWARDS_FLAGS, 0);
        builder.define(MIND_STATE, 0);
        builder.define(INSPECTING_SCYTHE, false);
        builder.define(IS_GLITCHING, false);
        builder.define(IS_DEBUGGING, false);
        builder.define(IS_CASTING_THUNDER, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (INSPECTING_SCYTHE.equals(key)) {
            if (this.entityData.get(INSPECTING_SCYTHE)) {
                this.scytheAnimTick = 160;
            }
        }
        if (IS_DEBUGGING.equals(key)) {
            if (this.entityData.get(IS_DEBUGGING)) {
                this.debugAnimTick = 100;
            }
        }
        if (IS_CASTING_THUNDER.equals(key)) {
            if (this.entityData.get(IS_CASTING_THUNDER)) {
                this.thunderTicks = MAX_THUNDER_TICKS;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 动画计时器逻辑
        if (this.scytheAnimTick > 0) {
            this.scytheAnimTick--;
        }

        if (this.debugAnimTick > 0) {
            this.debugAnimTick--;
            if (this.level().isClientSide && this.debugAnimTick > 10 && this.debugAnimTick < 90) {
                spawnDebugParticles();
            }
        }

        if (this.shockTicks > 0) {
            this.shockTicks--;
        }

        if (this.thunderTicks > 0) {
            this.thunderTicks--;

            spawnThunderParticles();

            if (this.thunderTicks == 1) {
                this.shockTicks = MAX_SHOCK_TICKS;

                if (!this.level().isClientSide) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level());
                    bolt.moveTo(this.getX(), this.getY(), this.getZ());
                    bolt.setVisualOnly(true);
                    this.level().addFreshEntity(bolt);
                }
            }
        }

        if (!this.level().isClientSide) {
            if (this.scytheAnimTick == 0 && this.entityData.get(INSPECTING_SCYTHE)) {
                this.entityData.set(INSPECTING_SCYTHE, false);
            }
            if (this.debugAnimTick == 0 && this.entityData.get(IS_DEBUGGING)) {
                this.entityData.set(IS_DEBUGGING, false);
            }
            if (this.thunderTicks == 0 && this.entityData.get(IS_CASTING_THUNDER)) {
                this.entityData.set(IS_CASTING_THUNDER, false);
            }

            if (this.tickCount == 0 || this.tickCount % 20 == 0) {
                HeroWorldData data = HeroWorldData.get((ServerLevel) this.level());
                if (this.getSkinVariant() != data.getGlobalSkinVariant()) {
                    this.setSkinVariant(data.getGlobalSkinVariant());
                }
            }
        }

        if (this.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
            HeroLogic.tick(this);
            if (!this.level().isClientSide && this.isAlive()) {
                this.brain.tick();
                SimpleNeuralNetwork.MindState currentState = this.brain.getState();
                if (this.getMindState() != currentState) {
                    this.setMindState(currentState);
                }
            }
        } else {
            if (!this.level().isClientSide) {
                HeroDimensionHandler.handleVoidProtection(this);
            }
        }
        HeroOtherProtection.tick(this);
        if (!this.level().isClientSide && this.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (this.isCompanionMode()) {
                this.setCompanionMode(false);
            }
        }

        if (this.level().isClientSide) {
            if (this.getMindState() == SimpleNeuralNetwork.MindState.JUDGE) {
                if (this.random.nextInt(3) == 0) {
                    double x = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
                    double y = this.getY() + this.random.nextDouble() * 2.0;
                    double z = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
                    this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    private void spawnDebugParticles() {
        if (this.random.nextInt(5) != 0) return;

        double yawRad = Math.toRadians(this.yBodyRot);
        double offsetX = -Math.sin(yawRad) * 0.8 + Math.cos(yawRad) * 0.3;
        double offsetZ = Math.cos(yawRad) * 0.8 + Math.sin(yawRad) * 0.3;
        double offsetY = 1.4;

        double x = this.getX() + offsetX;
        double y = this.getY() + offsetY;
        double z = this.getZ() + offsetZ;

        double pX = x + (this.random.nextDouble() - 0.5) * 0.5;
        double pY = y + (this.random.nextDouble() - 0.5) * 0.4;
        double pZ = z + (this.random.nextDouble() - 0.5) * 0.5;

        this.level().addParticle(ParticleTypes.ENCHANT, pX, pY, pZ, 0, 0.01, 0);
    }

    private void spawnThunderParticles() {
        if (!this.level().isClientSide) return;

        double yaw = Math.toRadians(this.yBodyRot + 90);
        double handX = this.getX() + Math.cos(yaw) * 0.6;
        double handY = this.getY() + 2.8;
        double handZ = this.getZ() + Math.sin(yaw) * 0.6;

        RandomSource rand = this.random;

        for(int i=0; i<3; i++) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    handX + (rand.nextDouble()-0.5)*0.5,
                    handY + (rand.nextDouble()-0.5)*0.5,
                    handZ + (rand.nextDouble()-0.5)*0.5,
                    0, 0, 0);
        }

        float progress = 1.0F - (float)this.thunderTicks / MAX_THUNDER_TICKS;
        if (progress > 0.3F) {
            for(int i=0; i<2; i++) {
                double height = rand.nextDouble() * 10.0 * progress;
                double angle = (this.tickCount * 0.5 + height) * 0.5;
                double radius = 1.0 - (height * 0.05);
                if (radius < 0) radius = 0;

                double pX = this.getX() + Math.cos(angle) * radius;
                double pZ = this.getZ() + Math.sin(angle) * radius;

                this.level().addParticle(ParticleTypes.FIREWORK,
                        pX, this.getY() + height, pZ,
                        0, 0.1, 0);
            }
        }
    }

    public float getThunderProgress(float partialTick) {
        if (this.thunderTicks <= 0) return 0.0F;
        float current = (MAX_THUNDER_TICKS - this.thunderTicks) + partialTick;
        float progress = current / (float)MAX_THUNDER_TICKS;
        return Mth.clamp(progress, 0.0F, 1.0F);
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
                    if (this.getDeltaMovement().y < 0.1) {
                        this.setDeltaMovement(this.getDeltaMovement().add(0, 0.05, 0));
                    }
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
        if (vehicle != null) {
            this.yBodyRot = vehicle.getYRot();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = HeroLogic.onInteract(this, player, hand);
        if (result != InteractionResult.PASS) return result;
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return HeroLogic.onHurt(this, source, amount) || super.hurt(source, amount);
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(this.getMaxHealth());
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.isRemoved()) {
            return;
        }
        super.die(damageSource);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance instance) {
        return HeroOtherProtection.canBeAffected(this, instance) && super.canBeAffected(instance);
    }

    @Override
    public boolean canBeLeashed() {
        return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Integer.MAX_VALUE)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D);
    }

    // --- 2. 属性与数据同步 (核心修复区域) ---
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        compound.putInt("TrustLevel", getTrustLevel());
        compound.putInt("PatrolTimer", patrolTimer);
        compound.putBoolean("CompanionMode", isCompanionMode());

        // [修复] 保存 UUID - 增加多重备份 (String格式)
        // 这样即使 Mod 更新改变了 UUID 二进制序列化方式，也能通过 String 找回
        if (getOwnerUUID() != null) {
            compound.putUUID("OwnerUUID", getOwnerUUID());
            compound.putString("OwnerUUID_String", getOwnerUUID().toString());
        }

        compound.putInt("ClaimedRewardsFlags", this.entityData.get(CLAIMED_REWARDS_FLAGS));
        compound.putInt("SkinVariant", getSkinVariant());
        this.brain.save(compound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        // [修复] 标记此实体为“从磁盘加载”，HeroLogic 会据此保护其不被错误重置
        this.loadedFromDisk = true;

        if (compound.contains("TrustLevel")) setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("CompanionMode")) setCompanionMode(compound.getBoolean("CompanionMode"));

        // [修复] 读取 UUID - 多重恢复策略
        UUID ownerUUID = null;
        if (compound.hasUUID("OwnerUUID")) {
            ownerUUID = compound.getUUID("OwnerUUID");
        }
        // 如果二进制读取失败，尝试从字符串读取
        else if (compound.contains("OwnerUUID_String")) {
            try {
                ownerUUID = UUID.fromString(compound.getString("OwnerUUID_String"));
            } catch (Exception ignored) {}
        }

        if (ownerUUID != null) {
            setOwnerUUID(ownerUUID);
        }

        if (compound.contains("ClaimedRewardsFlags")) {
            this.entityData.set(CLAIMED_REWARDS_FLAGS, compound.getInt("ClaimedRewardsFlags"));
        } else if (compound.contains("ClaimedRewards")) {
            int[] rewards = compound.getIntArray("ClaimedRewards");
            int flags = 0;
            for (int r : rewards) {
                if (r >= 0 && r < 32) {
                    flags |= (1 << r);
                }
            }
            this.entityData.set(CLAIMED_REWARDS_FLAGS, flags);
        }
        this.brain.load(compound);

        if (!this.level().isClientSide) {
            HeroWorldData data = HeroWorldData.get((ServerLevel) this.level());
            setSkinVariant(data.getGlobalSkinVariant());
        } else {
            if (compound.contains("SkinVariant")) {
                setSkinVariant(compound.getInt("SkinVariant"));
            }
        }
    }

    // [新增] Getter 用于 Logic 判断
    public boolean isLoadedFromDisk() {
        return this.loadedFromDisk;
    }

    // --- 3. Getters & Setters ---
    public boolean isFloating() { return entityData.get(IS_FLOATING); }
    public void setFloating(boolean floating) { entityData.set(IS_FLOATING, floating); }
    public int getTrustLevel() { return entityData.get(TRUST_LEVEL); }
    
    public void setTrustLevel(int level) { 
        entityData.set(TRUST_LEVEL, level);
        // [修复] 信任度变化时立即同步到全局数据，防止跨维度丢失
        if (!this.level().isClientSide) {
            HeroDataHandler.updateGlobalTrust(this);
        }
    }
    
    public void increaseTrust(int amount) { setTrustLevel(getTrustLevel() + amount); }
    public boolean isCompanionMode() { return entityData.get(IS_COMPANION_MODE); }
    public void setCompanionMode(boolean active) { entityData.set(IS_COMPANION_MODE, active); }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public BlockPos getInvitedPos() {
        return this.entityData.get(INVITED_POS).orElse(null);
    }

    public void setInvitedPos(@Nullable BlockPos pos) {
        this.entityData.set(INVITED_POS, Optional.ofNullable(pos));
    }

    public int getInvitedAction() {
        return this.entityData.get(INVITED_ACTION);
    }

    public void setInvitedAction(int action) {
        this.entityData.set(INVITED_ACTION, action);
    }
    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    public void setFallDistance(float distance) {
        this.fallDistance = distance;
    }

    public boolean hasClaimedReward(int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return false;
        return (this.entityData.get(CLAIMED_REWARDS_FLAGS) & (1 << rewardId)) != 0;
    }

    public void claimReward(int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return;
        int current = this.entityData.get(CLAIMED_REWARDS_FLAGS);
        this.entityData.set(CLAIMED_REWARDS_FLAGS, current | (1 << rewardId));
    }

    public void setClaimedRewards(int[] rewards) {
        int flags = 0;
        for (int r : rewards) {
            if (r >= 0 && r < 32) {
                flags |= (1 << r);
            }
        }
        this.entityData.set(CLAIMED_REWARDS_FLAGS, flags);
    }

    public int[] getClaimedRewards() {
        int flags = this.entityData.get(CLAIMED_REWARDS_FLAGS);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            if ((flags & (1 << i)) != 0) {
                list.add(i);
            }
        }
        return list.stream().mapToInt(i -> i).toArray();
    }

    public int getSkinVariant() {
        return this.entityData.get(SKIN_VARIANT);
    }

    public void setSkinVariant(int variant) {
        this.entityData.set(SKIN_VARIANT, variant);
        if (!this.level().isClientSide) {
            HeroWorldData data = HeroWorldData.get((ServerLevel) this.level());
            data.setGlobalSkinVariant(variant);
        }
    }

    public SimpleNeuralNetwork.MindState getMindState() {
        int index = this.entityData.get(MIND_STATE);
        SimpleNeuralNetwork.MindState[] states = SimpleNeuralNetwork.MindState.values();
        if (index >= 0 && index < states.length) {
            return states[index];
        }
        return SimpleNeuralNetwork.MindState.OBSERVER;
    }

    public void setMindState(SimpleNeuralNetwork.MindState state) {
        this.entityData.set(MIND_STATE, state.ordinal());
    }

    public HeroBrain getHeroBrain() {
        return this.brain;
    }

    // --- 4. 视觉与渲染覆盖 ---
    @Override
    public boolean isNoGravity() {
        if (level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence")) return true;
        return isFloating();
    }

    public float getFloatingAmount(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, clientFloatingAmountO, clientFloatingAmount);
    }

    @Override public boolean shouldShowName() {
        return this.getTags().contains("brain_debug");
    }
    @Override public boolean isCustomNameVisible() {
        return this.getTags().contains("brain_debug");
    }
    @Override public boolean isInvulnerable() { return level().isClientSide || super.isInvulnerable(); }

    // --- 5. 交易逻辑 (Merchant Impl) ---
    @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
    @Nullable @Override public Player getTradingPlayer() { return this.tradingPlayer; }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) this.offers = HeroTrades.getOffers(this);
        return this.offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        HeroTrades.onTrade(this, offer);
    }

    public void resetOffers() {
        this.offers = null;
        if (this.tradingPlayer != null) {
            this.tradingPlayer.sendMerchantOffers(getContainerId(), getOffers(), 0, getVillagerXp(), showProgressBar(), canRestock());
        }
    }

    @Override public void overrideOffers(@Nullable MerchantOffers offers) { this.offers = offers; }
    @Override public void notifyTradeUpdated(ItemStack stack) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) {}
    @Override public boolean showProgressBar() { return false; }
    @Override public net.minecraft.sounds.SoundEvent getNotifyTradeSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_YES; }
    @Override public boolean isClientSide() { return this.level().isClientSide; }
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }

    private int getContainerId() {
        if (this.tradingPlayer != null && this.tradingPlayer.containerMenu != null) return this.tradingPlayer.containerMenu.containerId;
        return 0;
    }

    public void playScytheInspectAnim() {
        this.scytheAnimTick = 160;
        this.entityData.set(INSPECTING_SCYTHE, true);
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.5F);
    }

    public boolean isInspectingScythe() {
        return this.scytheAnimTick > 0;
    }

    public void setGlitching(boolean glitching) {
        this.entityData.set(IS_GLITCHING, glitching);
    }

    public boolean isGlitching() {
        return this.entityData.get(IS_GLITCHING);
    }

    public void playDebugAnim() {
        this.debugAnimTick = 100;
        this.entityData.set(IS_DEBUGGING, true);
        this.playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 2.0F);
    }

    public boolean isDebugAnim() {
        return this.debugAnimTick > 0;
    }

    public void castThunder() {
        this.thunderTicks = MAX_THUNDER_TICKS;
        this.entityData.set(IS_CASTING_THUNDER, true);
        this.playSound(SoundEvents.TRIDENT_THUNDER.value(), 5.0F, 0.8F);
    }

    public boolean isCastingThunder() {
        return this.thunderTicks > 0;
    }
}
