package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.datafixers.util.Pair;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPursuit;
import com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroEpicFightChaseGoal;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

public class HeroEpicFightPatch extends HumanoidMobPatch<HeroEntity> {
    private boolean infantryAiConfigured;
    private String lastWeaponProfileKey = "";
    private Goal heroAttackGoal;
    private Goal heroChasingGoal;
    /** 追击/近战的攻击半径，供飞行追击落地收尾判断使用 */
    private double currentChaseAttackRadius;

    public HeroEpicFightPatch() {
        super(Factions.NEUTRAL);
    }

    @Override
    public boolean overrideRender() {
        HeroEntity hero = this.getOriginal();
        if (hero == null
                || hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || !hero.isBattleModeActive()) {
            return false;
        }

        return super.overrideRender();
    }

    @Override
    public void onConstructed(HeroEntity hero) {
        super.onConstructed(hero);
    }

    @Override
    public void tick(LivingEvent.LivingTickEvent event) {
        HeroEntity hero = this.getOriginal();
        if (hero != null) {
            this.sanitizeAnimatorBeforeTick(hero);
        }

        super.tick(event);
    }

    @Override
    protected void initAI() {
        HeroEntity hero = this.getOriginal();
        if (hero == null || hero.isRemoved() || !hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
            return;
        }

        super.initAI();
    }

    @Override
    public void onJoinWorld(HeroEntity hero, net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        super.onJoinWorld(hero, event);
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
    protected void serverTick(LivingEvent.LivingTickEvent event) {
        HeroEpicFightWeaponProfiles.bootstrap();
        HeroEntity hero = this.getOriginal();
        String currentProfileKey = this.getCurrentWeaponProfileKey(hero);
        boolean weaponProfileChanged = !currentProfileKey.equals(this.lastWeaponProfileKey);

        this.ensureInfantryAiConfigured();
        this.syncWeaponLivingMotions(weaponProfileChanged);
        super.serverTick(event);
        HeroCombatPlanner.tickEpicFightActionClock(hero);
        HeroNightfallMovesets.tickSkillEffects(this, hero);
        // 飞行追击收尾：目标已消失 / 已能贴地命中时，让悬停的 Hero 落回地面，避免永久悬空。
        // 仅浮空态才走反射查询 isHeroMidAttack，避免每 tick 无谓开销。
        if (hero != null && hero.isFloating()) {
            HeroCombatPursuit.landHeroIfCombatIdle(hero, hero.getTarget(), this.currentChaseAttackRadius,
                    HeroEpicFightCompat.isHeroMidAttack(hero));
        }
        this.logTickState("HeroEpicFightPatch.serverTick");
    }

    @Override
    public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
        HeroEntity hero = this.getOriginal();
        if (hero == null || hero.isRemoved()) {
            return;
        }

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.updateHeldItem.begin", "hand=" + hand
                + ",from=" + HeroEpicFightDebugLog.itemKey(from)
                + ",to=" + HeroEpicFightDebugLog.itemKey(to)
                + ",state=" + this.debugPatchState(hero));

        this.infantryAiConfigured = false;

        // 主手武器变化时强制清空上一把武器的战斗状态机（尤其赤月的 HEAVY_HOLD/RELEASE 两段态、
        // 缓冲动作与连段计数），避免上一把武器的姿态泄漏到新武器上引发异常行为/卡顿
        if (hand == InteractionHand.MAIN_HAND && !ItemStack.matches(from, to)) {
            hero.resetBattleCombatState();
        }

