package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.compat.farmersdelight.HeroFarmersDelightCompat;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class HeroCookingCompat {
    public static final int INVITED_ACTION_COOK = 4;
    public static final int MIN_COOK_REPEAT_COUNT = 1;
    public static final int MAX_COOK_REPEAT_COUNT = 64;
    private static final String AUTO_COOK_POS_KEY = "HeroAutoCookPos";
    private static final String AUTO_COOK_TICKS_KEY = "HeroAutoCookTicks";
    private static final String AUTO_COOK_RESULT_KEY = "HeroAutoCookResult";
    private static final String AUTO_COOK_RECIPE_ID_KEY = "HeroAutoCookRecipeId";
    private static final String AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY = "HeroAutoCookKaleidoscopeSnapshot";
    private static final String AUTO_EAT_TICKS_KEY = "HeroAutoEatTicks";
    private static final int AUTO_EAT_DURATION_TICKS = 36;

    private HeroCookingCompat() {
    }

    public static boolean isCookwareStation(Level level, BlockPos pos) {
        return HeroFarmersDelightCompat.isCookwareStation(level, pos)
                || HeroKaleidoscopeCompat.isCookwareStation(level, pos);
    }

    public static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (isAutonomousCookingActive(hero, pos)) {
            return tickAutonomousCooking(hero, pos);
        }
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.tickCookware(hero, pos);
        }
        return HeroKaleidoscopeCompat.tickCookware(hero, pos);
    }

    public static ItemStack getCookMainHandDisplay(HeroEntity hero) {
        if (hero == null || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), hero.getInvitedPos())) {
            return HeroFarmersDelightCompat.getCookMainHandDisplay(hero);
        }
        return HeroKaleidoscopeCompat.getCookMainHandDisplay(hero);
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (hero == null || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), hero.getInvitedPos())) {
            return HeroFarmersDelightCompat.getCookOffhandDisplay(hero);
        }
        return HeroKaleidoscopeCompat.getCookOffhandDisplay(hero);
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.resetCookSelection(hero, pos);
            return;
        }
        HeroKaleidoscopeCompat.resetCookSelection(hero, pos);
    }

    public static List<CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.getCookOptions(hero, pos);
        }
        return HeroKaleidoscopeCompat.getCookOptions(hero, pos).stream()
                .map(option -> new CookOptionView(option.recipeId(), option.result(), option.maxRepeatCount()))
                .toList();
    }

    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.selectCookOption(hero, pos, recipeId);
        }
        return HeroKaleidoscopeCompat.selectCookOption(hero, pos, recipeId);
    }

    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.getCookOptionMaxRepeat(hero, pos, recipeId);
        }
        return HeroKaleidoscopeCompat.getCookOptionMaxRepeat(hero, pos, recipeId);
    }

    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.cycleCookSelection(hero, pos);
        }
        return HeroKaleidoscopeCompat.cycleCookSelection(hero, pos);
    }

    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.setCookRepeatCount(hero, pos, count);
            return;
        }
        HeroKaleidoscopeCompat.setCookRepeatCount(hero, pos, count);
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.beginCookChunkTicket(hero, pos);
            return;
        }
        HeroKaleidoscopeCompat.beginCookChunkTicket(hero, pos);
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        HeroFarmersDelightCompat.endCookChunkTicket(hero);
        HeroKaleidoscopeCompat.endCookChunkTicket(hero);
    }

    public static int clampCookRepeatCount(int count) {
        return Math.max(MIN_COOK_REPEAT_COUNT, Math.min(MAX_COOK_REPEAT_COUNT, count));
    }

    public static boolean hasAutonomousCookOptions(HeroEntity hero, BlockPos pos) {
        return !getAutonomousCookResults(hero, pos).isEmpty();
    }

    public static boolean beginAutonomousCooking(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }

        clearAutonomousCooking(hero);
        AutonomousCookChoice choice = selectAutonomousCookChoice(hero, pos);
        if (choice == null || choice.result().isEmpty()) {
            return false;
        }

        ItemStack result = choice.result().copy();
        CompoundTag data = hero.getPersistentData();
        data.putLong(AUTO_COOK_POS_KEY, pos.asLong());
        data.putInt(AUTO_COOK_TICKS_KEY, 90 + hero.getRandom().nextInt(80));
        data.put(AUTO_COOK_RESULT_KEY, result.save(new CompoundTag()));
        if (choice.recipeId() != null) {
            data.putString(AUTO_COOK_RECIPE_ID_KEY, choice.recipeId().toString());
        } else {
            data.remove(AUTO_COOK_RECIPE_ID_KEY);
        }
        if (choice.kaleidoscopeSnapshot() != null && !choice.kaleidoscopeSnapshot().isEmpty()) {
            data.put(AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY, choice.kaleidoscopeSnapshot().copy());
        } else {
            data.remove(AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY);
        }
        data.remove(AUTO_EAT_TICKS_KEY);
        hero.stopUsingItem();
        hero.setVisualEatingActive(false);
        hero.setVisualMainHandItem(ItemStack.EMPTY);
        hero.setVisualOffHandItem(result);
        HeroDialogueHandler.onAutonomousCookingStart(hero, result);
        return true;
    }

    public static boolean isAutonomousCookingActive(HeroEntity hero, @Nullable BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }
        CompoundTag data = hero.getPersistentData();
        return data.contains(AUTO_COOK_RESULT_KEY)
                && data.contains(AUTO_COOK_TICKS_KEY)
                && data.getLong(AUTO_COOK_POS_KEY) == pos.asLong();
    }

    public static void clearAutonomousCooking(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        if (data.contains(AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY) && data.contains(AUTO_COOK_POS_KEY)) {
            HeroKaleidoscopeCompat.restoreAutonomousCookwareSnapshot(
                    hero.level(),
                    BlockPos.of(data.getLong(AUTO_COOK_POS_KEY)),
                    data.getCompound(AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY));
        }
        data.remove(AUTO_COOK_POS_KEY);
        data.remove(AUTO_COOK_TICKS_KEY);
        data.remove(AUTO_COOK_RESULT_KEY);
        data.remove(AUTO_COOK_RECIPE_ID_KEY);
        data.remove(AUTO_COOK_KALEIDOSCOPE_SNAPSHOT_KEY);
        hero.setVisualOffHandItem(ItemStack.EMPTY);
    }

    public static void tickVisuals(HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) {
            return;
        }

        CompoundTag data = hero.getPersistentData();
        int remainingEatTicks = Math.max(0, data.getInt(AUTO_EAT_TICKS_KEY));
        if (remainingEatTicks <= 0) {
            if (hero.isVisualEatingActive()) {
                hero.stopUsingItem();
                hero.setVisualEatingActive(false);
                hero.setVisualMainHandItem(ItemStack.EMPTY);
            }
            data.remove(AUTO_EAT_TICKS_KEY);
            return;
        }

        hero.setVisualEatingActive(true);
        if (remainingEatTicks % 6 == 0) {
            hero.swing(InteractionHand.MAIN_HAND);
        }

        remainingEatTicks--;
        if (remainingEatTicks <= 0) {
            data.remove(AUTO_EAT_TICKS_KEY);
            hero.stopUsingItem();
            hero.setVisualEatingActive(false);
            hero.setVisualMainHandItem(ItemStack.EMPTY);
            return;
        }

        data.putInt(AUTO_EAT_TICKS_KEY, remainingEatTicks);
    }

    public record CookOptionView(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) {
    }

    private record AutonomousCookChoice(ItemStack result, @Nullable ResourceLocation recipeId,
                                        @Nullable CompoundTag kaleidoscopeSnapshot) {
    }

    private static List<ItemStack> getAutonomousCookResults(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return List.of();
        }

        List<ItemStack> rawResults;
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            rawResults = HeroFarmersDelightCompat.getAutonomousCookResults(hero.level(), pos);
        } else if (HeroKaleidoscopeCompat.isCookwareStation(hero.level(), pos)) {
            rawResults = HeroKaleidoscopeCompat.getAutonomousCookResults(hero.level(), pos);
        } else {
            rawResults = List.of();
        }

        if (rawResults.isEmpty()) {
            return List.of();
        }

        List<ItemStack> edibleResults = new ArrayList<>();
        for (ItemStack stack : rawResults) {
            if (stack != null && !stack.isEmpty() && stack.isEdible()) {
                edibleResults.add(stack.copy());
            }
        }
        return edibleResults;
    }

    @Nullable
    private static AutonomousCookChoice selectAutonomousCookChoice(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return null;
        }

        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            List<ItemStack> options = getAutonomousCookResults(hero, pos);
            if (options.isEmpty()) {
                return null;
            }
            ItemStack result = options.get(hero.getRandom().nextInt(options.size())).copy();
            return result.isEmpty() ? null : new AutonomousCookChoice(result, null, null);
        }

        if (!HeroKaleidoscopeCompat.isCookwareStation(hero.level(), pos)) {
            List<ItemStack> options = getAutonomousCookResults(hero, pos);
            if (options.isEmpty()) {
                return null;
            }
            ItemStack result = options.get(hero.getRandom().nextInt(options.size())).copy();
            return result.isEmpty() ? null : new AutonomousCookChoice(result, null, null);
        }

        List<HeroKaleidoscopeCompat.AutonomousCookOption> rawOptions = HeroKaleidoscopeCompat.getAutonomousCookOptions(hero.level(), pos);
        List<HeroKaleidoscopeCompat.AutonomousCookOption> edibleOptions = new ArrayList<>();
        for (HeroKaleidoscopeCompat.AutonomousCookOption option : rawOptions) {
            if (option != null && option.result() != null && !option.result().isEmpty() && option.result().isEdible()) {
                edibleOptions.add(new HeroKaleidoscopeCompat.AutonomousCookOption(option.recipeId(), option.result().copy()));
            }
        }
        if (edibleOptions.isEmpty()) {
            return null;
        }

        HeroKaleidoscopeCompat.AutonomousCookOption selected = edibleOptions.get(hero.getRandom().nextInt(edibleOptions.size()));
        ItemStack result = selected.result().copy();
        CompoundTag snapshot = HeroKaleidoscopeCompat.captureAutonomousCookwareSnapshot(hero.level(), pos);
        boolean applied = HeroKaleidoscopeCompat.applyAutonomousCookwareDisplay(hero.level(), pos, selected.recipeId(), result);
        return new AutonomousCookChoice(result, applied ? selected.recipeId() : null, applied ? snapshot : null);
    }

    private static boolean tickAutonomousCooking(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!isCookwareStation(hero.level(), pos)) {
            clearAutonomousCooking(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(0);
            return false;
        }

        CompoundTag data = hero.getPersistentData();
        ItemStack cookedResult = ItemStack.of(data.getCompound(AUTO_COOK_RESULT_KEY));
        if (cookedResult.isEmpty()) {
            clearAutonomousCooking(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(0);
            return false;
        }

        int remainingTicks = Math.max(0, data.getInt(AUTO_COOK_TICKS_KEY) - 1);
        data.putInt(AUTO_COOK_TICKS_KEY, remainingTicks);
        if (remainingTicks > 0) {
            return true;
        }

        clearAutonomousCooking(hero);
        finishAutonomousCooking(hero, serverLevel, cookedResult.copy());
        hero.setInvitedPos(null);
        hero.setInvitedAction(0);
        return false;
    }

    private static void finishAutonomousCooking(HeroEntity hero, ServerLevel level, ItemStack cookedResult) {
        Monster nearbyMonster = findNearbyMonster(hero);
        ServerPlayer owner = resolveOwner(hero, level);
        int trust = hero.getTrustLevel();
        boolean ownerEligible = owner != null && owner.level() == hero.level()
                && owner.distanceToSqr(hero) <= 100.0D && trust >= 20;

        int eatWeight = 3;
        int monsterWeight = nearbyMonster != null ? 2 : 0;
        int playerWeight = ownerEligible ? (trust >= 80 ? 4 : trust >= 50 ? 2 : 1) : 0;
        int totalWeight = eatWeight + monsterWeight + playerWeight;
        int roll = totalWeight <= 0 ? 0 : hero.getRandom().nextInt(totalWeight);

        if (roll < eatWeight) {
            startEatAnimation(hero, cookedResult);
            playEatFeedback(hero, level, cookedResult);
            return;
        }
        roll -= eatWeight;
        if (roll < monsterWeight && nearbyMonster != null) {
            shareWithMonster(hero, level, nearbyMonster, cookedResult);
            return;
        }
        if (ownerEligible) {
            giveToOwner(hero, owner, cookedResult);
            return;
        }

        startEatAnimation(hero, cookedResult);
        playEatFeedback(hero, level, cookedResult);
    }

    @Nullable
    private static ServerPlayer resolveOwner(HeroEntity hero, ServerLevel level) {
        if (hero.getOwnerUUID() == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(hero.getOwnerUUID());
    }

    @Nullable
    private static Monster findNearbyMonster(HeroEntity hero) {
        return hero.level().getEntitiesOfClass(Monster.class, hero.getBoundingBox().inflate(8.0D), Monster::isAlive)
                .stream()
                .min(java.util.Comparator.comparingDouble(hero::distanceToSqr))
                .orElse(null);
    }

    private static void startEatAnimation(HeroEntity hero, ItemStack cookedResult) {
        if (hero == null || cookedResult == null || cookedResult.isEmpty()) {
            return;
        }

        hero.getPersistentData().putInt(AUTO_EAT_TICKS_KEY, AUTO_EAT_DURATION_TICKS);
        hero.setVisualOffHandItem(ItemStack.EMPTY);
        hero.setVisualMainHandItem(cookedResult);
        hero.setVisualEatingActive(true);
        hero.swing(InteractionHand.MAIN_HAND);
    }

    private static void playEatFeedback(HeroEntity hero, ServerLevel level, ItemStack cookedResult) {
        hero.heal(2.0F);
        level.playSound(null, hero.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.55F,
                0.9F + hero.getRandom().nextFloat() * 0.25F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, cookedResult.copyWithCount(1)),
                hero.getX(), hero.getY() + 1.35D, hero.getZ(), 8, 0.2D, 0.15D, 0.2D, 0.02D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                hero.getX(), hero.getY() + 1.2D, hero.getZ(), 4, 0.3D, 0.25D, 0.3D, 0.01D);
    }

    private static void shareWithMonster(HeroEntity hero, ServerLevel level, Monster monster, ItemStack cookedResult) {
        monster.heal(3.0F);
        monster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, true));
        level.playSound(null, monster.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.45F,
                0.85F + hero.getRandom().nextFloat() * 0.3F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, cookedResult.copyWithCount(1)),
                monster.getX(), monster.getY() + 1.0D, monster.getZ(), 10, 0.25D, 0.2D, 0.25D, 0.03D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                monster.getX(), monster.getY() + 1.0D, monster.getZ(), 5, 0.3D, 0.2D, 0.3D, 0.01D);
        if (hero.getOwnerUUID() != null) {
            hero.getHeroBrain().inputMonsterEmpathy(hero.getOwnerUUID(), 0.04f);
        }
    }

    private static void giveToOwner(HeroEntity hero, ServerPlayer owner, ItemStack cookedResult) {
        Vec3 lookVec = owner.position().subtract(hero.position());
        Vec3 motion = lookVec.lengthSqr() > 1.0E-4D ? lookVec.normalize().scale(0.28D) : Vec3.ZERO;
        ItemEntity itemEntity = new ItemEntity(
                hero.level(),
                hero.getX(),
                hero.getY() + 1.0D,
                hero.getZ(),
                cookedResult.copy()
        );
        itemEntity.setDeltaMovement(motion.x, 0.22D, motion.z);
        itemEntity.setDefaultPickUpDelay();
        hero.level().addFreshEntity(itemEntity);
        hero.level().playSound(null, hero.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.0F);
        HeroDialogueHandler.onGift(hero, owner);
    }
}
