package com.whitecloud233.modid.herobrine_companion.entity;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightDebugLog;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.modid.herobrine_companion.entity.logic.*;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state.ObserverStateDefinition;

import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.event.HeroTrades;
import com.whitecloud233.modid.herobrine_companion.event.HeroVisuals;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HeroEntity extends PathfinderMob implements Merchant {
    private static final String[] VIRTUAL_BULLET_KEYWORDS = new String[] {
            "bullet", "ammo", "round", "cartridge", "shell", "slug", "musket_ball", "musketball"
    };

    public static final EntityDataAccessor<Boolean> IS_FLOATING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> TRUST_LEVEL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_COMPANION_MODE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> CUSTOM_SKIN_NAME = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Optional<BlockPos>> INVITED_POS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final EntityDataAccessor<Integer> INVITED_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<ItemStack> VISUAL_MAIN_HAND_ITEM = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> VISUAL_OFF_HAND_ITEM = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Boolean> VISUAL_EATING_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> MIND_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> INSPECTING_SCYTHE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_GLITCHING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_DEBUGGING = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_CASTING_THUNDER = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_CHALLENGE_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> CHALLENGE_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> BATTLE_MODE_ACTIVE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_TICKS = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_COMBO_STEP = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BATTLE_ACTION_SERIAL = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);

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


    public boolean isStateDirty = true;
    private final Set<Integer> claimedRewards = new HashSet<>();
    public float clientFloatingAmount;
    public float clientFloatingAmountO;
    public boolean clientSideSetupDone = false;
    public int patrolTimer = 2400;
    public MoveControl moveControl;
    private int outOfWaterTimer = 0;
    private long lastSummonedTime = 0;
    private boolean isLoadedFromDisk = false;
    private boolean handlingHeroHurt = false;
    private int battleBufferedAction = BATTLE_BUFFER_NONE;
    private int battleBufferTicks = 0;
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
    private final FlyingPathNavigation flyingNavigation;
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
        this.flyingNavigation = this.navigation instanceof FlyingPathNavigation flying ? flying : new FlyingPathNavigation(this, level);
        this.groundNavigation = new GroundPathNavigation(this, level);
        this.groundNavigation.setCanFloat(false);
        // 👇 【新增】：在实体诞生时，强制让 Boss 血条默认保持隐藏
        this.bossEvent.setVisible(false);
        // 👇 [新增这一行]：允许地面寻路算法把门视为可开启的通道
        this.groundNavigation.setCanOpenDoors(true);
        this.refreshNavigationMode();
    }

    @Override
    protected void registerGoals() {
        if (this.getPersistentData().getBoolean("IsChallengeActive")) {
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);
            this.goalSelector.addGoal(1, new com.whitecloud233.modid.herobrine_companion.client.fight.goal.HeroPhase1Goal(this));
        } else {
            HeroAI.registerGoals(this);
        }
    }
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // 【核心修复】：无论是因为死亡、区块卸载还是其他原因被移除，
        // 都在生命周期结束的最后一刻，强制将自己从全局高速缓存中踢出，防止内存泄漏！
        com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES.remove(this);
    }
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public PathNavigation getNavigation() {
        return this.navigation;
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
        if (BATTLE_MODE_ACTIVE.equals(key)) {
            HeroEpicFightDebugLog.transition(this, "HeroEntity.onSyncedDataUpdated.battleMode",
                    this.entityData.get(BATTLE_MODE_ACTIVE) + "|" + HeroEpicFightDebugLog.heroIdentity(this),
                    "caller=syncedEntityData,battle=" + this.entityData.get(BATTLE_MODE_ACTIVE)
                            + ",entity=" + HeroEpicFightDebugLog.heroIdentity(this)
                            + ",state=" + HeroEpicFightDebugLog.heroCoreState(this));
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 👇 【核心修改】：强制清空受伤无敌时间渲染，取消挑战模式下的闪红效果
        if (this.getEntityData().get(IS_CHALLENGE_ACTIVE)) {
            this.hurtTime = 0;
        }

        HeroVisuals.tickClientAnimations(this);

        if (!this.level().isClientSide) {
            // 重置服务端动画标记
            if (this.scytheAnimTick == 0 && this.entityData.get(INSPECTING_SCYTHE)) this.entityData.set(INSPECTING_SCYTHE, false);
            if (this.debugAnimTick == 0 && this.entityData.get(IS_DEBUGGING)) this.entityData.set(IS_DEBUGGING, false);
            if (this.thunderTicks == 0 && this.entityData.get(IS_CASTING_THUNDER)) this.entityData.set(IS_CASTING_THUNDER, false);
            if (this.getEntityData().get(IS_CHALLENGE_ACTIVE) || this.isInspectingScythe() || this.isDebugAnim() || this.isCastingThunder()) {
                this.resetBattleCombatState();
            }

            com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeState.tick(this);

            // 同步血条逻辑
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

            // 👇【核心修复】：将全局缓存登记提到维度判断的外面！
            // 这样一来，哪怕 Hero 在试炼擂台（End Ring）被冻结了大脑，也能作为肉体被正确登记到花名册中
            if (this.isAlive() && !this.isRemoved()) {
                com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES.add(this);
            }

            // 👇 这是你原本的维度判断
            if (this.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                HeroLogic.tick(this);
                if (this.isAlive()) {
                    this.brain.tick();
                    if (this.getMindState() != this.brain.getState()) this.setMindState(this.brain.getState());

                    if (this.level() instanceof ServerLevel serverLevel) {
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
        return !this.shouldPreventFriendlyFire(target) && super.canAttack(target);
    }

    public boolean shouldPreventFriendlyFire(@Nullable Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        if (living == this || living instanceof HeroEntity) {
            return true;
        }
        if (!this.hasCompanionFriendlyFireProtection()) {
            return false;
        }
        if (living instanceof Player) {
            return true;
        }
        if (this.isAlliedTo(living) || living.isAlliedTo(this)) {
            return true;
        }

        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID != null && ownerUUID.equals(living.getUUID())) {
            return true;
        }

        Player owner = this.getOwnerPlayer();
        return owner != null && (living.is(owner) || living.isAlliedTo(owner) || owner.isAlliedTo(living));
    }

    public boolean hasCompanionFriendlyFireProtection() {
        return this.getOwnerUUID() != null && !this.isChallengeActiveState();
    }

    public boolean isChallengeActiveState() {
        return this.getEntityData().get(IS_CHALLENGE_ACTIVE)
                || this.getPersistentData().getBoolean("IsChallengeActive");
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

        if (weaponStack == null || weaponStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (weaponStack.getItem() instanceof ProjectileWeaponItem || weaponStack.getItem() instanceof CrossbowItem) {
            ItemStack virtualProjectile = this.getVirtualProjectileFor(weaponStack);
            return virtualProjectile.isEmpty() ? new ItemStack(Items.ARROW) : virtualProjectile;
        }

        UseAnim useAnim = weaponStack.getUseAnimation();
        if (useAnim == UseAnim.BOW || useAnim == UseAnim.CROSSBOW) {
            ItemStack virtualProjectile = this.getVirtualProjectileFor(weaponStack);
            return virtualProjectile.isEmpty() ? new ItemStack(Items.ARROW) : virtualProjectile;
        }

        return ItemStack.EMPTY;
    }

    private ItemStack getVirtualProjectileFor(ItemStack weaponStack) {
        ResourceLocation weaponId = ForgeRegistries.ITEMS.getKey(weaponStack.getItem());
        String weaponPath = weaponId != null ? weaponId.getPath().toLowerCase() : "";

        if (!this.looksLikeGunWeapon(weaponPath)) {
            return new ItemStack(Items.ARROW);
        }

        for (net.minecraft.world.item.Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) {
                continue;
            }

            String itemPath = itemId.getPath().toLowerCase();
            for (String keyword : VIRTUAL_BULLET_KEYWORDS) {
                if (itemPath.contains(keyword)) {
                    return new ItemStack(item);
                }
            }
        }

        return new ItemStack(Items.ARROW);
    }

    private boolean looksLikeGunWeapon(String weaponPath) {
        return weaponPath.contains("gun")
                || weaponPath.contains("firearm")
                || weaponPath.contains("rifle")
                || weaponPath.contains("pistol")
                || weaponPath.contains("revolver")
                || weaponPath.contains("musket")
                || weaponPath.contains("shotgun")
                || weaponPath.contains("cannon")
                || weaponPath.contains("blaster");
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
        if (this.level().isClientSide || !this.isBattleModeActive() || source == null) {
            return;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker
                && com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroBattleStanceGoal.canHeroAttackTarget(this, livingAttacker)) {
            this.setLastHurtByMob(livingAttacker);
            if (this.getTarget() != livingAttacker) {
                this.setTarget(livingAttacker);
            }
        }
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
        super.setItemSlot(slot, stack);
        if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND
                && !ItemStack.matches(previousStack, this.getItemBySlot(slot))) {
            HeroEpicFightDebugLog.event(this, "HeroEntity.setItemSlot", "slot=" + slot
                    + ",prev=" + HeroEpicFightDebugLog.itemKey(previousStack)
                    + ",next=" + HeroEpicFightDebugLog.itemKey(this.getItemBySlot(slot))
                    + ",stateBeforeReset=" + HeroEpicFightDebugLog.heroCoreState(this));
            this.stopUsingItem();
            if (!this.level().isClientSide) {
                this.getNavigation().stop();
            }
            this.resetBattleCombatState();
        }
        // 标记为脏，让下一次 100 tick 循环触发硬盘写入
        this.isStateDirty = true;
    }
    // 在 die() 方法中修改：
    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID owner = this.getOwnerUUID();
            if (owner != null && this.getUUID().equals(data.getActiveHeroUUID(owner))) {
                data.setActiveHeroUUID(owner, null);
                data.setLastKnownHeroPos(owner, null);
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
            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToPlayer(
                    new com.whitecloud233.modid.herobrine_companion.network.SavePosePacket(this.getId(), this.isPoseEditing, this.customPoseAngles),
                    player
            );
        }

        // 当其他玩家开始追踪这个 Hero 时，补发一次完整外观快照，
        // 包括多人模式下无法靠本地路径还原的自定义皮肤与 Curios/AW 背饰数据。
        com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToPlayer(
                new com.whitecloud233.modid.herobrine_companion.network.SyncHeroCosmeticsPacket(this),
                player
        );

        // 👇 【新增修复】：当玩家开始追踪 Hero 实体时，强制同步其已领取的奖励状态
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel && getOwnerUUID() != null) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToPlayer(
                    new com.whitecloud233.modid.herobrine_companion.network.SyncRewardsPacket(this.getId(), data.getClaimedRewards(getOwnerUUID())),
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
    @Override public boolean canBeLeashed(Player player) { return HeroOtherProtection.canBeLeashed(this) && super.canBeLeashed(player); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.10D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_FLOATING, true);
        this.entityData.define(TRUST_LEVEL, 0);
        this.entityData.define(IS_COMPANION_MODE, false);
        this.entityData.define(SKIN_VARIANT, SKIN_HEROBRINE);
        this.entityData.define(CUSTOM_SKIN_NAME, "");
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(INVITED_POS, Optional.empty());
        this.entityData.define(INVITED_ACTION, 0);
        this.entityData.define(VISUAL_MAIN_HAND_ITEM, ItemStack.EMPTY);
        this.entityData.define(VISUAL_OFF_HAND_ITEM, ItemStack.EMPTY);
        this.entityData.define(VISUAL_EATING_ACTIVE, false);
        this.entityData.define(MIND_STATE, 0);
        this.entityData.define(INSPECTING_SCYTHE, false);
        this.entityData.define(IS_GLITCHING, false);
        this.entityData.define(IS_DEBUGGING, false);
        this.entityData.define(IS_CASTING_THUNDER, false);
        this.entityData.define(IS_CHALLENGE_ACTIVE, false);
        this.entityData.define(CHALLENGE_TICKS, 0);
        this.entityData.define(BATTLE_MODE_ACTIVE, false);
        this.entityData.define(BATTLE_ACTION_STATE, BATTLE_ACTION_IDLE);
        this.entityData.define(BATTLE_ACTION_TICKS, 0);
        this.entityData.define(BATTLE_COMBO_STEP, 0);
        this.entityData.define(BATTLE_ACTION_SERIAL, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TrustLevel", getTrustLevel());
        compound.putInt("PatrolTimer", patrolTimer);
        compound.putBoolean("CompanionMode", isCompanionMode());
        compound.putBoolean("BattleModeActive", isBattleModeActive());
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

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isLoadedFromDisk = true;

        com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeState.onRestoreFromDisk(this);

        if (compound.contains("TrustLevel")) setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("CompanionMode")) setCompanionMode(compound.getBoolean("CompanionMode"));
        if (compound.contains("BattleModeActive")) setBattleModeActiveFrom("HeroEntity.readAdditionalSaveData", compound.getBoolean("BattleModeActive"));
        if (this.getPersistentData().getBoolean("IsChallengeActive")) {
            this.entityData.set(IS_CHALLENGE_ACTIVE, true);
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
    public CompoundTag getAccessoriesDataTag() { return HeroEquipment.getAccessoriesDataTag(this); }
    public void setAccessoriesDataFromTag(CompoundTag tag) { HeroEquipment.setAccessoriesDataFromTag(this, tag); }

    // Getters & Setters
    public boolean isLoadedFromDisk() { return this.isLoadedFromDisk; }
    public boolean isFloating() { return entityData.get(IS_FLOATING); }
    public void setFloating(boolean floating) {
        entityData.set(IS_FLOATING, floating);
        this.refreshNavigationMode();
    }
    public int getTrustLevel() { return entityData.get(TRUST_LEVEL); }
    public void setTrustLevel(int level) {
        entityData.set(TRUST_LEVEL, level);
        this.isStateDirty = true; // 👈 新增这一行
        if (!this.level().isClientSide) HeroDataHandler.updateGlobalTrust(this);
    }
    public void increaseTrust(int amount) { setTrustLevel(getTrustLevel() + amount); }
    public boolean isCompanionMode() { return entityData.get(IS_COMPANION_MODE); }
    public void setCompanionMode(boolean active) { entityData.set(IS_COMPANION_MODE, active); }
    public boolean isBattleModeActive() { return entityData.get(BATTLE_MODE_ACTIVE); }
    public void setBattleModeActive(boolean active) {
        this.setBattleModeActiveFrom(null, active);
    }
    public void setBattleModeActiveFrom(@Nullable String callerTag, boolean active) {
        this.applyBattleModeActive(HeroEpicFightDebugLog.resolveCallerTag(callerTag), active);
    }
    private void applyBattleModeActive(String callerTag, boolean active) {
        boolean previous = this.isBattleModeActive();
        String entityIdentity = HeroEpicFightDebugLog.heroIdentity(this);
        String trace = HeroEpicFightDebugLog.captureCallerTrace(5);
        if (previous == active) {
            HeroEpicFightDebugLog.repeatedEvent(this, "HeroEntity.setBattleModeActive.noop",
                    callerTag + "|" + active + "|" + entityIdentity,
                    "caller=" + callerTag + ",prev=" + previous + ",next=" + active
                            + ",entity=" + entityIdentity + ",state=" + HeroEpicFightDebugLog.heroCoreState(this)
                            + ",trace=" + trace);
            return;
        }
        HeroEpicFightDebugLog.event(this, "HeroEntity.setBattleModeActive.begin",
                "caller=" + callerTag + ",prev=" + previous + ",next=" + active
                        + ",entity=" + entityIdentity + ",state=" + HeroEpicFightDebugLog.heroCoreState(this)
                        + ",trace=" + trace);
        entityData.set(BATTLE_MODE_ACTIVE, active);
        if (active) {
            this.clearNearbySubmissionEffects();
            this.setFloating(false);
            this.setNoGravity(false);
            this.resetBattleCombatState();
        } else {
            this.setTarget(null);
            this.getNavigation().stop();
            this.resetBattleCombatState();
        }
        HeroEpicFightDebugLog.transition(this, "HeroEntity.setBattleModeActive.end",
                callerTag + "|" + active + "|" + HeroEpicFightDebugLog.heroCoreState(this),
                "caller=" + callerTag + ",entity=" + HeroEpicFightDebugLog.heroIdentity(this)
                        + ",state=" + HeroEpicFightDebugLog.heroCoreState(this));
    }
    public int getBattleActionState() { return entityData.get(BATTLE_ACTION_STATE); }
    public void setBattleActionState(int state) { entityData.set(BATTLE_ACTION_STATE, state); }
    public int getBattleActionTicks() { return entityData.get(BATTLE_ACTION_TICKS); }
    public void setBattleActionTicks(int ticks) { entityData.set(BATTLE_ACTION_TICKS, ticks); }
    public int getBattleComboStep() { return entityData.get(BATTLE_COMBO_STEP); }
    public void setBattleComboStep(int step) { entityData.set(BATTLE_COMBO_STEP, step); }
    public int getBattleActionSerial() { return entityData.get(BATTLE_ACTION_SERIAL); }
    public void setBattleActionSerial(int serial) { entityData.set(BATTLE_ACTION_SERIAL, serial); }
    public void beginBattleAction(int state) {
        HeroEpicFightDebugLog.event(this, "HeroEntity.beginBattleAction", "nextAction=" + state + ",stateBefore=" + HeroEpicFightDebugLog.heroCoreState(this));
        this.clearBattleBufferedAction();
        this.setBattleActionState(state);
        this.setBattleActionTicks(0);
        if (state == BATTLE_ACTION_LIGHT_COMBO_1 || state == BATTLE_ACTION_LIGHT_COMBO_2) {
            this.setBattleActionSerial(this.getBattleActionSerial() + 1);
        }
        HeroEpicFightDebugLog.transition(this, "HeroEntity.beginBattleAction.end", state + "|" + this.getBattleActionSerial() + "|" + this.getBattleComboStep(), "state=" + HeroEpicFightDebugLog.heroCoreState(this) + ",serial=" + this.getBattleActionSerial());
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
        return this.battleBufferedAction;
    }
    public int getBattleBufferTicks() {
        return this.battleBufferTicks;
    }
    public boolean hasBattleBufferedAction() {
        return this.battleBufferedAction != BATTLE_BUFFER_NONE && this.battleBufferTicks > 0;
    }
    public void queueBattleBufferedAction(int bufferedAction, int ticks) {
        if (!this.isBattleModeActive() || bufferedAction == BATTLE_BUFFER_NONE || ticks <= 0) {
            this.clearBattleBufferedAction();
            return;
        }

        this.battleBufferedAction = bufferedAction;
        this.battleBufferTicks = Math.min(20, ticks);
    }
    public void tickBattleBufferedAction() {
        if (!this.hasBattleBufferedAction()) {
            this.clearBattleBufferedAction();
            return;
        }

        this.battleBufferTicks--;
        if (this.battleBufferTicks <= 0) {
            this.clearBattleBufferedAction();
        }
    }
    public void clearBattleBufferedAction() {
        this.battleBufferedAction = BATTLE_BUFFER_NONE;
        this.battleBufferTicks = 0;
    }
    public void resetBattleActionTimeline() {
        if (this.getBattleActionState() != BATTLE_ACTION_IDLE || this.getBattleActionTicks() != 0 || this.hasBattleBufferedAction()) {
            HeroEpicFightDebugLog.event(this, "HeroEntity.resetBattleActionTimeline", "stateBefore=" + HeroEpicFightDebugLog.heroCoreState(this) + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
        }
        this.clearBattleBufferedAction();
        this.setBattleActionState(BATTLE_ACTION_IDLE);
        this.setBattleActionTicks(0);
    }
    public void resetBattleCombatState() {
        if (this.getBattleActionState() != BATTLE_ACTION_IDLE || this.getBattleActionTicks() != 0 || this.getBattleComboStep() != 0 || this.hasBattleBufferedAction()) {
            HeroEpicFightDebugLog.event(this, "HeroEntity.resetBattleCombatState", "stateBefore=" + HeroEpicFightDebugLog.heroCoreState(this) + ",buffered=" + this.getBattleBufferedAction() + "/" + this.getBattleBufferTicks());
        }
        this.resetBattleActionTimeline();
        this.setBattleComboStep(0);
        HeroEpicFightDebugLog.transition(this, "HeroEntity.resetBattleCombatState.end", HeroEpicFightDebugLog.heroCoreState(this), "state=" + HeroEpicFightDebugLog.heroCoreState(this));
    }
    private void refreshNavigationMode() {
        if (this.flyingNavigation == null || this.groundNavigation == null) {
            return;
        }

        PathNavigation desiredNavigation = this.isFloating() ? this.flyingNavigation : this.groundNavigation;
        if (this.navigation == desiredNavigation) {
            return;
        }

        if (this.navigation != null) {
            this.navigation.stop();
        }
        this.flyingNavigation.stop();
        this.groundNavigation.stop();
        this.navigation = desiredNavigation;
    }
    private void clearNearbySubmissionEffects() {
        if (this.level() == null) {
            return;
        }

        double clearRange = Math.max(24.0D, this.getAttributeValue(Attributes.FOLLOW_RANGE));
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(clearRange), mob -> mob.isAlive() && mob != this)) {
            mob.getPersistentData().putBoolean("HeroSubmission", false);
            mob.getPersistentData().remove("HeroSubmissionYaw");
            if (mob.isSilent()) {
                mob.setSilent(false);
            }
        }
    }
    public int getSkinVariant() { return entityData.get(SKIN_VARIANT); }
    // 修复 setSkinVariant 和 setCustomSkinName:
    public void setSkinVariant(int variant) {
        entityData.set(SKIN_VARIANT, variant);
        this.isStateDirty = true; // 👈 新增这一行
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            UUID owner = getOwnerUUID();
            if (owner != null) HeroWorldData.get(serverLevel).setSkinVariant(owner, variant);
        }
    }
    public String getCustomSkinName() { return entityData.get(CUSTOM_SKIN_NAME); }
    public void setCustomSkinName(String name) {
        entityData.set(CUSTOM_SKIN_NAME, name);
        this.isStateDirty = true; // 👈 新增这一行
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            UUID owner = getOwnerUUID();
            if (owner != null) HeroWorldData.get(serverLevel).setCustomSkinName(owner, name);
        }
    }
    @Nullable public UUID getOwnerUUID() { return this.entityData.get(OWNER_UUID).orElse(null); }
    public void setOwnerUUID(@Nullable UUID uuid) { this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid)); }
    public Component getOwnerDisplayNameComponent() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID == null) {
            return Component.translatable("message.herobrine_companion.hero_owner_unknown");
        }

        Player onlineOwner = this.level().getPlayerByUUID(ownerUUID);
        if (onlineOwner != null) {
            return Component.literal(onlineOwner.getGameProfile().getName());
        }

        if (this.level().getServer() != null) {
            java.util.Optional<GameProfile> cachedProfile = this.level().getServer().getProfileCache().get(ownerUUID);
            if (cachedProfile.isPresent() && cachedProfile.get().getName() != null && !cachedProfile.get().getName().isEmpty()) {
                return Component.literal(cachedProfile.get().getName());
            }
        }

        return Component.translatable("message.herobrine_companion.hero_owner_unknown");
    }
    public Component createNotYourHeroMessage() {
        return Component.translatable("message.herobrine_companion.not_your_hero")
                .append(Component.literal(" "))
                .append(Component.translatable("message.herobrine_companion.hero_owner_hint", this.getOwnerDisplayNameComponent()))
                .withStyle(ChatFormatting.RED);
    }
    @Nullable public BlockPos getInvitedPos() { return this.entityData.get(INVITED_POS).orElse(null); }
    public void setInvitedPos(@Nullable BlockPos pos) { this.entityData.set(INVITED_POS, Optional.ofNullable(pos)); }
    public int getInvitedAction() { return this.entityData.get(INVITED_ACTION); }
    public void setInvitedAction(int action) { this.entityData.set(INVITED_ACTION, action); }
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
    public GoalSelector getGoalSelector() { return this.goalSelector; }
    public void setFallDistance(float distance) { this.fallDistance = distance; }
    public HeroBrain getHeroBrain() { return this.brain; }

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
        if (this.entityData.get(IS_CHALLENGE_ACTIVE)) {
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
    public void castThunder() { this.thunderTicks = MAX_THUNDER_TICKS; this.entityData.set(IS_CASTING_THUNDER, true); this.playSound(SoundEvents.TRIDENT_THUNDER, 5.0F, 0.8F); }
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
