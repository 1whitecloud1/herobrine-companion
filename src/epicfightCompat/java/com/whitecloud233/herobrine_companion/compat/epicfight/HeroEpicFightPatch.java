package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.mojang.datafixers.util.Pair;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.TargetChasingGoal;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

public class HeroEpicFightPatch extends HumanoidMobPatch<HeroEntity> {
    private static final double MELEE_CHASE_SPEED = 1.35D;
    private static final Map<String, Optional<Method>> COMPAT_METHOD_CACHE = new ConcurrentHashMap<>();

    private boolean infantryAiConfigured;
    private String lastWeaponProfileKey = "";
    private Goal heroAttackGoal;
    private Goal heroChasingGoal;
    private boolean offhandSwapActive;
    private ItemStack expectedSwappedMainhand = ItemStack.EMPTY;
    private ItemStack expectedSwappedOffhand = ItemStack.EMPTY;

    public HeroEpicFightPatch(HeroEntity hero) {
        super(hero, Factions.NEUTRAL);
    }

    @Override
    public boolean overrideRender() {
        HeroEntity hero = this.getOriginal();
        if (hero != null && hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return false;
        }

        return super.overrideRender();
    }

    @Override

    public void onConstructed(HeroEntity hero) {
        super.onConstructed(hero);
    }


    @Override
    protected void initAI() {
        HeroEntity hero = this.getOriginal();
        if (hero == null || !hero.isAddedToLevel()|| !hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
            return;
        }

        super.initAI();
    }
    @Override
    public void onJoinWorld(HeroEntity hero, Level level, boolean isLogicalClient) {
        super.onJoinWorld(hero, level, isLogicalClient);
        HeroEpicFightWeaponProfiles.bootstrap();
        this.ensureInfantryAiConfigured();
        if (this.isEquipmentReady(hero)) {
            this.syncWeaponLivingMotions(true);
        }
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);

