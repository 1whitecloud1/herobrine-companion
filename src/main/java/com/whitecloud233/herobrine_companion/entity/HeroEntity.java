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
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.neoforged.fml.ModList; // [修正] 确保使用 NeoForge 的 ModList

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {

    // --- 数据参数定义 ---
    private static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 皮肤变体
    private static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CUSTOM_SKIN_NAME = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> CLAIMED_REWARDS_FLAGS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

    // 动画状态同步
    private static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int SKIN_AUTO = 0;
    public static final int SKIN_HERO = 1;
    public static final int SKIN_HEROBRINE = 2;
    public static final int SKIN_CUSTOM = 3;

    public float clientFloatingAmount;
    public float clientFloatingAmountO;
    public boolean clientSideSetupDone = false;
    public int patrolTimer = 2400;
    private int outOfWaterTimer = 0;

    private long lastSummonedTime = 0;
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_FLOATING, true);
        builder.define(TRUST_LEVEL, 0);
        builder.define(IS_COMPANION_MODE, false);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(INVITED_POS, Optional.empty());
        builder.define(INVITED_ACTION, 0);
        builder.define(SKIN_VARIANT, SKIN_HEROBRINE);
        builder.define(CUSTOM_SKIN_NAME, "");
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
        if (INSPECTING_SCYTHE.equals(key) && this.entityData.get(INSPECTING_SCYTHE)) {
            this.scytheAnimTick = 160;
        }
        if (IS_DEBUGGING.equals(key) && this.entityData.get(IS_DEBUGGING)) {
            this.debugAnimTick = 100;
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

            if (this.thunderTicks == 1) {
                this.shockTicks = MAX_SHOCK_TICKS;
                if (!this.level().isClientSide) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level());
                    if (bolt != null) {
                        bolt.moveTo(this.getX(), this.getY(), this.getZ());
                        bolt.setVisualOnly(true);
                        this.level().addFreshEntity(bolt);
                    }
                }
            }
        }

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            if (this.scytheAnimTick == 0 && this.entityData.get(INSPECTING_SCYTHE)) this.entityData.set(INSPECTING_SCYTHE, false);
            if (this.debugAnimTick == 0 && this.entityData.get(IS_DEBUGGING)) this.entityData.set(IS_DEBUGGING, false);
            if (this.thunderTicks == 0 && this.entityData.get(IS_CASTING_THUNDER)) this.entityData.set(IS_CASTING_THUNDER, false);

            HeroWorldData data = HeroWorldData.get(serverLevel);

            if (this.tickCount == 0 || this.tickCount % 20 == 0) {
                if (this.getSkinVariant() != data.getGlobalSkinVariant()) {
                    this.setSkinVariant(data.getGlobalSkinVariant());
                }

                // [融合逻辑] 定时备份装备与 Curios 饰品
                if (this.getOwnerUUID() != null) {
                    data.setEquipment(this.getOwnerUUID(), this.getArmorItemsTag(), this.getHandItemsTag());
                    data.setCuriosBackItem(this.getOwnerUUID(), this.getCuriosBackItemTag());
                }
            }

            int checkInterval = this.tickCount < 200 ? 10 : 100;
            if (this.tickCount % checkInterval == 0) {
                UUID activeUUID = data.getActiveHeroUUID();

                if (activeUUID != null && !activeUUID.equals(this.getUUID())) {
                    net.minecraft.server.MinecraftServer server = serverLevel.getServer();
                    boolean activeExists = false;
                    for (ServerLevel lvl : server.getAllLevels()) {
                        if (lvl.getEntity(activeUUID) != null) {
                            activeExists = true;
                            break;
                        }
                    }
                    if (activeExists) {
                        HeroDataHandler.updateGlobalTrust(this);
                        this.discard();
                        return;
                    } else {
                        data.setActiveHeroUUID(this.getUUID());
                    }
                }
                if (activeUUID == null) {
                    data.setActiveHeroUUID(this.getUUID());
                }
                data.setLastKnownHeroPos(GlobalPos.of(this.level().dimension(), this.blockPosition()));
            }

            if (this.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                HeroLogic.tick(this);
                if (this.isAlive()) {
                    this.brain.tick();
                    SimpleNeuralNetwork.MindState currentState = this.brain.getState();
                    if (this.getMindState() != currentState) {
                        this.setMindState(currentState);
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

        if (this.level().isClientSide && this.getMindState() == SimpleNeuralNetwork.MindState.JUDGE) {
            if (this.random.nextInt(3) == 0) {
                double x = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
                double y = this.getY() + this.random.nextDouble() * 2.0;
                double z = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
            }
        }
    }

    // ================= [融合逻辑] 原生装备序列化 (1.21.1 规范) =================
    public ListTag getArmorItemsTag() {
        ListTag tag = new ListTag();
        for (ItemStack stack : this.getArmorSlots()) {
            if (!stack.isEmpty()) {
                Tag savedTag = stack.saveOptional(this.registryAccess());
                if (savedTag instanceof CompoundTag ct) tag.add(ct);
            } else {
                tag.add(new CompoundTag());
            }
        }
        return tag;
    }

    public ListTag getHandItemsTag() {
        ListTag tag = new ListTag();
        for (ItemStack stack : this.getHandSlots()) {
            if (!stack.isEmpty()) {
                Tag savedTag = stack.saveOptional(this.registryAccess());
                if (savedTag instanceof CompoundTag ct) tag.add(ct);
            } else {
                tag.add(new CompoundTag());
            }
        }
        return tag;
    }

    public void loadEquipmentFromTag(ListTag armor, ListTag hands) {
        // 1.21.1 规范：部位名称改为 FEET, LEGS, CHEST, HEAD
        net.minecraft.world.entity.EquipmentSlot[] armorSlots = new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.FEET,   // 对应原来的 BOOTS
                net.minecraft.world.entity.EquipmentSlot.LEGS,   // 对应原来的 LEGGINGS
                net.minecraft.world.entity.EquipmentSlot.CHEST,  // 对应原来的 CHESTPLATE
                net.minecraft.world.entity.EquipmentSlot.HEAD    // 对应原来的 HELMET
        };

        // 手持槽位名称未变
        net.minecraft.world.entity.EquipmentSlot[] handSlots = new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND
        };

        if (armor != null && !armor.isEmpty()) {
            for(int i = 0; i < armor.size() && i < armorSlots.length; ++i) {
                Optional<ItemStack> stack = ItemStack.parse(this.registryAccess(), armor.getCompound(i));
                this.setItemSlot(armorSlots[i], stack.orElse(ItemStack.EMPTY));
            }
        }
        if (hands != null && !hands.isEmpty()) {
            for(int i = 0; i < hands.size() && i < handSlots.length; ++i) {
                Optional<ItemStack> stack = ItemStack.parse(this.registryAccess(), hands.getCompound(i));
                this.setItemSlot(handSlots[i], stack.orElse(ItemStack.EMPTY));
            }
        }
    }

    // ================= [融合逻辑] Curios 背部槽序列化 (防崩溃安全版) =================
    public CompoundTag getCuriosBackItemTag() {
        if (ModList.get().isLoaded("curios")) {
            return CuriosSafeInvoker.getBackItemTag(this);
        }
        return new CompoundTag();
    }

    public void setCuriosBackItemFromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        if (ModList.get().isLoaded("curios")) {
            CuriosSafeInvoker.setBackItemFromTag(this, tag);
        }
    }

    public boolean isCuriosBackSlotEmpty() {
        if (!ModList.get().isLoaded("curios")) return true;
        return CuriosSafeInvoker.isBackSlotEmpty(this);
    }

    private static class CuriosSafeInvoker {
        static CompoundTag getBackItemTag(HeroEntity hero) {
            CompoundTag tag = new CompoundTag();
            ItemStack stack = com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero);
            if (!stack.isEmpty()) {
                Tag saved = stack.saveOptional(hero.registryAccess());
                if (saved instanceof CompoundTag ct) return ct;
            }
            return tag;
        }

        static void setBackItemFromTag(HeroEntity hero, CompoundTag tag) {
            Optional<ItemStack> stack = ItemStack.parse(hero.registryAccess(), tag);
            com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.setBackSlotItem(hero, stack.orElse(ItemStack.EMPTY));
        }

        static boolean isBackSlotEmpty(HeroEntity hero) {
            return com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero).isEmpty();
        }
    }

    // --- 保存与加载 ---
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TrustLevel", getTrustLevel());
        compound.putInt("PatrolTimer", patrolTimer);
        compound.putBoolean("CompanionMode", isCompanionMode());
        compound.putInt("ClaimedRewardsFlags", this.entityData.get(CLAIMED_REWARDS_FLAGS));
        compound.putInt("SkinVariant", getSkinVariant());

        if (getOwnerUUID() != null) {
            compound.putUUID("OwnerUUID", getOwnerUUID());
            compound.putString("OwnerUUID_String", getOwnerUUID().toString());
        }
        this.brain.save(compound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isLoadedFromDisk = true;

        if (compound.contains("TrustLevel")) setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("CompanionMode")) setCompanionMode(compound.getBoolean("CompanionMode"));

        UUID ownerUUID = null;
        if (compound.hasUUID("OwnerUUID")) {
            ownerUUID = compound.getUUID("OwnerUUID");
        } else if (compound.contains("OwnerUUID_String")) {
            try { ownerUUID = UUID.fromString(compound.getString("OwnerUUID_String")); } catch (Exception ignored) {}
        }
        if (ownerUUID != null) setOwnerUUID(ownerUUID);

        if (compound.contains("ClaimedRewardsFlags")) {
            this.entityData.set(CLAIMED_REWARDS_FLAGS, compound.getInt("ClaimedRewardsFlags"));
        } else if (compound.contains("ClaimedRewards")) {
            int[] rewards = compound.getIntArray("ClaimedRewards");
            int flags = 0;
            for (int r : rewards) {
                if (r >= 0 && r < 32) flags |= (1 << r);
            }
            this.entityData.set(CLAIMED_REWARDS_FLAGS, flags);
        }

        this.brain.load(compound);

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            setSkinVariant(data.getGlobalSkinVariant());
        } else if (compound.contains("SkinVariant")) {
            setSkinVariant(compound.getInt("SkinVariant"));
        }
    }

    // --- 各种 Getters / Setters / 行为覆盖保持 1.21.1 结构 ---
    public boolean isLoadedFromDisk() { return this.isLoadedFromDisk; }
    public void setLastSummonedTime(long time) { this.lastSummonedTime = time; }
    public long getLastSummonedTime() { return this.lastSummonedTime; }

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

    @Nullable public UUID getOwnerUUID() { return this.entityData.get(OWNER_UUID).orElse(null); }
    public void setOwnerUUID(@Nullable UUID uuid) { this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid)); }

    @Nullable public BlockPos getInvitedPos() { return this.entityData.get(INVITED_POS).orElse(null); }
    public void setInvitedPos(@Nullable BlockPos pos) { this.entityData.set(INVITED_POS, Optional.ofNullable(pos)); }
    public int getInvitedAction() { return this.entityData.get(INVITED_ACTION); }
    public void setInvitedAction(int action) { this.entityData.set(INVITED_ACTION, action); }

    public GoalSelector getGoalSelector() { return this.goalSelector; }
    public void setFallDistance(float distance) { this.fallDistance = distance; }

    public boolean hasClaimedReward(int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return false;
        return (this.entityData.get(CLAIMED_REWARDS_FLAGS) & (1 << rewardId)) != 0;
    }

    public void claimReward(int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return;
        int current = this.entityData.get(CLAIMED_REWARDS_FLAGS);
        this.entityData.set(CLAIMED_REWARDS_FLAGS, current | (1 << rewardId));
    }

    public int getSkinVariant() { return this.entityData.get(SKIN_VARIANT); }
    public void setSkinVariant(int variant) {
        this.entityData.set(SKIN_VARIANT, variant);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData.get(serverLevel).setGlobalSkinVariant(variant);
        }
    }

    // ================= 补充缺失的皮肤与奖励同步方法 =================
    
    public String getCustomSkinName() { 
        return this.entityData.get(CUSTOM_SKIN_NAME); 
    }
    
    public void setCustomSkinName(String name) {
        this.entityData.set(CUSTOM_SKIN_NAME, name);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            // 同步到世界数据
            HeroWorldData.get(serverLevel).setGlobalCustomSkinName(name);
        }
    }

    public int getClaimedRewards() {
        return this.entityData.get(CLAIMED_REWARDS_FLAGS);
    }
    
    public void setClaimedRewards(int flags) {
        this.entityData.set(CLAIMED_REWARDS_FLAGS, flags);
    }
    // ==============================================================

    public SimpleNeuralNetwork.MindState getMindState() {
        int index = this.entityData.get(MIND_STATE);
        SimpleNeuralNetwork.MindState[] states = SimpleNeuralNetwork.MindState.values();
        return (index >= 0 && index < states.length) ? states[index] : SimpleNeuralNetwork.MindState.OBSERVER;
    }
    public void setMindState(SimpleNeuralNetwork.MindState state) { this.entityData.set(MIND_STATE, state.ordinal()); }
    public HeroBrain getHeroBrain() { return this.brain; }

    @Override public boolean isNoGravity() {
        return (level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence")) || isFloating();
    }
    public float getFloatingAmount(float partialTick) { return net.minecraft.util.Mth.lerp(partialTick, clientFloatingAmountO, clientFloatingAmount); }

    @Override public boolean shouldShowName() { return this.getTags().contains("brain_debug"); }
    @Override public boolean isCustomNameVisible() { return this.getTags().contains("brain_debug"); }
    @Override public boolean isInvulnerable() { return level().isClientSide || super.isInvulnerable(); }

    // 粒子生成逻辑
    private void spawnDebugParticles() {
        if (this.random.nextInt(5) != 0) return;
        double yawRad = Math.toRadians(this.yBodyRot);
        double x = this.getX() - Math.sin(yawRad) * 0.8 + Math.cos(yawRad) * 0.3;
        double y = this.getY() + 1.4;
        double z = this.getZ() + Math.cos(yawRad) * 0.8 + Math.sin(yawRad) * 0.3;
        this.level().addParticle(ParticleTypes.ENCHANT, x + (this.random.nextDouble() - 0.5) * 0.5, y + (this.random.nextDouble() - 0.5) * 0.4, z + (this.random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
    }

    private void spawnThunderParticles() {
        if (!this.level().isClientSide) return;
        double yaw = Math.toRadians(this.yBodyRot + 90);
        double handX = this.getX() + Math.cos(yaw) * 0.6;
        double handY = this.getY() + 2.8;
        double handZ = this.getZ() + Math.sin(yaw) * 0.6;

        for(int i=0; i<3; i++) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, handX + (random.nextDouble()-0.5)*0.5, handY + (random.nextDouble()-0.5)*0.5, handZ + (random.nextDouble()-0.5)*0.5, 0, 0, 0);
        }
    }
    public float getThunderProgress(float partialTick) {
        if (this.thunderTicks <= 0) return 0.0F;
        return Mth.clamp(((MAX_THUNDER_TICKS - this.thunderTicks) + partialTick) / (float)MAX_THUNDER_TICKS, 0.0F, 1.0F);
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!this.isCompanionMode() && !(level().dimension() == ModStructures.END_RING_DIMENSION_KEY && getTags().contains("hero_intro_sequence"))) {
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
    @Override public void rideTick() {
        super.rideTick();
        if (this.getVehicle() != null) this.yBodyRot = this.getVehicle().getYRot();
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = HeroLogic.onInteract(this, player, hand);
        return result != InteractionResult.PASS ? result : super.mobInteract(player, hand);
    }
    @Override public boolean hurt(DamageSource source, float amount) { return HeroLogic.onHurt(this, source, amount) || super.hurt(source, amount); }
    @Override public void setHealth(float health) { super.setHealth(this.getMaxHealth()); }

    @Override public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            if (this.getUUID().equals(data.getActiveHeroUUID())) {
                data.setActiveHeroUUID(null);
                data.setLastKnownHeroPos(null);
            }
        }
        if (!this.isRemoved()) super.die(damageSource);
    }
    @Override public boolean canBeAffected(MobEffectInstance instance) { return HeroOtherProtection.canBeAffected(this, instance) && super.canBeAffected(instance); }
    @Override public boolean canBeLeashed() { return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed(); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Integer.MAX_VALUE)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D);
    }

    // --- 交易逻辑 ---
    @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
    @Nullable @Override public Player getTradingPlayer() { return this.tradingPlayer; }
    @Override public MerchantOffers getOffers() {
        if (this.offers == null) this.offers = HeroTrades.getOffers(this);
        return this.offers;
    }
    @Override public void notifyTrade(MerchantOffer offer) {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        HeroTrades.onTrade(this, offer);
    }
    public void resetOffers() {
        this.offers = null;
        if (this.tradingPlayer != null && this.tradingPlayer.containerMenu != null) {
            this.tradingPlayer.sendMerchantOffers(this.tradingPlayer.containerMenu.containerId, getOffers(), 0, getVillagerXp(), showProgressBar(), canRestock());
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

    // --- 动画触发 ---
    public void playScytheInspectAnim() {
        this.scytheAnimTick = 160;
        this.entityData.set(INSPECTING_SCYTHE, true);
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.5F);
    }
    public boolean isInspectingScythe() { return this.scytheAnimTick > 0; }
    public void setGlitching(boolean glitching) { this.entityData.set(IS_GLITCHING, glitching); }
    public boolean isGlitching() { return this.entityData.get(IS_GLITCHING); }
    public void playDebugAnim() {
        this.debugAnimTick = 100;
        this.entityData.set(IS_DEBUGGING, true);
        this.playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 2.0F);
    }
    public boolean isDebugAnim() { return this.debugAnimTick > 0; }
    public void castThunder() {
        this.thunderTicks = MAX_THUNDER_TICKS;
        this.entityData.set(IS_CASTING_THUNDER, true);
        this.playSound(SoundEvents.TRIDENT_THUNDER.value(), 5.0F, 0.8F); // [修正] 适配 1.21.1 签名
    }
    public boolean isCastingThunder() { return this.thunderTicks > 0; }
}