        if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.resetActionAnimator();
            this.syncWeaponLivingMotions(true);
            HeroEpicFightDebugLog.transition(hero, "HeroEpicFightPatch.updateHeldItem.outOfBattle",
                    HeroEpicFightDebugLog.itemKey(from) + "->" + HeroEpicFightDebugLog.itemKey(to) + "|" + this.debugPatchTransitionState(hero),
                    "from=" + HeroEpicFightDebugLog.itemKey(from) + ",to=" + HeroEpicFightDebugLog.itemKey(to)
                            + ",state=" + this.debugPatchState(hero));
            return;
        }

        super.updateHeldItem(fromCap, toCap, from, to, hand);

        if (!hero.level().isClientSide) {
            this.ensureInfantryAiConfigured();
            this.syncWeaponLivingMotions(true);
            HeroEpicFightDebugLog.transition(hero, "HeroEpicFightPatch.updateHeldItem.server",
                    HeroEpicFightDebugLog.itemKey(from) + "->" + HeroEpicFightDebugLog.itemKey(to) + "|" + this.debugPatchTransitionState(hero),
                    "from=" + HeroEpicFightDebugLog.itemKey(from) + ",to=" + HeroEpicFightDebugLog.itemKey(to)
                            + ",state=" + this.debugPatchState(hero));
        }
    }

    @Override
    protected void clientTick(LivingEvent.LivingTickEvent event) {
        HeroEpicFightWeaponProfiles.bootstrap();
        super.clientTick(event);
        this.syncWeaponLivingMotions(false);
        this.logTickState("HeroEpicFightPatch.clientTick");
    }

    private void sanitizeAnimatorBeforeTick(HeroEntity hero) {
        Animator animator = this.getAnimator();
        if (animator == null || hero.isBattleModeActive()) {
            return;
        }

        if (!this.shouldSanitizeOutOfBattleAnimator(hero, animator)) {
            return;
        }

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.sanitizeAnimatorBeforeTick", "state=" + this.debugPatchState(hero));
        this.resetActionAnimator();
    }

    private boolean shouldSanitizeOutOfBattleAnimator(HeroEntity hero, Animator animator) {
        if (hero == null || animator == null) {
            return false;
        }

        if (hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_IDLE
                || hero.getBattleActionTicks() != 0
                || hero.getBattleComboStep() != 0) {
            return true;
        }

        String animationId = this.getPrimaryAnimationId(animator);
        if (animationId.isEmpty() || "anim=empty".equals(animationId)) {
            return false;
        }

        if (this.isSafeOutOfBattleAnimation(animationId)) {
            return false;
        }

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.sanitizeAnimatorBeforeTick.needsReset",
                "animId=" + animationId + ",state=" + this.debugPatchState(hero));
        return true;
    }

    private String getPrimaryAnimationId(Animator animator) {
        if (animator == null) {
            return "";
        }

        try {
            AnimationPlayer player = animator.getPlayerFor(null);
            if (player == null || player.isEmpty()) {
                return "anim=empty";
            }

            ResourceLocation animationId = player.getRealAnimation().get().getRegistryName();
            return animationId != null ? animationId.toString() : "unregistered";
        } catch (RuntimeException exception) {
            return "anim_error=" + exception.getClass().getSimpleName();
        }
    }

    private boolean isSafeOutOfBattleAnimation(String animationId) {
        return animationId.startsWith("epicfight:biped/living/")
                || animationId.startsWith("epicfight:biped/combat/")
                || animationId.startsWith("epicfight:entity/")
                || animationId.startsWith("epicfight:monster/")
                || "epicfight:biped/skill/air_burst".equals(animationId)
                || "unregistered".equals(animationId);
    }

    private void resetActionAnimator() {
        Animator animator = this.getAnimator();
        if (animator == null) {
            return;
        }

        HeroEntity hero = this.getOriginal();
        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.resetActionAnimator.begin", "animBefore=" + HeroEpicFightDebugLog.animatorKey(animator) + ",state=" + this.debugPatchState(hero));

        this.currentLivingMotion = LivingMotions.IDLE;
        this.currentCompositeMotion = LivingMotions.IDLE;

        if (animator instanceof ClientAnimator clientAnimator) {
            clientAnimator.iterAllLayers(layer -> layer.animationPlayer.setPlayAnimation(Animations.EMPTY_ANIMATION));
            clientAnimator.offAllLayers();
            clientAnimator.playAnimationInstantly(Animations.BIPED_IDLE);
            HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.resetActionAnimator.clientDone", "animAfter=" + HeroEpicFightDebugLog.animatorKey(animator) + ",state=" + this.debugPatchState(hero));
            return;
        }

        AnimationPlayer animationPlayer = animator.getPlayerFor(null);
        if (animationPlayer != null && !animationPlayer.isEmpty()) {
            animationPlayer.setPlayAnimation(Animations.EMPTY_ANIMATION);
        }
        animator.setSoftPause(true);
        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.resetActionAnimator.serverDone", "animAfter=" + HeroEpicFightDebugLog.animatorKey(animator) + ",state=" + this.debugPatchState(hero));
    }


    @Override
    protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        HeroEntity hero = this.getOriginal();
        ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
        if (HeroEpicFightWeaponProfiles.isRangedLoadout(stack)) {
            return null;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> nightfallBuilder = HeroNightfallMovesets.buildCombatBehaviors(this, stack);
        if (nightfallBuilder != null) {
            return nightfallBuilder;
        }

        CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(this.getOriginal());
        CombatBehaviors.Builder<HumanoidMobPatch<?>> playerLikeBuilder = this.getPlayerLikeAttackMotionBuilder(capability);
        if (playerLikeBuilder != null) {
            return playerLikeBuilder;
        }

        return null;
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
            ItemStack stack = hero.getMainHandItem();
            double attackRadius = HeroNightfallMovesets.getAttackRadius(stack, 0.0D);
            if (attackRadius <= 0.0D) {
                attackRadius = this.getPlayerLikeChaseRadius(HeroEpicFightWeaponProfiles.resolveCapability(hero));
            }
            this.currentChaseAttackRadius = attackRadius;
            this.heroAttackGoal = new AnimatedAttackGoal<>(this, builder.build(this));
            this.heroChasingGoal = new HeroEpicFightChaseGoal(hero, 1.0D, attackRadius);
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

        // 战斗模式新增了飞行追击（HeroCombatPursuit），浮空时同样走 FLY/FLOAT 动作，
        // 避免 Hero 在空中仍摆出地面奔跑姿态。
        if (hero.isFloating()) {
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

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.syncWeaponLivingMotions.begin", "force=" + force + ",battle=" + battleMode + ",profile=" + currentProfileKey + ",lastProfile=" + this.lastWeaponProfileKey + ",anim=" + HeroEpicFightDebugLog.animatorKey(animator));

        Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations = new LinkedHashMap<>();
        this.applyBaseLivingAnimations(livingAnimations, battleMode);

        if (battleMode) {
            HeroNightfallMovesets.applyLivingAnimations(hero.getMainHandItem(), livingAnimations);
            CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
            if (capability != null && !capability.isEmpty()) {
                livingAnimations.putAll(capability.getLivingMotionModifier(this, InteractionHand.MAIN_HAND));
                this.applyWeaponCategoryLivingMotions(livingAnimations, capability);
                HeroNightfallMovesets.applyLivingAnimations(hero.getMainHandItem(), livingAnimations);
            }
        }

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.syncWeaponLivingMotions.mapReady", "count=" + livingAnimations.size() + ",motions=" + livingAnimations.keySet());

        animator.resetLivingAnimations();
        livingAnimations.forEach(animator::addLivingAnimation);
        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.syncWeaponLivingMotions.animatorApplied", "anim=" + HeroEpicFightDebugLog.animatorKey(animator) + ",state=" + this.debugPatchState(hero));

        if (animator instanceof ClientAnimator clientAnimator) {
            clientAnimator.setCurrentMotionsAsDefault();
        }

        if (!hero.level().isClientSide) {
            SPChangeLivingMotion packet = new SPChangeLivingMotion(hero.getId());
            packet.putEntries(livingAnimations.entrySet());
            EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packet, hero);
        }

        this.lastWeaponProfileKey = currentProfileKey;
        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.syncWeaponLivingMotions.end", "lastProfile=" + this.lastWeaponProfileKey + ",state=" + this.debugPatchState(hero));
    }

    private void applyBaseLivingAnimations(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations, boolean battleMode) {
        livingAnimations.put(LivingMotions.IDLE, Animations.BIPED_IDLE);
        livingAnimations.put(LivingMotions.WALK, Animations.BIPED_WALK);
        if (battleMode) {
            livingAnimations.put(LivingMotions.CHASE, Animations.BIPED_WALK);
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

        if (HeroNightfallMovesets.isSupported(hero.getMainHandItem())) {
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

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> getPlayerLikeAttackMotionBuilder(CapabilityItem capability) {
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        if (profile == null || profile.comboAnimations().isEmpty()) {
            return null;
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        for (int i = 0; i < profile.comboAnimations().size(); i++) {
            int comboIndex = i;
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                    .weight(100.0F)
                    .canBeInterrupted(false)
                    .looping(false);
            comboSeries.nextBehavior(this.createTrackedAttackBehavior(
                            profile.comboAnimations().get(i),
                            HeroEpicFightPatch::resolveNextTapActionState,
                            hero -> this.advancePlayerLikeComboStep(hero, profile.comboAnimations().size(), comboIndex))
                    .custom(mobPatch -> this.canStartPlayerLikeGroundCombo(mobPatch, profile, comboIndex)));
            builder.newBehaviorSeries(comboSeries);
        }

        if (!profile.dashAnimations().isEmpty()) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> dashSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                    .weight(180.0F)
                    .cooldown(24)
                    .canBeInterrupted(false)
                    .looping(false);
            for (AnimationManager.AnimationAccessor<? extends StaticAnimation> animation : profile.dashAnimations()) {
                dashSeries.nextBehavior(this.createTrackedAttackBehavior(animation, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep)
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
            for (AnimationManager.AnimationAccessor<? extends StaticAnimation> animation : profile.airAnimations()) {
                airSeries.nextBehavior(this.createTrackedAttackBehavior(animation, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep)
                        .custom(this::canStartPlayerLikeAirAttack));
            }
            builder.newBehaviorSeries(airSeries);
        }

        return builder;
    }

    private PlayerLikeAttackProfile resolvePlayerLikeAttackProfile(CapabilityItem capability) {
        HeroEntity hero = this.getOriginal();
        ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
        ResourceLocation itemId = !stack.isEmpty() ? ForgeRegistries.ITEMS.getKey(stack.getItem()) : null;
        String itemPath = itemId != null ? itemId.getPath().toLowerCase() : "";
        if (!stack.isEmpty() && stack.getItem() instanceof PoemOfTheEndItem) {
            return new PlayerLikeAttackProfile(
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3),
                    List.of(Animations.SWORD_DASH),
                    List.of(Animations.SWORD_AIR_SLASH),
                    2.5D,
                    1.6D,
                    3.75D,
                    3.0D
            );
        }


        if (capability == null || capability.isEmpty()) {
            return null;
        }

        Style style = capability.getStyle(this);
        WeaponCategory category = capability.getWeaponCategory();

        if (category == CapabilityItem.WeaponCategories.SWORD) {
            if (style == CapabilityItem.Styles.TWO_HAND) {
                return new PlayerLikeAttackProfile(
                        List.of(Animations.SWORD_DUAL_AUTO1, Animations.SWORD_DUAL_AUTO2, Animations.SWORD_DUAL_AUTO3),
                        List.of(Animations.SWORD_DUAL_DASH),
                        List.of(Animations.SWORD_DUAL_AIR_SLASH),
                        2.5D,
                        1.6D,
                        3.75D,
                        3.0D
                );
            }
            return new PlayerLikeAttackProfile(
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3),
                    List.of(Animations.SWORD_DASH),
                    List.of(Animations.SWORD_AIR_SLASH),
                    2.5D,
                    1.6D,
                    3.75D,
                    3.0D
            );
        }

        if (category == CapabilityItem.WeaponCategories.AXE) {
            return new PlayerLikeAttackProfile(
                    List.of(Animations.AXE_AUTO1, Animations.AXE_AUTO2),
                    List.of(Animations.AXE_DASH),
                    List.of(Animations.AXE_AIRSLASH),
                    2.0D,
                    1.45D,
                    3.2D,
                    2.75D
            );
        }

        if (category == CapabilityItem.WeaponCategories.LONGSWORD) {
            if (style == CapabilityItem.Styles.OCHS) {
                return new PlayerLikeAttackProfile(
                        List.of(Animations.LONGSWORD_LIECHTENAUER_AUTO1, Animations.LONGSWORD_LIECHTENAUER_AUTO2, Animations.LONGSWORD_LIECHTENAUER_AUTO3),
                        List.of(Animations.LONGSWORD_DASH),
                        List.of(Animations.LONGSWORD_AIR_SLASH),
                        2.75D,
                        1.85D,
                        4.1D,
                        3.35D
                );
            }
            return new PlayerLikeAttackProfile(
                    List.of(Animations.LONGSWORD_AUTO1, Animations.LONGSWORD_AUTO2, Animations.LONGSWORD_AUTO3),
                    List.of(Animations.LONGSWORD_DASH),
                    List.of(Animations.LONGSWORD_AIR_SLASH),
                    2.75D,
                    1.85D,
                    4.1D,
                    3.35D
            );
        }

        if (category == CapabilityItem.WeaponCategories.SPEAR) {
            if (style == CapabilityItem.Styles.ONE_HAND) {
                return new PlayerLikeAttackProfile(
                        List.of(Animations.SPEAR_ONEHAND_AUTO),
                        List.of(Animations.SPEAR_DASH),
                        List.of(Animations.SPEAR_ONEHAND_AIR_SLASH),
                        3.0D,
                        2.0D,
                        4.4D,
                        3.6D
                );
            }
            return new PlayerLikeAttackProfile(
                    List.of(Animations.SPEAR_TWOHAND_AUTO1, Animations.SPEAR_TWOHAND_AUTO2),
                    List.of(Animations.SPEAR_DASH),
                    List.of(Animations.SPEAR_TWOHAND_AIR_SLASH),
                    3.0D,
                    2.0D,
                    4.4D,
                    3.6D
            );
        }

        if (category == CapabilityItem.WeaponCategories.GREATSWORD) {
            return new PlayerLikeAttackProfile(
                    List.of(Animations.GREATSWORD_AUTO1, Animations.GREATSWORD_AUTO2),
                    List.of(Animations.GREATSWORD_DASH),
                    List.of(Animations.GREATSWORD_AIR_SLASH),
                    3.0D,
                    2.0D,
                    4.5D,
                    3.75D
            );
        }

        if (category == CapabilityItem.WeaponCategories.UCHIGATANA) {
            if (style == CapabilityItem.Styles.SHEATH) {
                return new PlayerLikeAttackProfile(
                        List.of(Animations.UCHIGATANA_SHEATHING_AUTO),
                        List.of(Animations.UCHIGATANA_SHEATHING_DASH),
                        List.of(Animations.UCHIGATANA_SHEATH_AIR_SLASH),
                        2.75D,
                        1.75D,
                        4.0D,
                        3.25D
                );
            }
            return new PlayerLikeAttackProfile(
                    List.of(Animations.UCHIGATANA_AUTO1, Animations.UCHIGATANA_AUTO2, Animations.UCHIGATANA_AUTO3),
                    List.of(Animations.UCHIGATANA_DASH),
                    List.of(Animations.UCHIGATANA_AIR_SLASH),
                    2.75D,
                    1.75D,
                    4.0D,
                    3.25D
            );
        }

        if (category == CapabilityItem.WeaponCategories.TACHI) {
            return new PlayerLikeAttackProfile(
                    List.of(Animations.TACHI_AUTO1, Animations.TACHI_AUTO2, Animations.TACHI_AUTO3),
                    List.of(Animations.TACHI_DASH),
                    List.of(Animations.LONGSWORD_AIR_SLASH),
                    2.75D,
                    1.85D,
                    4.1D,
                    3.35D
            );
        }

        if (category == CapabilityItem.WeaponCategories.DAGGER) {
            if (style == CapabilityItem.Styles.TWO_HAND) {
                return new PlayerLikeAttackProfile(
                        List.of(Animations.DAGGER_DUAL_AUTO1, Animations.DAGGER_DUAL_AUTO2, Animations.DAGGER_DUAL_AUTO3, Animations.DAGGER_DUAL_AUTO4),
                        List.of(Animations.DAGGER_DUAL_DASH),
                        List.of(Animations.DAGGER_DUAL_AIR_SLASH),
                        2.0D,
                        1.25D,
                        3.0D,
                        2.6D
                );
            }
            return new PlayerLikeAttackProfile(
                    List.of(Animations.DAGGER_AUTO1, Animations.DAGGER_AUTO2, Animations.DAGGER_AUTO3),
                    List.of(Animations.DAGGER_DASH),
                    List.of(Animations.DAGGER_AIR_SLASH),
                    2.0D,
                    1.25D,
                    3.0D,
                    2.6D
            );
        }

        return null;
    }

    private double getPlayerLikeChaseRadius(CapabilityItem capability) {
        PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
        if (profile == null) {
            return 0.0D;
        }

        return this.getPlayerLikeComboContinueDistance(profile);
    }

    private double getPlayerLikeComboContinueDistance(PlayerLikeAttackProfile profile) {
        return profile.comboMaxDistance() + 0.35D;
    }

    private CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createTrackedAttackBehavior(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation,
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
                    hero.beginBattleAction(actionState);
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

    private boolean isPlayableAttackAnimation(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private boolean canStartPlayerLikeGroundCombo(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile, int comboIndex) {
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
                && !this.isDashLikeMovement(hero, profile)
                && !HeroCombatPlanner.isAttackReplayLocked(hero, 6)
                && this.getExpectedPlayerLikeComboIndex(hero, profile.comboAnimations().size()) == comboIndex
                && (HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, this.getPlayerLikeComboContinueDistance(profile), 4 + Math.min(comboIndex, 2))
                || (comboIndex > 0 && HeroCombatPlanner.canQueueComboFollowUp(hero, target, this.getPlayerLikeComboContinueDistance(profile) * this.getPlayerLikeComboContinueDistance(profile), 3)))
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO);
    }

    private boolean canStartPlayerLikeDashAttack(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile) {
        HeroEntity hero = this.getTrackedHero(mobPatch);
        LivingEntity target = this.getTrackedTarget(hero);
        HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(profile);
        return hero != null
                && target != null
                && this.isPlayerLikeGroundState(hero)
                && this.isDashLikeMovement(hero, profile)
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
                // 飞行追击悬停时同样允许空中连段（浮空态不再拦截）；
                // HeroCombatPlanner.canStartAirAttack 已放行悬停、仅拦截快速爬升
                && hero.getDeltaMovement().y < 0.08D
                && HeroCombatPlanner.canStartAirAttack(hero, target, this.resolveAirAttackMaxDistance(hero), 5)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR);
    }

    private boolean isPlayerLikeGroundState(HeroEntity hero) {
        return hero.isBattleModeActive()
                && hero.onGround()
                && !hero.isFloating()
                && !hero.isBattleHoldAction()
                && !HeroCombatPlanner.isAttackReplayLocked(hero, 8)
                && !hero.isBattleReleaseAction();
    }

    private boolean isDashLikeMovement(HeroEntity hero, PlayerLikeAttackProfile profile) {
        return hero.getDeltaMovement().horizontalDistanceSqr() > 0.02D && profile.dashMaxDistance() > 0.0D;
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

        WeaponCategory fallbackCategory = this.getNightfallFallbackCategory(capability, stack);
        if (fallbackCategory != null) {
            builder = this.getWeaponMotionBuilder(fallbackCategory, CapabilityItem.Styles.TWO_HAND);
            if (builder != null) {
                return builder;
            }
            return this.getWeaponMotionBuilder(fallbackCategory, CapabilityItem.Styles.COMMON);
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

    private WeaponCategory getNightfallFallbackCategory(CapabilityItem capability, ItemStack stack) {
        String categoryName = String.valueOf(capability.getWeaponCategory()).toLowerCase();
        String itemPath = "";
        if (stack != null && !stack.isEmpty()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null && "efn".equals(itemId.getNamespace())) {
                itemPath = itemId.getPath().toLowerCase();
            }
        }

        String key = categoryName + " " + itemPath;
        if (key.contains("yamato") || key.contains("murasama") || key.contains("hf_blade") || key.contains("tachi") || key.contains("kusabimaru")) {
            return CapabilityItem.WeaponCategories.TACHI;
        }
        if (key.contains("ruinsgreatsword") || key.contains("thornwheel") || key.contains("greatsword")) {
            return CapabilityItem.WeaponCategories.GREATSWORD;
        }
        if (key.contains("meen") || key.contains("lance") || key.contains("spear")) {
            return CapabilityItem.WeaponCategories.SPEAR;
        }
        if (key.contains("claw") || key.contains("beast")) {
            return CapabilityItem.WeaponCategories.FIST;
        }
        if (key.contains("shortsword") || key.contains("dual") || key.contains("bloodlust") || key.contains("crescent") || key.contains("exsilium") || key.contains("pioneer") || key.contains("broadblade")) {
            return CapabilityItem.WeaponCategories.LONGSWORD;
        }

        return null;
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
        if (hero == null || hero.isRemoved()) {
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
        if (hero == null || hero.isRemoved()) {
            return;
        }
        if (!hero.isBattleModeActive()) {
            if (this.infantryAiConfigured || this.heroAttackGoal != null || this.heroChasingGoal != null) {
                HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.ensureInfantryAiConfigured", "step=disableOutOfBattle,state=" + this.debugPatchState(hero));
            }
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
            return;
        }
        if (this.infantryAiConfigured) {
            return;
        }

        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.ensureInfantryAiConfigured", "step=configureBegin,state=" + this.debugPatchState(hero));
        this.setAIAsInfantry(this.isRangedWeaponEquipped());
        this.infantryAiConfigured = true;
        HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.ensureInfantryAiConfigured", "step=configureDone,state=" + this.debugPatchState(hero));
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
        if (this.heroAttackGoal != null || this.heroChasingGoal != null) {
            HeroEpicFightDebugLog.event(hero, "HeroEpicFightPatch.removeHeroCombatGoals", "attackGoal=" + (this.heroAttackGoal != null) + ",chasingGoal=" + (this.heroChasingGoal != null) + ",state=" + this.debugPatchState(hero));
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

    private void logTickState(String scope) {
        HeroEntity hero = this.getOriginal();
        if (hero == null) {
            return;
        }

        String transitionState = this.debugPatchTransitionState(hero);
        HeroEpicFightDebugLog.transition(hero, scope, transitionState, "state=" + this.debugPatchState(hero));
    }

    private String debugPatchState(HeroEntity hero) {
        return HeroEpicFightDebugLog.heroCoreState(hero)
                + ",living=" + this.currentLivingMotion
                + ",composite=" + this.currentCompositeMotion
                + ",anim=" + HeroEpicFightDebugLog.animatorKey(this.getAnimator())
                + ",profile=" + this.getCurrentWeaponProfileKey(hero)
                + ",lastProfile=" + this.lastWeaponProfileKey
                + ",ai=" + this.infantryAiConfigured;
    }

    private String debugPatchTransitionState(HeroEntity hero) {
        return "battle=" + hero.isBattleModeActive()
                + ",action=" + hero.getBattleActionState()
                + ",combo=" + hero.getBattleComboStep()
                + ",main=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem())
                + ",off=" + HeroEpicFightDebugLog.itemKey(hero.getOffhandItem())
                + ",living=" + this.currentLivingMotion
                + ",composite=" + this.currentCompositeMotion
                + ",anim=" + HeroEpicFightDebugLog.animatorId(this.getAnimator())
                + ",profile=" + this.getCurrentWeaponProfileKey(hero)
                + ",lastProfile=" + this.lastWeaponProfileKey
                + ",ai=" + this.infantryAiConfigured;
    }
}