        HeroEntity hero = this.getOriginal();
        boolean battleMode = hero != null && hero.isBattleModeActive();
        Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations = new LinkedHashMap<>();
        this.applyBaseLivingAnimations(livingAnimations, battleMode);
        livingAnimations.forEach(animator::addLivingAnimation);
    }

    @Override
    public void onStartTracking(ServerPlayer trackingPlayer) {
        if (this.isEquipmentReady(this.getOriginal())) {
            this.syncWeaponLivingMotions(true);
        }
    }

    @Override
    public void preTickServer() {
        HeroEpicFightWeaponProfiles.bootstrap();
        HeroEntity hero = this.getOriginal();
        String currentProfileKey = this.getCurrentWeaponProfileKey(hero);
        boolean weaponProfileChanged = !currentProfileKey.equals(this.lastWeaponProfileKey);

        this.ensureInfantryAiConfigured();
        this.syncWeaponLivingMotions(weaponProfileChanged);
        super.preTickServer();
        HeroCombatPlanner.tickEpicFightActionClock(hero);
    }

    @Override
    public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
        HeroEntity hero = this.getOriginal();
        if (hero == null || !hero.isAddedToLevel()) {
            return;
        }

        this.infantryAiConfigured = false;
        if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.syncWeaponLivingMotions(true);
            return;
        }

        super.updateHeldItem(fromCap, toCap, from, to, hand);

        if (!hero.level().isClientSide) {
            this.ensureInfantryAiConfigured();
            this.syncWeaponLivingMotions(true);
        }
    }

    @Override
    protected void setOffhandDamage(InteractionHand hand,
                                    ItemStack mainhandItemStack,
                                    ItemStack offhandItemStack,
                                    boolean offhandValid,
                                    Collection<AttributeModifier> mainhandAttributes,
                                    Collection<AttributeModifier> offhandAttributes) {
        if (hand == InteractionHand.OFF_HAND) {
            this.offhandSwapActive = true;
            this.expectedSwappedMainhand = offhandValid ? offhandItemStack.copy() : ItemStack.EMPTY;
            this.expectedSwappedOffhand = mainhandItemStack.copy();
        }

        super.setOffhandDamage(hand, mainhandItemStack, offhandItemStack, offhandValid, mainhandAttributes, offhandAttributes);
    }

    @Override
    protected void recoverMainhandDamage(InteractionHand hand,
                                         ItemStack mainhandItemStack,
                                         ItemStack offhandItemStack,
                                         Collection<AttributeModifier> mainhandAttributes,
                                         Collection<AttributeModifier> offhandAttributes) {
        if (hand == InteractionHand.MAIN_HAND) {
            return;
        }

        HeroEntity hero = this.getOriginal();
        try {
            boolean canRestoreHands = hero != null
                    && this.offhandSwapActive
                    && ItemStack.matches(hero.getMainHandItem(), this.expectedSwappedMainhand)
                    && ItemStack.matches(hero.getOffhandItem(), this.expectedSwappedOffhand);

            if (canRestoreHands) {
                hero.setItemInHand(InteractionHand.MAIN_HAND, mainhandItemStack);
                hero.setItemInHand(InteractionHand.OFF_HAND, offhandItemStack);
            }

            AttributeInstance damageAttributeInstance = this.original.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttributeInstance != null) {
                offhandAttributes.forEach(damageAttributeInstance::removeModifier);
                mainhandAttributes.forEach(damageAttributeInstance::addTransientModifier);
            }
        } finally {
            this.offhandSwapActive = false;
            this.expectedSwappedMainhand = ItemStack.EMPTY;
            this.expectedSwappedOffhand = ItemStack.EMPTY;
        }
    }

    @Override
    public void preTickClient() {
        HeroEpicFightWeaponProfiles.bootstrap();
        super.preTickClient();
        this.syncWeaponLivingMotions(false);
    }

    @Override
    protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        HeroEntity hero = this.getOriginal();
        ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
        if (HeroEpicFightWeaponProfiles.isRangedLoadout(stack)) {
            return null;
        }

        CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(this.getOriginal());
        CombatBehaviors.Builder<HumanoidMobPatch<?>> playerLikeBuilder = this.getPlayerLikeAttackMotionBuilder(capability);
        if (playerLikeBuilder != null) {
            return playerLikeBuilder;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> nativeBuilder = this.getWeaponMotionBuilder(capability, stack);
        if (nativeBuilder != null) {
            return nativeBuilder;
        }

        return super.getHoldingItemWeaponMotionBuilder();
    }

    @Override
    public void setAIAsInfantry(boolean holdingRanedWeapon) {
        HeroEntity hero = this.getOriginal();
        if (hero == null) {
            return;
        }
        if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
            return;
        }
        Set<Goal> toRemove = new HashSet<>();
        this.selectGoalToRemove(toRemove);
        toRemove.forEach(hero.goalSelector::removeGoal);
        this.removeHeroCombatGoals();

        if (holdingRanedWeapon) {
            this.heroAttackGoal = new HeroEpicFightRangedAttackGoal(this, hero, 1.0D, 14.0F);
            hero.goalSelector.addGoal(0, this.heroAttackGoal);
            return;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = this.getHoldingItemWeaponMotionBuilder();
        if (builder != null) {
            double attackRadius = this.getPlayerLikeChaseRadius(HeroEpicFightWeaponProfiles.resolveCapability(hero));
            this.heroAttackGoal = new AnimatedAttackGoal<>(this, builder.build(this));
            this.heroChasingGoal = attackRadius > 0.0D
                    ? new HeroEpicFightChasingGoal(this, hero, MELEE_CHASE_SPEED, attackRadius)
                    : new HeroEpicFightChasingGoal(this, hero, MELEE_CHASE_SPEED);
            hero.goalSelector.addGoal(0, this.heroAttackGoal);
            hero.goalSelector.addGoal(1, this.heroChasingGoal);
        }
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        HeroEntity hero = this.getOriginal();
        if (hero == null) {
            return;
        }

        boolean rangedLoadout = this.isRangedWeaponEquipped();

        if (hero.isCastingThunder()) {
            this.currentLivingMotion = LivingMotions.SPELLCAST;
            this.currentCompositeMotion = this.resolveCompositeMotion(hero, this.currentLivingMotion);
            return;
        }

        if (hero.isFloating() && !hero.isBattleModeActive()) {
            this.currentLivingMotion = hero.getDeltaMovement().lengthSqr() > 0.01D ? LivingMotions.FLY : LivingMotions.FLOAT;
            this.currentCompositeMotion = this.resolveCompositeMotion(hero, this.currentLivingMotion);
            return;
        }

        if (hero.isBattleModeActive()) {
            if (rangedLoadout) {
                this.commonAggressiveRangedMobUpdateMotion(false);
                return;
            }

            this.commonAggressiveMobUpdateMotion(false);
        } else {
            this.commonMobUpdateMotion(considerInaction);
        }

        this.currentCompositeMotion = this.resolveCompositeMotion(hero, this.currentLivingMotion);
    }

    private void syncWeaponLivingMotions(boolean force) {
        HeroEntity hero = this.getOriginal();
        Animator animator = this.getAnimator();
        if (animator == null || !this.isEquipmentReady(hero)) {
            return;
        }

        String currentProfileKey = this.getCurrentWeaponProfileKey(hero);
        boolean battleMode = hero.isBattleModeActive();
        if (!force && currentProfileKey.equals(this.lastWeaponProfileKey)) {
            return;
        }

        Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations = new LinkedHashMap<>();
        this.applyBaseLivingAnimations(livingAnimations, battleMode);

        if (battleMode) {
            CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
            if (capability != null && !capability.isEmpty()) {
                livingAnimations.putAll(capability.getLivingMotionModifier(this, InteractionHand.MAIN_HAND));
                this.applyWeaponCategoryLivingMotions(livingAnimations, capability);
            }
            this.applyRunChaseLivingMotion(livingAnimations);
        }

        animator.resetLivingAnimations();
        livingAnimations.forEach(animator::addLivingAnimation);

        if (animator instanceof ClientAnimator clientAnimator) {
            clientAnimator.setCurrentMotionsAsDefault();
        }

        if (!hero.level().isClientSide) {
            SPChangeLivingMotion packet = new SPChangeLivingMotion(hero.getId());
            packet.putEntries(livingAnimations.entrySet());
            EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packet, hero);
        }

        this.lastWeaponProfileKey = currentProfileKey;
    }

    private void applyBaseLivingAnimations(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations, boolean battleMode) {
        livingAnimations.put(LivingMotions.IDLE, Animations.BIPED_IDLE);
        livingAnimations.put(LivingMotions.WALK, Animations.BIPED_WALK);
        if (battleMode) {
            livingAnimations.put(LivingMotions.CHASE, Animations.BIPED_RUN);
            livingAnimations.put(LivingMotions.RUN, Animations.BIPED_RUN);
        }
        livingAnimations.put(LivingMotions.FLOAT, Animations.BIPED_FLOAT);
        livingAnimations.put(LivingMotions.FLY, Animations.BIPED_FLYING);
        livingAnimations.put(LivingMotions.FALL, Animations.BIPED_FALL);
        livingAnimations.put(LivingMotions.MOUNT, Animations.BIPED_MOUNT);
        livingAnimations.put(LivingMotions.DEATH, Animations.BIPED_DEATH);
        livingAnimations.put(LivingMotions.SPELLCAST, Animations.EVOKER_CAST_SPELL);
        livingAnimations.put(LivingMotions.DIGGING, Animations.BIPED_DIG);
        livingAnimations.put(LivingMotions.AIM, Animations.BIPED_BOW_AIM);
        livingAnimations.put(LivingMotions.SHOT, Animations.BIPED_BOW_SHOT);
        livingAnimations.put(LivingMotions.RELOAD, Animations.BIPED_CROSSBOW_RELOAD);
        livingAnimations.put(LivingMotions.BLOCK, Animations.BIPED_BLOCK);
    }

    private LivingMotion resolveCompositeMotion(HeroEntity hero, LivingMotion baseMotion) {
        if (hero == null) {
            return baseMotion;
        }

        CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
        if (capability != null && !capability.isEmpty()) {
            LivingMotion customLivingMotion = capability.getLivingMotion(this, InteractionHand.MAIN_HAND);
            if (customLivingMotion != null) {
                return customLivingMotion;
            }
        }

        if (hero.isBattleHoldAction()) {
            return this.getHoldCompositeMotion(hero);
        }

        if (hero.isBattleReleaseAction()) {
            return this.getReleaseCompositeMotion(hero);
        }

        if (hero.isBattleTapAction()) {
            return this.getTapCompositeMotion(hero, baseMotion);
        }

        return baseMotion;
    }

    private LivingMotion getTapCompositeMotion(HeroEntity hero, LivingMotion baseMotion) {
        ItemStack stack = hero.getMainHandItem();
        if (stack.getItem() instanceof PoemOfTheEndItem) {
            return baseMotion;
        }

        return baseMotion;
    }

    private LivingMotion getHoldCompositeMotion(HeroEntity hero) {
        ItemStack stack = hero.getMainHandItem();
        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            return switch (poem.getMode(stack)) {
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> LivingMotions.DIGGING;
                case PoemOfTheEndItem.MODE_REALM_BREAKER,
                     PoemOfTheEndItem.MODE_THUNDER_CALL,
                     PoemOfTheEndItem.MODE_NORMAL -> LivingMotions.AIM;
                default -> LivingMotions.AIM;
            };
        }

        return LivingMotions.AIM;
    }

    private LivingMotion getReleaseCompositeMotion(HeroEntity hero) {
        ItemStack stack = hero.getMainHandItem();
        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            return switch (poem.getMode(stack)) {
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> LivingMotions.DIGGING;
                case PoemOfTheEndItem.MODE_REALM_BREAKER,
                     PoemOfTheEndItem.MODE_THUNDER_CALL,
                     PoemOfTheEndItem.MODE_NORMAL -> LivingMotions.SHOT;
                default -> LivingMotions.SHOT;
            };
        }

        return LivingMotions.SHOT;
    }

    private void applyWeaponCategoryLivingMotions(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations, CapabilityItem capability) {
        if (this.weaponLivingMotions == null || capability == null || capability.isEmpty()) {
            return;
        }

        Map<Style, Set<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>>> motionsByStyle = this.weaponLivingMotions.get(capability.getWeaponCategory());
        if (motionsByStyle == null) {
            return;
        }

        Style style = capability.getStyle(this);
        Set<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>> styleMotions = motionsByStyle.get(style);
        if (styleMotions == null) {
            styleMotions = motionsByStyle.get(CapabilityItem.Styles.COMMON);
        }
        if (styleMotions == null) {
            return;
        }

        for (Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motionPair : styleMotions) {
            livingAnimations.put(motionPair.getFirst(), motionPair.getSecond());
        }
    }

    private void applyRunChaseLivingMotion(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations) {
        AssetAccessor<? extends StaticAnimation> runAnimation = livingAnimations.get(LivingMotions.RUN);
        livingAnimations.put(LivingMotions.CHASE, runAnimation != null ? runAnimation : Animations.BIPED_RUN);
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> getPlayerLikeAttackMotionBuilder(CapabilityItem capability) {
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        if (profile == null || profile.comboAnimations().isEmpty()) {
            return null;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(140.0F)
                .canBeInterrupted(false)
                .looping(true);
        comboSeries.nextBehavior(this.createTrackedComboAttackBehavior(profile)
                .custom(mobPatch -> this.canStartPlayerLikeGroundCombo(mobPatch, profile)));
        builder.newBehaviorSeries(comboSeries);

        if (!profile.dashAnimations().isEmpty()) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> dashSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                    .weight(65.0F)
                    .cooldown(24)
                    .canBeInterrupted(false)
                    .looping(false);
            for (int i = 0; i < profile.dashAnimations().size(); i++) {
                AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = profile.dashAnimations().get(i);
                HeroCombatPlanner.ActionProfile actionProfile = profile.dashActionProfiles().get(i);
                dashSeries.nextBehavior(this.createTrackedAttackBehavior(animation, actionProfile, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep)
                        .custom(mobPatch -> this.canStartPlayerLikeDashAttack(mobPatch, profile)));
            }
            builder.newBehaviorSeries(dashSeries);
        }

        if (!profile.airAnimations().isEmpty()) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> airSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                    .weight(220.0F)
                    .cooldown(16)
                    .canBeInterrupted(false)
                    .looping(false);
            for (int i = 0; i < profile.airAnimations().size(); i++) {
                AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = profile.airAnimations().get(i);
                HeroCombatPlanner.ActionProfile actionProfile = profile.airActionProfiles().get(i);
                airSeries.nextBehavior(this.createTrackedAttackBehavior(animation, actionProfile, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep)
                        .custom(this::canStartPlayerLikeAirAttack));
            }
            builder.newBehaviorSeries(airSeries);
        }

        return builder;
    }

    private PlayerLikeAttackProfile resolvePlayerLikeAttackProfile(CapabilityItem capability) {
        PlayerLikeAttackProfile moveSetProfile = this.resolveMoveSetAttackProfile(capability);
        if (moveSetProfile != null) {
            this.applyDynamicActionProfiles(moveSetProfile);
            return moveSetProfile;
        }

        HeroEntity hero = this.getOriginal();
        ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
        if (!stack.isEmpty() && stack.getItem() instanceof PoemOfTheEndItem) {
            PlayerLikeAttackProfile profile = this.createPlayerLikeAttackProfile(
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3),
                    List.of(Animations.SWORD_DASH),
                    List.of(Animations.SWORD_AIR_SLASH),
                    2.5D,
                    1.6D,
                    3.75D,
                    3.0D
            );
            this.applyDynamicActionProfiles(profile);
            return profile;
        }

        this.clearDynamicActionProfiles();
        return null;
    }

    private PlayerLikeAttackProfile resolveMoveSetAttackProfile(CapabilityItem capability) {
        if (!(capability instanceof WeaponCapability weaponCapability)) {
            return null;
        }

        Object moveSet = this.resolveCurrentMoveSet(weaponCapability);
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> moveSetAnimations = getComboAttackAnimations(moveSet);
        if (moveSetAnimations.isEmpty()) {
            return null;
        }

        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> usableAnimations = new ArrayList<>();
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations = new ArrayList<>();
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations = new ArrayList<>();
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations = new ArrayList<>();

        for (AnimationManager.AnimationAccessor<? extends AttackAnimation> animation : moveSetAnimations) {
            if (animation == null) {
                continue;
            }

            usableAnimations.add(animation);
            if (this.isDashAttackAnimation(animation)) {
                dashAnimations.add(animation);
            } else if (this.isAirAttackAnimation(animation)) {
                airAnimations.add(animation);
            } else {
                comboAnimations.add(animation);
            }
        }

        if (comboAnimations.isEmpty() && usableAnimations.size() == 1) {
            comboAnimations.add(usableAnimations.getFirst());
        } else if (comboAnimations.isEmpty() && !usableAnimations.isEmpty()) {
            comboAnimations.addAll(usableAnimations);
        }

        if (comboAnimations.isEmpty()) {
            return null;
        }

        double reach = Math.max(0.0D, weaponCapability.getReach());
        double comboMaxDistance = 2.4D + reach;
        double dashMinDistance = 1.35D + Math.min(reach, 1.0D) * 0.25D;
        double dashMaxDistance = comboMaxDistance + 1.25D;
        double airMaxDistance = comboMaxDistance + 0.5D;

        return this.createPlayerLikeAttackProfile(
                comboAnimations,
                dashAnimations,
                airAnimations,
                comboMaxDistance,
                dashMinDistance,
                dashMaxDistance,
                airMaxDistance
        );
    }

    private Object resolveCurrentMoveSet(WeaponCapability weaponCapability) {
        Object currentSet = invokeCompatibleMethod(weaponCapability, "getCurrentSet", this);
        if (currentSet != null) {
            return currentSet;
        }

        Object style = invokeCompatibleMethod(weaponCapability, "getStyle", this);
        if (style == null) {
            return null;
        }

        return invokeCompatibleMethod(weaponCapability, "getMovesetForStyle", style);
    }

    private static Object invokeCompatibleMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }

        Method method = findCompatibleMethod(target.getClass(), methodName, args);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findCompatibleMethod(Class<?> ownerType, String methodName, Object... args) {
        String cacheKey = buildCompatMethodCacheKey(ownerType, methodName, args);
        Optional<Method> cached = COMPAT_METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }

        for (Method method : ownerType.getMethods()) {
            if (!method.getName().equals(methodName) || !parametersMatch(method.getParameterTypes(), args)) {
                continue;
            }
            COMPAT_METHOD_CACHE.put(cacheKey, Optional.of(method));
            return method;
        }

        COMPAT_METHOD_CACHE.put(cacheKey, Optional.empty());
        return null;
    }

    private static String buildCompatMethodCacheKey(Class<?> ownerType, String methodName, Object... args) {
        StringBuilder builder = new StringBuilder(ownerType.getName()).append('#').append(methodName).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            Object arg = args[i];
            builder.append(arg == null ? "null" : arg.getClass().getName());
        }
        return builder.append(')').toString();
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }

            Class<?> parameterType = parameterTypes[i];
            if (parameterType.isPrimitive()) {
                parameterType = wrapPrimitiveType(parameterType);
            }
            if (!parameterType.isInstance(arg)) {
                return false;
            }
        }

        return true;
    }

    private static Class<?> wrapPrimitiveType(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return Boolean.class;
        }
        if (primitiveType == byte.class) {
            return Byte.class;
        }
        if (primitiveType == char.class) {
            return Character.class;
        }
        if (primitiveType == short.class) {
            return Short.class;
        }
        if (primitiveType == int.class) {
            return Integer.class;
        }
        if (primitiveType == long.class) {
            return Long.class;
        }
        if (primitiveType == float.class) {
            return Float.class;
        }
        if (primitiveType == double.class) {
            return Double.class;
        }
        return primitiveType;
    }

    @SuppressWarnings("unchecked")
    private static List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> getComboAttackAnimations(Object moveSet) {
        if (moveSet == null) {
            return List.of();
        }

        try {
            Object result = moveSet.getClass().getMethod("getComboAttackAnimations").invoke(moveSet);
            if (result instanceof List<?> animations) {
                return (List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>) animations;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        return List.of();
    }

    private boolean isDashAttackAnimation(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        String animationKey = this.getAnimationPath(animation);
        return animationKey.contains("dash");
    }

    private boolean isAirAttackAnimation(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        String animationKey = this.getAnimationPath(animation);
        return animationKey.contains("air");
    }

    private String getAnimationPath(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        if (animation == null || animation.registryName() == null) {
            return "";
        }

        return animation.registryName().getPath().toLowerCase(Locale.ROOT);
    }

    private double getPlayerLikeChaseRadius(CapabilityItem capability) {
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        if (profile == null) {
            return 0.0D;
        }

        return this.getPlayerLikeComboContinueDistance(profile);
    }

    private double getPlayerLikeComboContinueDistance(PlayerLikeAttackProfile profile) {
        return profile.comboMaxDistance() + 0.15D;
    }

    private CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createTrackedAttackBehavior(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation,
                                                                                               HeroCombatPlanner.ActionProfile actionProfile,
                                                                                               ToIntFunction<HeroEntity> actionStateResolver,
                                                                                               Consumer<HeroEntity> comboStepUpdater) {
        return CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder().behavior(mobPatch -> {
            if (!this.isPlayableAttackAnimation(animation)) {
                return;
            }

            HeroEntity hero = this.getTrackedHero(mobPatch);
            if (hero != null && actionStateResolver != null) {
                int actionState = actionStateResolver.applyAsInt(hero);
                if (actionState >= 0) {
                    hero.beginBattleAction(actionState, actionProfile);
                }
            }
            if (hero != null) {
                if (comboStepUpdater != null) {
                    comboStepUpdater.accept(hero);
                }
                hero.swing(InteractionHand.MAIN_HAND);
            }

            mobPatch.playAnimationSynchronized(animation, 0.0F);
        });
    }

    private CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createTrackedComboAttackBehavior(PlayerLikeAttackProfile profile) {
        return CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder().behavior(mobPatch -> {
            if (profile == null || profile.comboAnimations().isEmpty()) {
                return;
            }

            HeroEntity hero = this.getTrackedHero(mobPatch);
            int comboIndex = this.getExpectedPlayerLikeComboIndex(hero, profile.comboAnimations().size());
            AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = profile.comboAnimations().get(comboIndex);
            HeroCombatPlanner.ActionProfile actionProfile = profile.comboActionProfiles().get(comboIndex);
            if (!this.isPlayableAttackAnimation(animation)) {
                return;
            }

            if (hero != null) {
                hero.beginBattleAction(resolveComboActionState(comboIndex), actionProfile);
                this.advancePlayerLikeComboStep(hero, profile.comboAnimations().size(), comboIndex);
                hero.swing(InteractionHand.MAIN_HAND);
            }

            mobPatch.playAnimationSynchronized(animation, 0.0F);
        });
    }

    private boolean isPlayableAttackAnimation(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private boolean canStartPlayerLikeGroundCombo(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile) {
        HeroEntity hero = this.getTrackedHero(mobPatch);
        if (hero == null) {
            return true;
        }

        LivingEntity target = this.getTrackedTarget(hero);
        if (target == null) {
            return false;
        }

        HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(profile);

        return this.isPlayerLikeGroundState(hero)
                && (HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, this.getPlayerLikeComboContinueDistance(profile), 4)
                || this.isTargetWithinComboFlowDistance(hero, target, profile, 0.65D))
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO);
    }


    private boolean canStartPlayerLikeDashAttack(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile) {
        HeroEntity hero = this.getTrackedHero(mobPatch);
        LivingEntity target = this.getTrackedTarget(hero);
        HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(profile);
        return hero != null
                && target != null
                && this.isPlayerLikeGroundState(hero)
                && this.isDashLikeMovement(hero, target, profile)
                && !HeroCombatPlanner.isAttackReplayLocked(hero, 8)
                && HeroCombatPlanner.isDashOpportunity(hero, target, profile.dashMinDistance(), profile.dashMaxDistance(), 6)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.DASH);
    }

    private boolean canStartPlayerLikeAirAttack(HumanoidMobPatch<?> mobPatch) {
        HeroEntity hero = this.getTrackedHero(mobPatch);
        if (hero == null || !hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }

        LivingEntity target = this.getTrackedTarget(hero);
        HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(HeroEpicFightWeaponProfiles.resolveCapability(hero));
        return !hero.onGround()
                && !hero.isFloating()
                && hero.getDeltaMovement().y < 0.08D
                && HeroCombatPlanner.canStartAirAttack(hero, target, this.resolveAirAttackMaxDistance(hero), 5)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR);
    }

    private boolean isPlayerLikeGroundState(HeroEntity hero) {
        return hero.isBattleModeActive()
                && hero.onGround()
                && !hero.isFloating()
                && !hero.isBattleHoldAction()
                && !hero.isBattleReleaseAction();
    }

    boolean shouldStopChasingForMelee(LivingEntity target) {
        HeroEntity hero = this.getOriginal();
        if (hero == null || target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }

        CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        if (profile == null) {
            return hero.distanceToSqr(target) <= 4.0D;
        }

        return HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, this.getPlayerLikeComboContinueDistance(profile), 2)
                || this.isTargetWithinComboFlowDistance(hero, target, profile, 0.2D);
    }

    private boolean isDashLikeMovement(HeroEntity hero, LivingEntity target, PlayerLikeAttackProfile profile) {
        if (hero == null || target == null || profile.dashMaxDistance() <= 0.0D) {
            return false;
        }

        double horizontalDistanceSqr = hero.distanceToSqr(target.getX(), hero.getY(), target.getZ());
        double dashStartDistance = Math.max(profile.comboMaxDistance() + 0.45D, profile.dashMinDistance());
        return horizontalDistanceSqr >= dashStartDistance * dashStartDistance;
    }

    private boolean isTargetWithinComboFlowDistance(HeroEntity hero, LivingEntity target, PlayerLikeAttackProfile profile, double extraRange) {
        if (hero == null || target == null || profile == null) {
            return false;
        }

        double maxDistance = this.getPlayerLikeComboContinueDistance(profile) + Math.max(0.0D, extraRange);
        return hero.distanceToSqr(target) <= maxDistance * maxDistance;
    }

    private LivingEntity getTrackedTarget(HeroEntity hero) {
        if (hero == null) {
            return null;
        }

        LivingEntity target = hero.getTarget();
        return target != null && target.isAlive() && !target.isRemoved() ? target : null;
    }

    private double resolveAirAttackMaxDistance(HeroEntity hero) {
        CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        return profile != null ? profile.airMaxDistance() : 3.0D;
    }

    private HeroCombatPlanner.CombatTuning getPlayerLikeCombatTuning(PlayerLikeAttackProfile profile) {
        if (profile == null) {
            return HeroCombatPlanner.CombatTuning.comboOnly(0.0D);
        }

        return new HeroCombatPlanner.CombatTuning(
                this.getPlayerLikeComboContinueDistance(profile),
                profile.dashMinDistance(),
                profile.dashMaxDistance(),
                profile.airMaxDistance(),
                0.0D,
                0.0D,
                !profile.dashAnimations().isEmpty(),
                !profile.airAnimations().isEmpty(),
                false
        );
    }

    private HeroCombatPlanner.CombatTuning getPlayerLikeCombatTuning(CapabilityItem capability) {
        return this.getPlayerLikeCombatTuning(this.resolvePlayerLikeAttackProfile(capability));
    }

    private HeroEntity getTrackedHero(HumanoidMobPatch<?> mobPatch) {
        if (mobPatch instanceof HeroEpicFightPatch heroPatch) {
            return heroPatch.getOriginal();
        }

        return null;
    }

    private static int resolveNextTapActionState(HeroEntity hero) {
        if (hero == null) {
            return HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1;
        }

        return hero.getBattleActionSerial() % 2 == 0
                ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }

    private static int resolveComboActionState(int comboIndex) {
        return comboIndex % 2 == 0
                ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }

    private PlayerLikeAttackProfile createPlayerLikeAttackProfile(
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations,
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations,
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations,
            double comboMaxDistance,
            double dashMinDistance,
            double dashMaxDistance,
            double airMaxDistance
    ) {
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboList = List.copyOf(comboAnimations);
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashList = List.copyOf(dashAnimations);
        List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airList = List.copyOf(airAnimations);
        return new PlayerLikeAttackProfile(
                comboList,
                dashList,
                airList,
                this.createDynamicActionProfiles(comboList, HeroCombatPlanner.PlannedAction.LIGHT_COMBO, "combo", 4.0D),
                this.createDynamicActionProfiles(dashList, HeroCombatPlanner.PlannedAction.DASH, "dash", 8.0D),
                this.createDynamicActionProfiles(airList, HeroCombatPlanner.PlannedAction.AIR, "air", 7.0D),
                comboMaxDistance,
                dashMinDistance,
                dashMaxDistance,
                airMaxDistance
        );
    }

    private List<HeroCombatPlanner.ActionProfile> createDynamicActionProfiles(
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> animations,
            HeroCombatPlanner.PlannedAction plannedAction,
            String namePrefix,
            double baseStyleScore
    ) {
        List<HeroCombatPlanner.ActionProfile> profiles = new ArrayList<>();
        for (int i = 0; i < animations.size(); i++) {
            profiles.add(this.createDynamicActionProfile(animations.get(i), plannedAction, namePrefix + "_" + i, baseStyleScore));
        }
        return List.copyOf(profiles);
    }

    private HeroCombatPlanner.ActionProfile createDynamicActionProfile(
            AnimationManager.AnimationAccessor<? extends StaticAnimation> animation,
            HeroCombatPlanner.PlannedAction plannedAction,
            String name,
            double baseStyleScore
    ) {
        HeroCombatPlanner.ActionPhaseSpec phaseSpec = this.derivePhaseSpec(animation);
        int windupTicks = Math.max(1, phaseSpec.activeEndTick());
        double heroVelocityInfluence = switch (plannedAction) {
            case DASH -> 0.72D;
            case AIR -> 0.45D;
            case LIGHT_COMBO -> 0.50D;
            case SKILL -> 0.45D;
            case APPROACH, NONE -> 0.50D;
        };
        double tolerance = switch (plannedAction) {
            case DASH -> 0.65D;
            case AIR -> 0.60D;
            case LIGHT_COMBO -> 0.45D;
            case SKILL -> 0.75D;
            case APPROACH, NONE -> 0.45D;
        };
        int bufferTicks = Math.max(3, phaseSpec.recoveryEndTick() - phaseSpec.startupEndTick());
        double weightFromLength = Math.max(0.0D, this.secondsToTicks(this.getAnimationTotalTime(animation)) * 0.12D - 0.6D);
        return new HeroCombatPlanner.ActionProfile(name, plannedAction, windupTicks, heroVelocityInfluence, tolerance, baseStyleScore + weightFromLength, bufferTicks, phaseSpec);
    }

    private HeroCombatPlanner.ActionPhaseSpec derivePhaseSpec(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        int totalTicks = Math.max(2, this.secondsToTicks(this.getAnimationTotalTime(animation)));
        if (animation != null && !animation.isEmpty() && animation.get() instanceof AttackAnimation attackAnimation && attackAnimation.phases.length > 0) {
            AttackAnimation.Phase phase = attackAnimation.phases[0];
            int startupEndTick = this.secondsToTicks(Math.max(phase.preDelay, phase.antic));
            int activeEndTick = this.secondsToTicks(Math.max(phase.contact, phase.preDelay + EpicFightSharedConstants.A_TICK));
            float recoveryTime = phase.recovery;
            float endTime = phase.end == Float.MAX_VALUE ? attackAnimation.getTotalTime() : phase.end;
            int chainEndTick = this.secondsToTicks(Math.max(recoveryTime, phase.contact + EpicFightSharedConstants.A_TICK));
            int recoveryEndTick = Math.max(totalTicks, this.secondsToTicks(Math.max(endTime, recoveryTime + EpicFightSharedConstants.A_TICK)));
            return this.normalizePhaseSpec(startupEndTick, activeEndTick, chainEndTick, recoveryEndTick);
        }

        int startupEndTick = Math.max(1, Math.round(totalTicks * 0.30F));
        int activeEndTick = Math.max(startupEndTick + 1, Math.round(totalTicks * 0.50F));
        int chainEndTick = Math.max(activeEndTick + 1, Math.round(totalTicks * 0.78F));
        return this.normalizePhaseSpec(startupEndTick, activeEndTick, chainEndTick, totalTicks);
    }

    private HeroCombatPlanner.ActionPhaseSpec normalizePhaseSpec(int startupEndTick, int activeEndTick, int chainEndTick, int recoveryEndTick) {
        int startup = Math.max(1, startupEndTick);
        int active = Math.max(startup + 1, activeEndTick);
        int chain = Math.max(active + 1, chainEndTick);
        int recovery = Math.max(chain + 1, recoveryEndTick);
        return new HeroCombatPlanner.ActionPhaseSpec(startup, active, chain, recovery);
    }

    private float getAnimationTotalTime(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        if (animation == null || animation.isEmpty()) {
            return EpicFightSharedConstants.A_TICK * 8.0F;
        }
        return Math.max(EpicFightSharedConstants.A_TICK, animation.get().getTotalTime());
    }

    private int secondsToTicks(float seconds) {
        return Math.max(1, Math.round(seconds / EpicFightSharedConstants.A_TICK));
    }

    private void applyDynamicActionProfiles(PlayerLikeAttackProfile profile) {
        HeroEntity hero = this.getOriginal();
        if (hero == null || profile == null) {
            return;
        }
        hero.setBattleDynamicProfiles(
                profile.comboActionProfiles(),
                profile.dashActionProfiles().isEmpty() ? null : profile.dashActionProfiles().get(0),
                profile.airActionProfiles().isEmpty() ? null : profile.airActionProfiles().get(0)
        );
    }

    private void clearDynamicActionProfiles() {
        HeroEntity hero = this.getOriginal();
        if (hero == null) {
            return;
        }
        hero.setBattleDynamicProfiles(List.of(), null, null);
    }

    private int getExpectedPlayerLikeComboIndex(HeroEntity hero, int comboSize) {
        if (hero == null || comboSize <= 0) {
            return 0;
        }

        int step = hero.getBattleComboStep();
        if (step < 0) {
            return 0;
        }

        return step % comboSize;
    }

    private void advancePlayerLikeComboStep(HeroEntity hero, int comboSize, int executedIndex) {
        if (hero == null || comboSize <= 0) {
            return;
        }

        hero.setBattleComboStep((executedIndex + 1) % comboSize);
    }

    private void resetPlayerLikeComboStep(HeroEntity hero) {
        if (hero == null) {
            return;
        }

        hero.setBattleComboStep(0);
    }

    private record PlayerLikeAttackProfile(
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations,
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations,
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations,
            List<HeroCombatPlanner.ActionProfile> comboActionProfiles,
            List<HeroCombatPlanner.ActionProfile> dashActionProfiles,
            List<HeroCombatPlanner.ActionProfile> airActionProfiles,
            double comboMaxDistance,
            double dashMinDistance,
            double dashMaxDistance,
            double airMaxDistance
    ) {
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> getWeaponMotionBuilder(CapabilityItem capability, ItemStack stack) {
        if (capability == null || capability.isEmpty() || this.weaponAttackMotions == null) {
            return null;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = this.getWeaponMotionBuilder(capability.getWeaponCategory(), capability.getStyle(this));
        if (builder != null) {
            return builder;
        }

        return null;
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> getWeaponMotionBuilder(WeaponCategory category, Style style) {
        Map<Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>> motionsByStyle = this.weaponAttackMotions.get(category);
        if (motionsByStyle == null) {
            return null;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = motionsByStyle.get(style);
        if (builder == null) {
            builder = motionsByStyle.get(CapabilityItem.Styles.COMMON);
        }
        if (builder == null && !motionsByStyle.isEmpty()) {
            builder = motionsByStyle.values().iterator().next();
        }
        return builder;
    }


    private String getCurrentWeaponProfileKey(HeroEntity hero) {
        if (!this.isEquipmentReady(hero)) {
            return "missing";
        }
        if (!hero.isBattleModeActive()) {
            return "false|out_of_battle";
        }
        return "true|" + HeroEpicFightWeaponProfiles.getProfileKey(hero, this);
    }

    private boolean isEquipmentReady(HeroEntity hero) {
        if (hero == null || !hero.isAddedToLevel()) {
            return false;
        }

        try {
            hero.getMainHandItem();
            return true;
        } catch (NullPointerException exception) {
            return false;
        }
    }

    private void ensureInfantryAiConfigured() {
        HeroEntity hero = this.getOriginal();
        if (hero == null || !hero.isAddedToLevel()) {
            return;
        }
        if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
            return;
        }
        if (this.infantryAiConfigured) {
            return;
        }

        this.setAIAsInfantry(this.isRangedWeaponEquipped());
        this.infantryAiConfigured = true;
    }

    boolean isRangedWeaponEquipped() {
        return HeroEpicFightWeaponProfiles.isRangedLoadout(this.getOriginal());
    }

    boolean fireCrossbow(LivingEntity target, ItemStack stack) {
        HeroEntity hero = this.getOriginal();
        return HeroCombatWeaponHelper.fireCrossbow(hero, target, stack);
    }

    private void removeHeroCombatGoals() {
        HeroEntity hero = this.getOriginal();
        if (hero == null) {
            return;
        }
        if (this.heroAttackGoal != null) {
            hero.goalSelector.removeGoal(this.heroAttackGoal);
            this.heroAttackGoal = null;
        }
        if (this.heroChasingGoal != null) {
            hero.goalSelector.removeGoal(this.heroChasingGoal);
            this.heroChasingGoal = null;
        }
    }
}


