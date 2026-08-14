package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.mojang.datafixers.util.Pair;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroEpicFightChaseGoal;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.Item;
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
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.capabilities.item.CapabilityItem.Styles;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors.Behavior;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors.BehaviorSeries;

public class HeroEpicFightPatch extends HumanoidMobPatch<HeroEntity> {
   private static final double MELEE_CHASE_SPEED = 1.35;
   private boolean infantryAiConfigured;
   private String lastWeaponProfileKey = "";
   private Goal heroAttackGoal;
   private Goal heroChasingGoal;
   private boolean offhandSwapActive;
   private ItemStack expectedSwappedMainhand;
   private ItemStack expectedSwappedOffhand;

   public HeroEpicFightPatch(HeroEntity hero) {
      super(hero, Factions.NEUTRAL);
      this.expectedSwappedMainhand = ItemStack.EMPTY;
      this.expectedSwappedOffhand = ItemStack.EMPTY;
   }

   public boolean overrideRender() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      return hero != null && !(Boolean)hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE) && hero.isBattleModeActive() ? super.overrideRender() : false;
   }

   public void onConstructed(HeroEntity hero) {
      super.onConstructed(hero);
   }

   protected void initAI() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null && hero.isAddedToLevel() && hero.isBattleModeActive()) {
         super.initAI();
      } else {
         this.removeHeroCombatGoals();
         this.infantryAiConfigured = false;
      }
   }

   public void onJoinWorld(HeroEntity hero, Level level, boolean isLogicalClient) {
      super.onJoinWorld(hero, level, isLogicalClient);
      HeroEpicFightWeaponProfiles.bootstrap();
      this.ensureInfantryAiConfigured();
      if (this.isEquipmentReady(hero)) {
         this.syncWeaponLivingMotions(true);
      }

   }

   public void initAnimator(Animator animator) {
      super.initAnimator(animator);
      HeroEntity hero = (HeroEntity)this.getOriginal();
      boolean battleMode = hero != null && hero.isBattleModeActive();
      Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations = new LinkedHashMap();
      this.applyBaseLivingAnimations(livingAnimations, battleMode);
      Objects.requireNonNull(animator);
      livingAnimations.forEach(animator::addLivingAnimation);
   }

   public void onStartTracking(ServerPlayer trackingPlayer) {
      if (this.isEquipmentReady((HeroEntity)this.getOriginal())) {
         this.syncWeaponLivingMotions(true);
      }

   }

   public void preTickServer() {
      HeroEpicFightWeaponProfiles.bootstrap();
      HeroEntity hero = (HeroEntity)this.getOriginal();
      String currentProfileKey = this.getCurrentWeaponProfileKey(hero);
      boolean weaponProfileChanged = !currentProfileKey.equals(this.lastWeaponProfileKey);
      this.ensureInfantryAiConfigured();
      this.syncWeaponLivingMotions(weaponProfileChanged);
      super.preTickServer();
      HeroCombatPlanner.tickEpicFightActionClock(hero);
      HeroNightfallMovesets.tickSkillEffects(this, hero);
   }

   public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null && hero.isAddedToLevel()) {
         this.infantryAiConfigured = false;
         if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.syncWeaponLivingMotions(true);
         } else {
            super.updateHeldItem(fromCap, toCap, from, to, hand);
            if (!hero.level().isClientSide) {
               this.ensureInfantryAiConfigured();
               this.syncWeaponLivingMotions(true);
            }

         }
      }
   }

   protected void setOffhandDamage(InteractionHand hand, ItemStack mainhandItemStack, ItemStack offhandItemStack, boolean offhandValid, Collection<AttributeModifier> mainhandAttributes, Collection<AttributeModifier> offhandAttributes) {
      if (hand == InteractionHand.OFF_HAND) {
         this.offhandSwapActive = true;
         this.expectedSwappedMainhand = offhandValid ? offhandItemStack.copy() : ItemStack.EMPTY;
         this.expectedSwappedOffhand = mainhandItemStack.copy();
      }

      super.setOffhandDamage(hand, mainhandItemStack, offhandItemStack, offhandValid, mainhandAttributes, offhandAttributes);
   }

   protected void recoverMainhandDamage(InteractionHand hand, ItemStack mainhandItemStack, ItemStack offhandItemStack, Collection<AttributeModifier> mainhandAttributes, Collection<AttributeModifier> offhandAttributes) {
      if (hand != InteractionHand.MAIN_HAND) {
         HeroEntity hero = (HeroEntity)this.getOriginal();

         try {
            boolean canRestoreHands = hero != null && this.offhandSwapActive && ItemStack.matches(hero.getMainHandItem(), this.expectedSwappedMainhand) && ItemStack.matches(hero.getOffhandItem(), this.expectedSwappedOffhand);
            if (canRestoreHands) {
               hero.setItemInHand(InteractionHand.MAIN_HAND, mainhandItemStack);
               hero.setItemInHand(InteractionHand.OFF_HAND, offhandItemStack);
            }

            AttributeInstance damageAttributeInstance = ((HeroEntity)this.original).getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttributeInstance != null) {
               Objects.requireNonNull(damageAttributeInstance);
               offhandAttributes.forEach(damageAttributeInstance::removeModifier);
               Objects.requireNonNull(damageAttributeInstance);
               mainhandAttributes.forEach(damageAttributeInstance::addTransientModifier);
            }
         } finally {
            this.offhandSwapActive = false;
            this.expectedSwappedMainhand = ItemStack.EMPTY;
            this.expectedSwappedOffhand = ItemStack.EMPTY;
         }

      }
   }

   public void preTickClient() {
      HeroEpicFightWeaponProfiles.bootstrap();
      super.preTickClient();
      this.syncWeaponLivingMotions(false);
   }

   protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
      if (HeroEpicFightWeaponProfiles.isRangedLoadout(stack)) {
         return null;
      } else {
         CombatBehaviors.Builder<HumanoidMobPatch<?>> nightfallBuilder = HeroNightfallMovesets.buildCombatBehaviors(this, stack);
         if (nightfallBuilder != null) {
            return nightfallBuilder;
         } else {
            CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability((HeroEntity)this.getOriginal());
            CombatBehaviors.Builder<HumanoidMobPatch<?>> playerLikeBuilder = this.getPlayerLikeAttackMotionBuilder(capability);
            if (playerLikeBuilder != null) {
               return playerLikeBuilder;
            } else {
               CombatBehaviors.Builder<HumanoidMobPatch<?>> nativeBuilder = this.getWeaponMotionBuilder(capability, stack);
               return nativeBuilder != null ? nativeBuilder : super.getHoldingItemWeaponMotionBuilder();
            }
         }
      }
   }

   public void setAIAsInfantry(boolean holdingRanedWeapon) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null) {
         if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
         } else {
            Set<Goal> toRemove = new HashSet();
            this.selectGoalToRemove(toRemove);
            GoalSelector var10001 = hero.goalSelector;
            Objects.requireNonNull(var10001);
            toRemove.forEach(var10001::removeGoal);
            this.removeHeroCombatGoals();
            if (holdingRanedWeapon) {
               this.heroAttackGoal = new HeroEpicFightRangedAttackGoal(this, hero, (double)1.0F, 14.0F);
               hero.goalSelector.addGoal(0, this.heroAttackGoal);
            } else {
               CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = this.getHoldingItemWeaponMotionBuilder();
               if (builder != null) {
                  ItemStack stack = hero.getMainHandItem();
                  double attackRadius = HeroNightfallMovesets.getAttackRadius(stack, (double)0.0F);
                  if (attackRadius <= (double)0.0F) {
                     attackRadius = this.getPlayerLikeChaseRadius(HeroEpicFightWeaponProfiles.resolveCapability(hero));
                  }

                  this.heroAttackGoal = new AnimatedAttackGoal(this, builder.build(this));
                  this.heroChasingGoal = new HeroEpicFightChaseGoal(hero, 1.35, attackRadius);
                  hero.goalSelector.addGoal(0, this.heroAttackGoal);
                  hero.goalSelector.addGoal(1, this.heroChasingGoal);
               }

            }
         }
      }
   }

   public void updateMotion(boolean considerInaction) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null) {
         boolean rangedLoadout = this.isRangedWeaponEquipped();
         if (hero.isCastingThunder()) {
            this.currentLivingMotion = LivingMotions.SPELLCAST;
            this.currentCompositeMotion = this.resolveCompositeMotion(hero, this.currentLivingMotion);
         } else if (hero.isFloating() && !hero.isBattleModeActive()) {
            this.currentLivingMotion = hero.getDeltaMovement().lengthSqr() > 0.01 ? LivingMotions.FLY : LivingMotions.FLOAT;
            this.currentCompositeMotion = this.resolveCompositeMotion(hero, this.currentLivingMotion);
         } else {
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
      }
   }

   private void syncWeaponLivingMotions(boolean force) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      Animator animator = this.getAnimator();
      if (animator != null && this.isEquipmentReady(hero)) {
         String currentProfileKey = this.getCurrentWeaponProfileKey(hero);
         boolean battleMode = hero.isBattleModeActive();
         if (force || !currentProfileKey.equals(this.lastWeaponProfileKey)) {
            Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations = new LinkedHashMap();
            this.applyBaseLivingAnimations(livingAnimations, battleMode);
            if (battleMode) {
               HeroNightfallMovesets.applyLivingAnimations(hero.getMainHandItem(), livingAnimations);
               CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
               if (capability != null && !capability.isEmpty()) {
                  livingAnimations.putAll(capability.getLivingMotionModifier(this, InteractionHand.MAIN_HAND));
                  this.applyWeaponCategoryLivingMotions(livingAnimations, capability);
                  HeroNightfallMovesets.applyLivingAnimations(hero.getMainHandItem(), livingAnimations);
               }

               this.applyRunChaseLivingMotion(livingAnimations);
            }

            animator.resetLivingAnimations();
            Objects.requireNonNull(animator);
            livingAnimations.forEach(animator::addLivingAnimation);
            if (animator instanceof ClientAnimator) {
               ClientAnimator clientAnimator = (ClientAnimator)animator;
               clientAnimator.setCurrentMotionsAsDefault();
            }

            if (!hero.level().isClientSide) {
               SPChangeLivingMotion packet = new SPChangeLivingMotion(hero.getId());
               packet.putEntries(livingAnimations.entrySet());
               EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packet, hero, new CustomPacketPayload[0]);
            }

            this.lastWeaponProfileKey = currentProfileKey;
         }
      }
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
      } else if (HeroNightfallMovesets.isSupported(hero.getMainHandItem())) {
         return baseMotion;
      } else {
         CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
         if (capability != null && !capability.isEmpty()) {
            LivingMotion customLivingMotion = capability.getLivingMotion(this, InteractionHand.MAIN_HAND);
            if (customLivingMotion != null) {
               return customLivingMotion;
            }
         }

         if (hero.isBattleHoldAction()) {
            return this.getHoldCompositeMotion(hero);
         } else if (hero.isBattleReleaseAction()) {
            return this.getReleaseCompositeMotion(hero);
         } else {
            return hero.isBattleTapAction() ? this.getTapCompositeMotion(hero, baseMotion) : baseMotion;
         }
      }
   }

   private LivingMotion getTapCompositeMotion(HeroEntity hero, LivingMotion baseMotion) {
      ItemStack stack = hero.getMainHandItem();
      return stack.getItem() instanceof PoemOfTheEndItem ? baseMotion : baseMotion;
   }

   private LivingMotion getHoldCompositeMotion(HeroEntity hero) {
      ItemStack stack = hero.getMainHandItem();
      Item var4 = stack.getItem();
      if (var4 instanceof PoemOfTheEndItem) {
         PoemOfTheEndItem poem = (PoemOfTheEndItem)var4;
         LivingMotions var10000;
         switch (poem.getMode(stack)) {
            case 0:
            case 1:
            case 2:
               var10000 = LivingMotions.AIM;
               break;
            case 3:
               var10000 = LivingMotions.DIGGING;
               break;
            default:
               var10000 = LivingMotions.AIM;
         }

         return var10000;
      } else {
         return LivingMotions.AIM;
      }
   }

   private LivingMotion getReleaseCompositeMotion(HeroEntity hero) {
      ItemStack stack = hero.getMainHandItem();
      Item var4 = stack.getItem();
      if (var4 instanceof PoemOfTheEndItem) {
         PoemOfTheEndItem poem = (PoemOfTheEndItem)var4;
         LivingMotions var10000;
         switch (poem.getMode(stack)) {
            case 0:
            case 1:
            case 2:
               var10000 = LivingMotions.SHOT;
               break;
            case 3:
               var10000 = LivingMotions.DIGGING;
               break;
            default:
               var10000 = LivingMotions.SHOT;
         }

         return var10000;
      } else {
         return LivingMotions.SHOT;
      }
   }

   private void applyWeaponCategoryLivingMotions(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations, CapabilityItem capability) {
      if (this.weaponLivingMotions != null && capability != null && !capability.isEmpty()) {
         Map<Style, Set<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>>> motionsByStyle = (Map)this.weaponLivingMotions.get(capability.getWeaponCategory());
         if (motionsByStyle != null) {
            Style style = capability.getStyle(this);
            Set<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>> styleMotions = (Set)motionsByStyle.get(style);
            if (styleMotions == null) {
               styleMotions = (Set)motionsByStyle.get(Styles.COMMON);
            }

            if (styleMotions != null) {
               for(Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motionPair : styleMotions) {
                  livingAnimations.put((LivingMotion)motionPair.getFirst(), (AssetAccessor)motionPair.getSecond());
               }

            }
         }
      }
   }

   private void applyRunChaseLivingMotion(Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations) {
      AssetAccessor<? extends StaticAnimation> runAnimation = (AssetAccessor)livingAnimations.get(LivingMotions.RUN);
      livingAnimations.put(LivingMotions.CHASE, runAnimation != null ? runAnimation : Animations.BIPED_RUN);
   }

   private CombatBehaviors.Builder<HumanoidMobPatch<?>> getPlayerLikeAttackMotionBuilder(CapabilityItem capability) {
      PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
      if (profile != null && !profile.comboAnimations().isEmpty()) {
         CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.<HumanoidMobPatch<?>>builder();
         CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = BehaviorSeries.<HumanoidMobPatch<?>>builder().weight(140.0F).canBeInterrupted(false).looping(true);
         comboSeries.nextBehavior(this.createTrackedComboAttackBehavior(profile).custom((mobPatch) -> this.canStartPlayerLikeGroundCombo(mobPatch, profile)));
         builder.newBehaviorSeries(comboSeries);
         if (!profile.dashAnimations().isEmpty()) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> dashSeries = BehaviorSeries.<HumanoidMobPatch<?>>builder().weight(65.0F).cooldown(24).canBeInterrupted(false).looping(false);

            for(int i = 0; i < profile.dashAnimations().size(); ++i) {
               AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = (AnimationManager.AnimationAccessor)profile.dashAnimations().get(i);
               HeroCombatPlanner.ActionProfile actionProfile = (HeroCombatPlanner.ActionProfile)profile.dashActionProfiles().get(i);
               dashSeries.nextBehavior(this.createTrackedAttackBehavior(animation, actionProfile, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep).custom((mobPatch) -> this.canStartPlayerLikeDashAttack(mobPatch, profile)));
            }

            builder.newBehaviorSeries(dashSeries);
         }

         if (!profile.airAnimations().isEmpty()) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> airSeries = BehaviorSeries.<HumanoidMobPatch<?>>builder().weight(220.0F).cooldown(16).canBeInterrupted(false).looping(false);

            for(int i = 0; i < profile.airAnimations().size(); ++i) {
               AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = (AnimationManager.AnimationAccessor)profile.airAnimations().get(i);
               HeroCombatPlanner.ActionProfile actionProfile = (HeroCombatPlanner.ActionProfile)profile.airActionProfiles().get(i);
               airSeries.nextBehavior(this.createTrackedAttackBehavior(animation, actionProfile, HeroEpicFightPatch::resolveNextTapActionState, this::resetPlayerLikeComboStep).custom(this::canStartPlayerLikeAirAttack));
            }

            builder.newBehaviorSeries(airSeries);
         }

         return builder;
      } else {
         return null;
      }
   }

   private PlayerLikeAttackProfile resolvePlayerLikeAttackProfile(CapabilityItem capability) {
      PlayerLikeAttackProfile moveSetProfile = this.resolveMoveSetAttackProfile(capability);
      if (moveSetProfile != null) {
         this.applyDynamicActionProfiles(moveSetProfile);
         return moveSetProfile;
      } else {
         HeroEntity hero = (HeroEntity)this.getOriginal();
         ItemStack stack = hero != null ? hero.getMainHandItem() : ItemStack.EMPTY;
         if (!stack.isEmpty() && stack.getItem() instanceof PoemOfTheEndItem) {
            PlayerLikeAttackProfile profile = this.createPlayerLikeAttackProfile(List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3), List.of(Animations.SWORD_DASH), List.of(Animations.SWORD_AIR_SLASH), (double)2.5F, 1.6, (double)3.75F, (double)3.0F);
            this.applyDynamicActionProfiles(profile);
            return profile;
         } else {
            this.clearDynamicActionProfiles();
            return null;
         }
      }
   }

   private PlayerLikeAttackProfile resolveMoveSetAttackProfile(CapabilityItem capability) {
      if (!(capability instanceof WeaponCapability weaponCapability)) {
         return null;
      } else {
         Object moveSet = weaponCapability.getCurrentSet(this);
         List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> moveSetAnimations = getComboAttackAnimations(moveSet);
         if (moveSetAnimations.isEmpty()) {
            return null;
         } else {
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> usableAnimations = new ArrayList();
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations = new ArrayList();
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations = new ArrayList();
            List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations = new ArrayList();

            for(AnimationManager.AnimationAccessor<? extends AttackAnimation> animation : moveSetAnimations) {
               if (animation != null) {
                  usableAnimations.add(animation);
                  if (this.isDashAttackAnimation(animation)) {
                     dashAnimations.add(animation);
                  } else if (this.isAirAttackAnimation(animation)) {
                     airAnimations.add(animation);
                  } else {
                     comboAnimations.add(animation);
                  }
               }
            }

            if (comboAnimations.isEmpty() && usableAnimations.size() == 1) {
               comboAnimations.add((AnimationManager.AnimationAccessor)usableAnimations.getFirst());
            } else if (comboAnimations.isEmpty() && !usableAnimations.isEmpty()) {
               comboAnimations.addAll(usableAnimations);
            }

            if (comboAnimations.isEmpty()) {
               return null;
            } else {
               double reach = Math.max((double)0.0F, (double)weaponCapability.getReach());
               double comboMaxDistance = 2.4 + reach;
               double dashMinDistance = 1.35 + Math.min(reach, (double)1.0F) * (double)0.25F;
               double dashMaxDistance = comboMaxDistance + (double)1.25F;
               double airMaxDistance = comboMaxDistance + (double)0.5F;
               return this.createPlayerLikeAttackProfile(comboAnimations, dashAnimations, airAnimations, comboMaxDistance, dashMinDistance, dashMaxDistance, airMaxDistance);
            }
         }
      }
   }

   private static List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> getComboAttackAnimations(Object moveSet) {
      if (moveSet == null) {
         return List.of();
      } else {
         try {
            Object result = moveSet.getClass().getMethod("getComboAttackAnimations").invoke(moveSet);
            if (result instanceof List) {
               List<?> animations = (List)result;
               return (List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>)animations;
            }
         } catch (LinkageError | ReflectiveOperationException var3) {
         }

         return List.of();
      }
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
      return animation != null && animation.registryName() != null ? animation.registryName().getPath().toLowerCase(Locale.ROOT) : "";
   }

   private double getPlayerLikeChaseRadius(CapabilityItem capability) {
      PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
      return profile == null ? (double)0.0F : this.getPlayerLikeComboContinueDistance(profile);
   }

   private double getPlayerLikeComboContinueDistance(PlayerLikeAttackProfile profile) {
      return profile.comboMaxDistance() + 0.15;
   }

   private CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createTrackedAttackBehavior(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation, HeroCombatPlanner.ActionProfile actionProfile, ToIntFunction<HeroEntity> actionStateResolver, Consumer<HeroEntity> comboStepUpdater) {
      return Behavior.<HumanoidMobPatch<?>>builder().behavior((mobPatch) -> {
         if (this.isPlayableAttackAnimation(animation)) {
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
         }
      });
   }

   private CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createTrackedComboAttackBehavior(PlayerLikeAttackProfile profile) {
      return Behavior.<HumanoidMobPatch<?>>builder().behavior((mobPatch) -> {
         if (profile != null && !profile.comboAnimations().isEmpty()) {
            HeroEntity hero = this.getTrackedHero(mobPatch);
            int comboIndex = this.getExpectedPlayerLikeComboIndex(hero, profile.comboAnimations().size());
            AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = (AnimationManager.AnimationAccessor)profile.comboAnimations().get(comboIndex);
            HeroCombatPlanner.ActionProfile actionProfile = (HeroCombatPlanner.ActionProfile)profile.comboActionProfiles().get(comboIndex);
            if (this.isPlayableAttackAnimation(animation)) {
               if (hero != null) {
                  hero.beginBattleAction(resolveComboActionState(comboIndex), actionProfile);
                  this.advancePlayerLikeComboStep(hero, profile.comboAnimations().size(), comboIndex);
                  hero.swing(InteractionHand.MAIN_HAND);
               }

               mobPatch.playAnimationSynchronized(animation, 0.0F);
            }
         }
      });
   }

   private boolean isPlayableAttackAnimation(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
      return animation != null && !animation.isEmpty();
   }

   private boolean canStartPlayerLikeGroundCombo(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile) {
      HeroEntity hero = this.getTrackedHero(mobPatch);
      if (hero == null) {
         return true;
      } else {
         LivingEntity target = this.getTrackedTarget(hero);
         if (target == null) {
            return false;
         } else {
            HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(profile);
            return this.isPlayerLikeGroundState(hero) && (HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, this.getPlayerLikeComboContinueDistance(profile), 4) || this.isTargetWithinComboFlowDistance(hero, target, profile, 0.65)) && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO);
         }
      }
   }

   private boolean canStartPlayerLikeDashAttack(HumanoidMobPatch<?> mobPatch, PlayerLikeAttackProfile profile) {
      HeroEntity hero = this.getTrackedHero(mobPatch);
      LivingEntity target = this.getTrackedTarget(hero);
      HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(profile);
      return hero != null && target != null && this.isPlayerLikeGroundState(hero) && this.isDashLikeMovement(hero, target, profile) && !HeroCombatPlanner.isAttackReplayLocked(hero, 8) && HeroCombatPlanner.isDashOpportunity(hero, target, profile.dashMinDistance(), profile.dashMaxDistance(), 6) && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.DASH);
   }

   private boolean canStartPlayerLikeAirAttack(HumanoidMobPatch<?> mobPatch) {
      HeroEntity hero = this.getTrackedHero(mobPatch);
      if (hero != null && hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
         LivingEntity target = this.getTrackedTarget(hero);
         HeroCombatPlanner.CombatTuning tuning = this.getPlayerLikeCombatTuning(HeroEpicFightWeaponProfiles.resolveCapability(hero));
         return !hero.onGround() && !hero.isFloating() && hero.getDeltaMovement().y < 0.08 && HeroCombatPlanner.canStartAirAttack(hero, target, this.resolveAirAttackMaxDistance(hero), 5) && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR);
      } else {
         return false;
      }
   }

   private boolean isPlayerLikeGroundState(HeroEntity hero) {
      return hero.isBattleModeActive() && hero.onGround() && !hero.isFloating() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction();
   }

   boolean shouldStopChasingForMelee(LivingEntity target) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null && target != null && target.isAlive() && !target.isRemoved()) {
         CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
         PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
         if (profile == null) {
            return hero.distanceToSqr(target) <= (double)4.0F;
         } else {
            return HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, this.getPlayerLikeComboContinueDistance(profile), 2) || this.isTargetWithinComboFlowDistance(hero, target, profile, 0.2);
         }
      } else {
         return false;
      }
   }

   private boolean isDashLikeMovement(HeroEntity hero, LivingEntity target, PlayerLikeAttackProfile profile) {
      if (hero != null && target != null && !(profile.dashMaxDistance() <= (double)0.0F)) {
         double horizontalDistanceSqr = hero.distanceToSqr(target.getX(), hero.getY(), target.getZ());
         double dashStartDistance = Math.max(profile.comboMaxDistance() + 0.45, profile.dashMinDistance());
         return horizontalDistanceSqr >= dashStartDistance * dashStartDistance;
      } else {
         return false;
      }
   }

   private boolean isTargetWithinComboFlowDistance(HeroEntity hero, LivingEntity target, PlayerLikeAttackProfile profile, double extraRange) {
      if (hero != null && target != null && profile != null) {
         double maxDistance = this.getPlayerLikeComboContinueDistance(profile) + Math.max((double)0.0F, extraRange);
         return hero.distanceToSqr(target) <= maxDistance * maxDistance;
      } else {
         return false;
      }
   }

   private LivingEntity getTrackedTarget(HeroEntity hero) {
      if (hero == null) {
         return null;
      } else {
         LivingEntity target = hero.getTarget();
         return target != null && target.isAlive() && !target.isRemoved() ? target : null;
      }
   }

   private double resolveAirAttackMaxDistance(HeroEntity hero) {
      CapabilityItem capability = HeroEpicFightWeaponProfiles.resolveCapability(hero);
      PlayerLikeAttackProfile profile = this.resolvePlayerLikeAttackProfile(capability);
      return profile != null ? profile.airMaxDistance() : (double)3.0F;
   }

   private HeroCombatPlanner.CombatTuning getPlayerLikeCombatTuning(PlayerLikeAttackProfile profile) {
      return profile == null ? HeroCombatPlanner.CombatTuning.comboOnly((double)0.0F) : new HeroCombatPlanner.CombatTuning(this.getPlayerLikeComboContinueDistance(profile), profile.dashMinDistance(), profile.dashMaxDistance(), profile.airMaxDistance(), (double)0.0F, (double)0.0F, !profile.dashAnimations().isEmpty(), !profile.airAnimations().isEmpty(), false);
   }

   private HeroCombatPlanner.CombatTuning getPlayerLikeCombatTuning(CapabilityItem capability) {
      return this.getPlayerLikeCombatTuning(this.resolvePlayerLikeAttackProfile(capability));
   }

   private HeroEntity getTrackedHero(HumanoidMobPatch<?> mobPatch) {
      if (mobPatch instanceof HeroEpicFightPatch heroPatch) {
         return (HeroEntity)heroPatch.getOriginal();
      } else {
         return null;
      }
   }

   private static int resolveNextTapActionState(HeroEntity hero) {
      if (hero == null) {
         return 2;
      } else {
         return hero.getBattleActionSerial() % 2 == 0 ? 2 : 3;
      }
   }

   private static int resolveComboActionState(int comboIndex) {
      return comboIndex % 2 == 0 ? 2 : 3;
   }

   private PlayerLikeAttackProfile createPlayerLikeAttackProfile(List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations, List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations, List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations, double comboMaxDistance, double dashMinDistance, double dashMaxDistance, double airMaxDistance) {
      List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboList = List.copyOf(comboAnimations);
      List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashList = List.copyOf(dashAnimations);
      List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airList = List.copyOf(airAnimations);
      return new PlayerLikeAttackProfile(comboList, dashList, airList, this.createDynamicActionProfiles(comboList, HeroCombatPlanner.PlannedAction.LIGHT_COMBO, "combo", (double)4.0F), this.createDynamicActionProfiles(dashList, HeroCombatPlanner.PlannedAction.DASH, "dash", (double)8.0F), this.createDynamicActionProfiles(airList, HeroCombatPlanner.PlannedAction.AIR, "air", (double)7.0F), comboMaxDistance, dashMinDistance, dashMaxDistance, airMaxDistance);
   }

   private List<HeroCombatPlanner.ActionProfile> createDynamicActionProfiles(List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> animations, HeroCombatPlanner.PlannedAction plannedAction, String namePrefix, double baseStyleScore) {
      List<HeroCombatPlanner.ActionProfile> profiles = new ArrayList();

      for(int i = 0; i < animations.size(); ++i) {
         profiles.add(this.createDynamicActionProfile((AnimationManager.AnimationAccessor)animations.get(i), plannedAction, namePrefix + "_" + i, baseStyleScore));
      }

      return List.copyOf(profiles);
   }

   private HeroCombatPlanner.ActionProfile createDynamicActionProfile(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation, HeroCombatPlanner.PlannedAction plannedAction, String name, double baseStyleScore) {
      HeroCombatPlanner.ActionPhaseSpec phaseSpec = this.derivePhaseSpec(animation);
      int windupTicks = Math.max(1, phaseSpec.activeEndTick());
      double var10000;
      switch (plannedAction) {
         case DASH:
            var10000 = 0.72;
            break;
         case AIR:
            var10000 = 0.45;
            break;
         case LIGHT_COMBO:
            var10000 = (double)0.5F;
            break;
         case SKILL:
            var10000 = 0.45;
            break;
         case APPROACH:
         case NONE:
            var10000 = (double)0.5F;
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      double heroVelocityInfluence = var10000;
      switch (plannedAction) {
         case DASH:
            var10000 = 0.65;
            break;
         case AIR:
            var10000 = 0.6;
            break;
         case LIGHT_COMBO:
            var10000 = 0.45;
            break;
         case SKILL:
            var10000 = (double)0.75F;
            break;
         case APPROACH:
         case NONE:
            var10000 = 0.45;
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      double tolerance = var10000;
      int bufferTicks = Math.max(3, phaseSpec.recoveryEndTick() - phaseSpec.startupEndTick());
      double weightFromLength = Math.max((double)0.0F, (double)this.secondsToTicks(this.getAnimationTotalTime(animation)) * 0.12 - 0.6);
      return new HeroCombatPlanner.ActionProfile(name, plannedAction, windupTicks, heroVelocityInfluence, tolerance, baseStyleScore + weightFromLength, bufferTicks, phaseSpec);
   }

   private HeroCombatPlanner.ActionPhaseSpec derivePhaseSpec(AnimationManager.AnimationAccessor<? extends StaticAnimation> animation) {
      int totalTicks = Math.max(2, this.secondsToTicks(this.getAnimationTotalTime(animation)));
      if (animation != null && !animation.isEmpty()) {
         Object var4 = animation.get();
         if (var4 instanceof AttackAnimation) {
            AttackAnimation attackAnimation = (AttackAnimation)var4;
            if (attackAnimation.phases.length > 0) {
               AttackAnimation.Phase phase = attackAnimation.phases[0];
               int startupEndTick = this.secondsToTicks(Math.max(phase.preDelay, phase.antic));
               int activeEndTick = this.secondsToTicks(Math.max(phase.contact, phase.preDelay + 0.05F));
               float recoveryTime = phase.recovery;
               float endTime = phase.end == Float.MAX_VALUE ? attackAnimation.getTotalTime() : phase.end;
               int chainEndTick = this.secondsToTicks(Math.max(recoveryTime, phase.contact + 0.05F));
               int recoveryEndTick = Math.max(totalTicks, this.secondsToTicks(Math.max(endTime, recoveryTime + 0.05F)));
               return this.normalizePhaseSpec(startupEndTick, activeEndTick, chainEndTick, recoveryEndTick);
            }
         }
      }

      int startupEndTick = Math.max(1, Math.round((float)totalTicks * 0.3F));
      int activeEndTick = Math.max(startupEndTick + 1, Math.round((float)totalTicks * 0.5F));
      int chainEndTick = Math.max(activeEndTick + 1, Math.round((float)totalTicks * 0.78F));
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
      return animation != null && !animation.isEmpty() ? Math.max(0.05F, ((StaticAnimation)animation.get()).getTotalTime()) : 0.4F;
   }

   private int secondsToTicks(float seconds) {
      return Math.max(1, Math.round(seconds / 0.05F));
   }

   private void applyDynamicActionProfiles(PlayerLikeAttackProfile profile) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null && profile != null) {
         hero.setBattleDynamicProfiles(profile.comboActionProfiles(), profile.dashActionProfiles().isEmpty() ? null : (HeroCombatPlanner.ActionProfile)profile.dashActionProfiles().get(0), profile.airActionProfiles().isEmpty() ? null : (HeroCombatPlanner.ActionProfile)profile.airActionProfiles().get(0));
      }
   }

   private void clearDynamicActionProfiles() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null) {
         hero.setBattleDynamicProfiles(List.of(), (HeroCombatPlanner.ActionProfile)null, (HeroCombatPlanner.ActionProfile)null);
      }
   }

   private int getExpectedPlayerLikeComboIndex(HeroEntity hero, int comboSize) {
      if (hero != null && comboSize > 0) {
         int step = hero.getBattleComboStep();
         return step < 0 ? 0 : step % comboSize;
      } else {
         return 0;
      }
   }

   private void advancePlayerLikeComboStep(HeroEntity hero, int comboSize, int executedIndex) {
      if (hero != null && comboSize > 0) {
         hero.setBattleComboStep((executedIndex + 1) % comboSize);
      }
   }

   private void resetPlayerLikeComboStep(HeroEntity hero) {
      if (hero != null) {
         hero.setBattleComboStep(0);
      }
   }

   private CombatBehaviors.Builder<HumanoidMobPatch<?>> getWeaponMotionBuilder(CapabilityItem capability, ItemStack stack) {
      if (capability != null && !capability.isEmpty() && this.weaponAttackMotions != null) {
         CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = this.getWeaponMotionBuilder(capability.getWeaponCategory(), capability.getStyle(this));
         return builder != null ? builder : null;
      } else {
         return null;
      }
   }

   private CombatBehaviors.Builder<HumanoidMobPatch<?>> getWeaponMotionBuilder(WeaponCategory category, Style style) {
      Map<Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>> motionsByStyle = (Map)this.weaponAttackMotions.get(category);
      if (motionsByStyle == null) {
         return null;
      } else {
         CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = (CombatBehaviors.Builder)motionsByStyle.get(style);
         if (builder == null) {
            builder = (CombatBehaviors.Builder)motionsByStyle.get(Styles.COMMON);
         }

         if (builder == null && !motionsByStyle.isEmpty()) {
            builder = (CombatBehaviors.Builder)motionsByStyle.values().iterator().next();
         }

         return builder;
      }
   }

   private String getCurrentWeaponProfileKey(HeroEntity hero) {
      if (!this.isEquipmentReady(hero)) {
         return "missing";
      } else if (!hero.isBattleModeActive()) {
         return "false|out_of_battle";
      } else {
         String var10000 = HeroEpicFightWeaponProfiles.getProfileKey(hero, this);
         return "true|" + var10000;
      }
   }

   private boolean isEquipmentReady(HeroEntity hero) {
      if (hero != null && hero.isAddedToLevel()) {
         try {
            hero.getMainHandItem();
            return true;
         } catch (NullPointerException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private void ensureInfantryAiConfigured() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null && hero.isAddedToLevel()) {
         if (!hero.isBattleModeActive()) {
            this.removeHeroCombatGoals();
            this.infantryAiConfigured = false;
         } else if (!this.infantryAiConfigured) {
            this.setAIAsInfantry(this.isRangedWeaponEquipped());
            this.infantryAiConfigured = true;
         }
      }
   }

   boolean isRangedWeaponEquipped() {
      return HeroEpicFightWeaponProfiles.isRangedLoadout((HeroEntity)this.getOriginal());
   }

   boolean fireCrossbow(LivingEntity target, ItemStack stack) {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      return HeroCombatWeaponHelper.fireCrossbow(hero, target, stack);
   }

   private void removeHeroCombatGoals() {
      HeroEntity hero = (HeroEntity)this.getOriginal();
      if (hero != null) {
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

   private static record PlayerLikeAttackProfile(List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> comboAnimations, List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> dashAnimations, List<AnimationManager.AnimationAccessor<? extends StaticAnimation>> airAnimations, List<HeroCombatPlanner.ActionProfile> comboActionProfiles, List<HeroCombatPlanner.ActionProfile> dashActionProfiles, List<HeroCombatPlanner.ActionProfile> airActionProfiles, double comboMaxDistance, double dashMinDistance, double dashMaxDistance, double airMaxDistance) {
   }
}
