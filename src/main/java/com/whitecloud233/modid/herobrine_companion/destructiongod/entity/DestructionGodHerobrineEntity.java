package com.whitecloud233.modid.herobrine_companion.destructiongod.entity;

import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.ai.goal.DestructionGodCombatGoal;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class DestructionGodHerobrineEntity extends Monster {
    public static final EntityDataAccessor<Integer> BOSS_PHASE = SynchedEntityData.defineId(DestructionGodHerobrineEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> SKILL_STATE = SynchedEntityData.defineId(DestructionGodHerobrineEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> SKILL_TICKS = SynchedEntityData.defineId(DestructionGodHerobrineEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(DestructionGodHerobrineEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int PHASE_1 = 1;
    public static final int PHASE_2 = 2;
    public static final int PHASE_3 = 3;
    public static final int PHASE_4 = 4;

    public static final int SKILL_NONE = 0;
    public static final int SKILL_WORLD_REND = 1;
    public static final int SKILL_APOCALYPSE_CRACK = 2;
    public static final int SKILL_WORLD_PEEL = 3;
    public static final int SKILL_WORLD_COLLAPSE = 4;
    public static final int SKILL_SCYTHE_ICHIMONJI = 5;
    public static final int SKILL_SCYTHE_REVERSE_MOON = 6;
    public static final int SKILL_SCYTHE_EXECUTION = 7;
    public static final int SKILL_DESTRUCTION_LIGHTNING = 8;
    public static final int SKILL_DESTRUCTION_GOD_ORB = 9;
    public static final int SKILL_THUNDER_SKYNET = 10;
    public static final int SKILL_SCYTHE_FAULT_SPLIT = 11;
    public static final int GLOBAL_SKILL_INTERVAL_TICKS = 28;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.herobrine_companion.destruction_god_herobrine"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private final int[] skillCooldowns = new int[DestructionSkill.values().length];
    private int globalSkillIntervalTicks;
    private boolean skillEffectTriggered;
    private int skillTriggerMask;
    @Nullable
    private UUID executionTargetUuid;

    public DestructionGodHerobrineEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setNoGravity(true);
        this.xpReward = 250;
        this.bossEvent.setVisible(true);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.POEM_OF_THE_END.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DestructionGodCombatGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 800.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BOSS_PHASE, PHASE_1);
        this.entityData.define(SKILL_STATE, SKILL_NONE);
        this.entityData.define(SKILL_TICKS, 0);
        this.entityData.define(ENRAGED, false);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;

        if (!this.level().isClientSide) {
            this.updateBossPhase();
            this.tickCooldowns();
            this.tickSkillState();
            this.maintainDivineHover();
            this.tickBossEvent();
            this.ensureScytheEquipped();
        }
    }

    private void maintainDivineHover() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.getSkillState() == SKILL_SCYTHE_EXECUTION && this.getSkillTicks() >= 20 && this.getSkillTicks() <= 32) {
            return;
        }

        int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.getX()), Mth.floor(this.getZ()));
        double desiredY = surfaceY + 5.5D;
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            int targetSurfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(target.getX()), Mth.floor(target.getZ()));
            desiredY = Math.max(desiredY, Math.max(target.getY() + 3.8D, targetSurfaceY + 4.5D));
            desiredY = Math.min(desiredY, target.getY() + 12.0D);
        }
        if (this.isCastingSkill()) {
            desiredY += 1.5D;
        }
        if (this.getSkillState() == SKILL_THUNDER_SKYNET && target != null && target.isAlive()) {
            int targetSurfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(target.getX()), Mth.floor(target.getZ()));
            desiredY = Math.max(desiredY, Math.max(target.getY() + 10.0D, targetSurfaceY + 12.5D));
            desiredY = Math.min(desiredY, target.getY() + 18.0D);
        }

        double deltaY = desiredY - this.getY();
        Vec3 motion = this.getDeltaMovement();
        double correction = Mth.clamp(deltaY * 0.08D, -0.16D, 0.32D);
        if (Math.abs(deltaY) < 0.6D) {
            correction *= 0.25D;
        }
        this.setDeltaMovement(motion.x, motion.y * 0.45D + correction, motion.z);
    }

    private void ensureScytheEquipped() {
        if (this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.POEM_OF_THE_END.get()));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    private void updateBossPhase() {
        float ratio = this.getHealth() / this.getMaxHealth();
        int nextPhase = ratio <= 0.15F ? PHASE_4 : ratio <= 0.40F ? PHASE_3 : ratio <= 0.70F ? PHASE_2 : PHASE_1;
        if (nextPhase != this.getBossPhase()) {
            this.entityData.set(BOSS_PHASE, nextPhase);
            if (nextPhase >= PHASE_3) {
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.7F);
            }
            if (nextPhase == PHASE_4) {
                this.entityData.set(ENRAGED, true);
                this.playSound(SoundEvents.WITHER_SPAWN, 4.0F, 0.65F);
            }
        }
    }

    private void tickCooldowns() {
        for (int index = 0; index < this.skillCooldowns.length; index++) {
            if (this.skillCooldowns[index] > 0) this.skillCooldowns[index]--;
        }
        if (this.globalSkillIntervalTicks > 0) this.globalSkillIntervalTicks--;
    }

    private void tickSkillState() {
        if (!this.isCastingSkill()) {
            return;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.65D, 1.0D, 0.65D));
        this.entityData.set(SKILL_TICKS, this.getSkillTicks() + 1);
        if (this.getSkillTicks() >= this.getSkillDuration()) {
            this.finishSkill();
        }
    }

    private void tickBossEvent() {
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.bossEvent.setName(this.getDisplayName());
    }

    public int getBossPhase() {
        return this.entityData.get(BOSS_PHASE);
    }

    public int getSkillState() {
        return this.entityData.get(SKILL_STATE);
    }

    public int getSkillTicks() {
        return this.entityData.get(SKILL_TICKS);
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    public boolean isCastingSkill() {
        return this.getSkillState() != SKILL_NONE;
    }

    public boolean hasGlobalSkillInterval() {
        return this.globalSkillIntervalTicks > 0;
    }

    public void beginSkill(int skill) {
        this.entityData.set(SKILL_STATE, skill);
        this.entityData.set(SKILL_TICKS, 0);
        this.skillEffectTriggered = false;
        this.skillTriggerMask = 0;
        this.getNavigation().stop();
        this.playSkillSound(skill);
        this.setSkillCooldown(skill, getBaseCooldown(skill));
    }

    public void beginSkill(int skill, @Nullable LivingEntity executionTarget) {
        this.beginSkill(skill);
        this.setExecutionTarget(executionTarget);
    }

    public void finishSkill() {
        this.entityData.set(SKILL_STATE, SKILL_NONE);
        this.entityData.set(SKILL_TICKS, 0);
        this.skillEffectTriggered = false;
        this.skillTriggerMask = 0;
        this.executionTargetUuid = null;
        this.globalSkillIntervalTicks = GLOBAL_SKILL_INTERVAL_TICKS;
    }

    public boolean consumeSkillTriggerWindow() {
        if (!this.isCastingSkill()) {
            return false;
        }
        if (this.skillEffectTriggered) {
            return false;
        }
        if (this.getSkillTicks() < this.getSkillTriggerTick()) {
            return false;
        }
        this.skillEffectTriggered = true;
        return true;
    }

    public int getSkillDuration() {
        return DestructionSkill.fromId(this.getSkillState()).duration(this.getBossPhase());
    }

    public int getSkillTriggerTick() {
        return DestructionSkill.fromId(this.getSkillState()).castingTriggerTick();
    }

    public static int getSkillDuration(int skill) {
        return DestructionSkill.fromId(skill).duration();
    }

    public static int getThunderSkyNetDuration(int phase) {
        return DestructionSkill.THUNDER_SKYNET.duration(phase);
    }

    public static int getSkillTriggerTick(int skill) {
        return DestructionSkill.fromId(skill).triggerTick();
    }

    public boolean consumeSkillStageTrigger(int stageIndex, int triggerTick) {
        if (!this.isCastingSkill() || stageIndex < 0 || triggerTick < 0) {
            return false;
        }
        int bit = 1 << stageIndex;
        if ((this.skillTriggerMask & bit) != 0) {
            return false;
        }
        if (this.getSkillTicks() < triggerTick) {
            return false;
        }
        this.skillTriggerMask |= bit;
        if (stageIndex == 0) {
            this.skillEffectTriggered = true;
        }
        return true;
    }

    public boolean isSkillReady(int skill) {
        if (this.hasGlobalSkillInterval()) return false;
        DestructionSkill descriptor = DestructionSkill.fromId(skill);
        return descriptor != DestructionSkill.NONE && this.skillCooldowns[descriptor.ordinal()] <= 0;
    }

    public void setSkillCooldown(int skill, int value) {
        DestructionSkill descriptor = DestructionSkill.fromId(skill);
        if (descriptor != DestructionSkill.NONE) this.skillCooldowns[descriptor.ordinal()] = value;
    }

    private int getBaseCooldown(int skill) {
        return DestructionSkill.fromId(skill).cooldown();
    }

    private void playSkillSound(int skill) {
        DestructionSkill.SoundCue sound = DestructionSkill.fromId(skill).sound();
        if (sound != null) this.playSound(sound.event().get(), sound.volume(), sound.pitch());
    }

    public boolean isScytheSkill(int skill) {
        return DestructionSkill.fromId(skill).isScythe();
    }

    public void setExecutionTarget(@Nullable LivingEntity target) {
        this.executionTargetUuid = target != null ? target.getUUID() : null;
    }

    @Nullable
    public LivingEntity getExecutionTarget() {
        if (this.executionTargetUuid == null || this.level().isClientSide) {
            return null;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(this.executionTargetUuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Player || target instanceof Enemy;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }
}

