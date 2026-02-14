package com.whitecloud233.modid.herobrine_companion.entity;

import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroDimensionHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroLogic;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroOtherProtection;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroTrades;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {

    private static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> USE_HEROBRINE_SKIN = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    // 邀请状态同步数据
    private static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 大脑状态同步
    private static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    // 动画状态同步
    private static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    private final Set<Integer> claimedRewards = new HashSet<>();

    public float clientFloatingAmount;
    public float clientFloatingAmountO;
    public boolean clientSideSetupDone = false;
    public int patrolTimer = 2400;
    private int outOfWaterTimer = 0;

    // [修复关键] 标记实体是否从磁盘加载
    private boolean isLoadedFromDisk = false;

    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;

    private final HeroBrain brain;
    private final GroundPathNavigation groundNavigation;

    public int scytheAnimTick = 0;
    public int debugAnimTick = 0;

    public int thunderTicks = 0;
    public static final int MAX_THUNDER_TICKS = 60;

    public int shockTicks = 0;
    public static final int MAX_SHOCK_TICKS = 60;

    public HeroEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.moveControl = new HeroMoveControl(this);
        this.brain = new HeroBrain(this);

        this.groundNavigation = new GroundPathNavigation(this, level);
        this.groundNavigation.setCanFloat(false);
    }

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
            } else {
                this.shockTicks = MAX_SHOCK_TICKS;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.scytheAnimTick > 0) this.scytheAnimTick--;
        if (this.debugAnimTick > 0) {
            this.debugAnimTick--;
            if (this.level().isClientSide && this.debugAnimTick > 10 && this.debugAnimTick < 90) {
                spawnDebugParticles();
            }
        }
        if (this.shockTicks > 0) this.shockTicks--;

        if (this.thunderTicks > 0) {
            this.thunderTicks--;
            spawnThunderParticles();

            if (!this.level().isClientSide && this.thunderTicks == 1) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level());
                if (bolt != null) {
                    bolt.moveTo(this.getX(), this.getY(), this.getZ());
                    bolt.setVisualOnly(true);
                    this.level().addFreshEntity(bolt);
                }
                this.shockTicks = MAX_SHOCK_TICKS;
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

            if (this.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                HeroLogic.tick(this);
                if (this.isAlive()) {
                    this.brain.tick();
                    SimpleNeuralNetwork.MindState currentState = this.brain.getState();
                    if (this.getMindState() != currentState) {
                        this.setMindState(currentState);
                    }
                    if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel serverLevel) {
                        boolean globalSkin = HeroWorldData.get(serverLevel).shouldUseHerobrineSkin();
                        if (this.shouldUseHerobrineSkin() != globalSkin) {
                            this.entityData.set(USE_HEROBRINE_SKIN, globalSkin);
                        }
                    }
                }
            } else {
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

    // [API 适配] 1.20.1 中 canBeLeashed 需要参数
    @Override
    public boolean canBeLeashed(Player player) {
        return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed(player);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_FLOATING, true);
        this.entityData.define(TRUST_LEVEL, 0);
        this.entityData.define(IS_COMPANION_MODE, false);
        this.entityData.define(USE_HEROBRINE_SKIN, false);
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(INVITED_POS, Optional.empty());
        this.entityData.define(INVITED_ACTION, 0);
        this.entityData.define(MIND_STATE, 0);
        this.entityData.define(INSPECTING_SCYTHE, false);
        this.entityData.define(IS_GLITCHING, false);
        this.entityData.define(IS_DEBUGGING, false);
        this.entityData.define(IS_CASTING_THUNDER, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TrustLevel", getTrustLevel());
        compound.putInt("PatrolTimer", patrolTimer);
        compound.putBoolean("CompanionMode", isCompanionMode());
        compound.putBoolean("UseHerobrineSkin", shouldUseHerobrineSkin());

        // [修复] UUID 备份保存 (防止二进制格式变动)
        if (getOwnerUUID() != null) {
            compound.putUUID("OwnerUUID", getOwnerUUID());
            compound.putString("OwnerUUID_String", getOwnerUUID().toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        // [修复] 标记此实体为“从磁盘加载”，防止 Logic 误判为新生成实体
        this.isLoadedFromDisk = true;

        if (compound.contains("TrustLevel")) setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("CompanionMode")) setCompanionMode(compound.getBoolean("CompanionMode"));

        boolean localSkin = false;
        if (compound.contains("UseHerobrineSkin")) {
            localSkin = compound.getBoolean("UseHerobrineSkin");
            this.entityData.set(USE_HEROBRINE_SKIN, localSkin);
        }

        // [修复] 读取 UUID - 优先二进制，失败则读取字符串
        UUID ownerUUID = null;
        if (compound.hasUUID("OwnerUUID")) {
            ownerUUID = compound.getUUID("OwnerUUID");
        } else if (compound.contains("OwnerUUID_String")) {
            try {
                ownerUUID = UUID.fromString(compound.getString("OwnerUUID_String"));
            } catch (Exception ignored) {}
        }

        if (ownerUUID != null) {
            setOwnerUUID(ownerUUID);
        }

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            if (data.shouldUseHerobrineSkin()) {
                this.entityData.set(USE_HEROBRINE_SKIN, true);
            } else if (localSkin) {
                data.setUseHerobrineSkin(true);
            }
        }
    }

    // [新增] Getter
    public boolean isLoadedFromDisk() {
        return this.isLoadedFromDisk;
    }

    public boolean isFloating() { return entityData.get(IS_FLOATING); }
    public void setFloating(boolean floating) { entityData.set(IS_FLOATING, floating); }
    public int getTrustLevel() { return entityData.get(TRUST_LEVEL); }
    public void setTrustLevel(int level) { entityData.set(TRUST_LEVEL, level); }
    public void increaseTrust(int amount) { setTrustLevel(getTrustLevel() + amount); }
    public boolean isCompanionMode() { return entityData.get(IS_COMPANION_MODE); }
    public void setCompanionMode(boolean active) { entityData.set(IS_COMPANION_MODE, active); }
    public boolean shouldUseHerobrineSkin() { return entityData.get(USE_HEROBRINE_SKIN); }
    public void setUseHerobrineSkin(boolean use) {
        entityData.set(USE_HEROBRINE_SKIN, use);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData.get(serverLevel).setUseHerobrineSkin(use);
        }
    }

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

    public boolean hasClaimedReward(int id) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            UUID owner = getOwnerUUID();
            if (owner != null) {
                return HeroWorldData.get(serverLevel).isRewardClaimed(owner, id);
            }
        }
        return claimedRewards.contains(id);
    }

    public void claimReward(int id) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            UUID owner = getOwnerUUID();
            if (owner != null) {
                HeroWorldData.get(serverLevel).setRewardClaimed(owner, id, true);
            }
        }
        claimedRewards.add(id);
    }

    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    public void setFallDistance(float distance) {
        this.fallDistance = distance;
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

    @Override
    public boolean isNoGravity() {
        if (level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence")) return true;
        return isFloating();
    }

    public float getFloatingAmount(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, clientFloatingAmountO, clientFloatingAmount);
    }

    @Override public boolean shouldShowName() { return false; }
    @Override public boolean isCustomNameVisible() { return false; }
    @Override public boolean isInvulnerable() { return true; }

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
        // [API 适配] 1.20.1 中 SoundEvents.TRIDENT_THUNDER 是 SoundEvent 类型，无需 .value()
        this.playSound(SoundEvents.TRIDENT_THUNDER, 5.0F, 0.8F);
    }

    public boolean isCastingThunder() {
        return this.thunderTicks > 0;
    }
}