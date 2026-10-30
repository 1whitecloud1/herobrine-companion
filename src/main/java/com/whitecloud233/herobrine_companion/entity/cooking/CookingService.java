package com.whitecloud233.herobrine_companion.entity.cooking;

import com.whitecloud233.herobrine_companion.util.DebugLog;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroInvitationHelper;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public final class CookingService {
    public static final int INVITED_ACTION_COOK = CookingRules.INVITED_ACTION_COOK;
    public static final int INVITED_ACTION_MILLSTONE = CookingRules.INVITED_ACTION_MILLSTONE;
    /** 磨盘转一圈的 tick 数；客户端推磨步频按它算，和服务端转速同源。 */
    public static final float MILLSTONE_ROT_SPEED_TICK = CookingRules.MILLSTONE_ROT_SPEED_TICK;
    public static final int MIN_COOK_REPEAT_COUNT = CookingRules.MIN_COOK_REPEAT_COUNT;
    public static final int MAX_COOK_REPEAT_COUNT = CookingRules.MAX_COOK_REPEAT_COUNT;
    private static final String AUTO_COOK_POS_KEY = "HeroAutoCookPos";
    private static final String AUTO_COOK_TICKS_KEY = "HeroAutoCookTicks";
    private static final String AUTO_COOK_RESULT_KEY = "HeroAutoCookResult";
    private static final String AUTO_COOK_RECIPE_ID_KEY = "HeroAutoCookRecipeId";
    private static volatile CookingBackendRegistry backends = new CookingBackendRegistry(List.of());
    private static final String AUTO_EAT_TICKS_KEY = "HeroAutoEatTicks";
    private static final int AUTO_EAT_DURATION_TICKS = 36;

    /**
     * 临时排查埋点（用于定位「邀请做菜后 Hero 不干活」）：同一 key 的状态串发生变化时才输出一行 INFO，
     * 输出量极小。问题定位完成后整体删除。统一前缀 [CookDiag] 便于 grep。
     */
    private static final org.slf4j.Logger DIAG = org.slf4j.LoggerFactory.getLogger("HeroCookDiag");
    private static final java.util.Map<String, String> DIAG_STATE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void diag(String key, String state) {
        if (!DebugLog.enabled()) {
            return;
        }
        if (state.equals(DIAG_STATE.put(key, state))) {
            return;
        }
        DebugLog.info(DIAG, "[CookDiag] {} :: {}", key, state);
    }

    private CookingService() {
    }

    public static void installBackends(CookingBackendRegistry registry) {
        backends = java.util.Objects.requireNonNull(registry);
    }

    private static java.util.Optional<CookingBackend> backend(HeroEntity hero, BlockPos pos) {
        return hero == null ? java.util.Optional.empty() : backends.find(hero.level(), pos);
    }

    public static boolean isCookwareStation(Level level, BlockPos pos) {
        return backends.find(level, pos).isPresent();
    }

    /** 石磨不算锅具，单独判定，避免把做菜选项流程套到拉磨上。 */
    public static boolean isMillstoneStation(Level level, BlockPos pos) {
        return millstoneBackend(level, pos) != null;
    }

    @Nullable
    private static CookingBackend millstoneBackend(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        for (CookingBackend backend : backends.all()) {
            if (backend.supportsMillstone(level, pos)) {
                return backend;
            }
        }
        return null;
    }

    public static BlockPos millstoneCenter(Level level, BlockPos pos) {
        CookingBackend backend = millstoneBackend(level, pos);
        return backend == null || level == null || pos == null ? pos : backend.millstoneCenter(level, pos);
    }

    /** 石磨上已经有别的生物在拉磨时不能再邀请；自己已绑定则继续。 */
    public static boolean isMillstoneBusy(HeroEntity hero, BlockPos pos) {
        CookingBackend backend = millstoneBackend(hero == null ? null : hero.level(), pos);
        return backend != null && backend.isMillstoneBusy(hero, pos);
    }

    /**
     * 邀请前的可用性判定。石磨的绑定规则由厨具模组掌握（生物类型是否在
     * {@code millstone_bindable} 标签里、是否已驯服、是否被人骑着等），
     * 这里只取它的结论，不在本模组里复制一套。
     */
    public static boolean canHostMillstone(HeroEntity hero, BlockPos pos) {
        CookingBackend backend = millstoneBackend(hero == null ? null : hero.level(), pos);
        return backend != null && backend.canHostMillstone(hero, pos);
    }

    public static List<MillstoneOption> getMillstoneOptions(HeroEntity hero, BlockPos pos) {
        CookingBackend backend = millstoneBackend(hero == null ? null : hero.level(), pos);
        return backend == null ? List.of() : backend.millstoneOptions(hero, pos);
    }

    public static int placeMillstoneInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) {
        CookingBackend backend = millstoneBackend(hero == null ? null : hero.level(), pos);
        return backend == null ? 0 : backend.placeMillstoneInput(hero, pos, recipeId, count);
    }

    /**
     * 石磨是否还有活可干：还有材料在磨、或者磨完的产物还没被取走。
     * 玩家选了「磨 N 份」之后，Hero 要靠这个判断什么时候收工。
     */
    public static boolean isMillstoneWorking(HeroEntity hero, BlockPos pos) {
        CookingBackend backend = millstoneBackend(hero == null ? null : hero.level(), pos);
        return backend != null && backend.isMillstoneWorking(hero, pos);
    }

    public static boolean tickMillstone(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        CookingBackend backend = millstoneBackend(hero.level(), pos);
        return backend != null && backend.tickMillstone(hero, pos);
    }

    public static void releaseMillstone(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return;
        }
        for (CookingBackend backend : backends.all()) {
            backend.releaseMillstone(hero, pos);
        }
        hero.setMillstoneBound(false);
    }

    public static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || hero.level().isClientSide) return false;
        if (isAutonomousCookingActive(hero, pos)) {
            boolean autonomousResult = tickAutonomousCooking(hero, pos);
            diag("service.tick", "branch=AUTONOMOUS result=" + autonomousResult);
            return autonomousResult;
        }
        java.util.Optional<CookingBackend> resolved = backend(hero, pos);
        boolean result = resolved.map(value -> value.tick(hero, pos)).orElse(false);
        diag("service.tick", "branch=normal backend=" + resolved.map(CookingBackend::id).orElse("NONE")
                + " result=" + result + " pos=" + pos.toShortString());
        return result;
    }

    public static ItemStack getCookMainHandDisplay(HeroEntity hero) {
        return backend(hero, hero == null ? null : hero.getInvitedPos())
                .map(value -> value.mainHandDisplay(hero)).orElse(ItemStack.EMPTY);
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        return backend(hero, hero == null ? null : hero.getInvitedPos())
                .map(value -> value.offHandDisplay(hero)).orElse(ItemStack.EMPTY);
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        backend(hero, pos).ifPresent(value -> value.resetSelection(hero, pos));
    }

    public static List<CookOption> getCookOptions(HeroEntity hero, BlockPos pos) {
        return backend(hero, pos).map(value -> List.copyOf(value.options(hero, pos))).orElse(List.of());
    }

    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        return backend(hero, pos).map(value -> value.select(hero, pos, recipeId)).orElse(false);
    }

    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        return backend(hero, pos).map(value -> value.maxRepeat(hero, pos, recipeId)).orElse(0);
    }

    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        return backend(hero, pos).map(value -> value.cycleSelection(hero, pos))
                .orElseGet(() -> Component.translatable("message.herobrine_companion.invite_cook_none"));
    }

    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        backend(hero, pos).ifPresent(value -> value.setRepeatCount(hero, pos, clampCookRepeatCount(count)));
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        backend(hero, pos).ifPresent(value -> value.beginChunkTicket(hero, pos));
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        for (CookingBackend backend : backends.all()) backend.endChunkTicket(hero);
    }

    public static boolean requestCookSelection(HeroEntity hero, ServerPlayer player, BlockPos pos,
                                               ResourceLocation recipeId, int repeatCount) {
        if (hero == null || player == null || pos == null || recipeId == null
                || !(hero.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        boolean boundContract = false;
        for (ItemStack stack : player.getHandSlots()) {
            if (stack.getItem() instanceof HeroSummonItem
                    && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("BoundHero")
                    && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(HeroSummonItem.getOwnerUUID(stack))) {
                boundContract = true;
                break;
            }
        }
        if (!CookingRules.canManage(player.getUUID(), hero.getOwnerUUID(), boundContract,
                hero.isAlive() && !hero.isRemoved() && player.isAlive(), hero.level() == player.level())) {
            player.sendSystemMessage(hero.createNotYourHeroMessage());
            return false;
        }
        if (!level.hasChunkAt(pos)) return false;
        CookingBackend backend = backend(hero, pos).orElse(null);
        if (backend == null) {
            diag("invite", "FAIL noBackend pos=" + pos.toShortString());
            return false;
        }
        int maxRepeat = backend.maxRepeat(hero, pos, recipeId);
        if (maxRepeat <= 0) {
            diag("invite", "FAIL maxRepeat=0 recipe=" + recipeId + " backend=" + backend.id()
                    + " options=" + backend.options(hero, pos).size());
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cook_none"));
            return false;
        }
        if (hero.getPersistentData().contains(AUTO_COOK_POS_KEY)) clearAutonomousCooking(hero);
        if (!backend.select(hero, pos, recipeId)) {
            diag("invite", "FAIL selectFailed recipe=" + recipeId + " options=" + backend.options(hero, pos).size());
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cook_none"));
            return false;
        }
        endCookChunkTicket(hero);
        backend.setRepeatCount(hero, pos, Math.min(clampCookRepeatCount(repeatCount), maxRepeat));
        hero.setInvitedPos(pos);
        hero.setInvitedAction(INVITED_ACTION_COOK);
        diag("invite", "OK recipe=" + recipeId + " maxRepeat=" + maxRepeat + " pos=" + pos.toShortString());
        backend.beginChunkTicket(hero, pos);
        hero.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cook_" + (hero.getRandom().nextInt(3) + 1)));
        return true;
    }

    public static int clampCookRepeatCount(int count) { return CookingRules.clampRepeatCount(count); }
    /**
     * 玩家在石磨界面里选好「磨什么、磨几份」之后的落地流程：
     * 校验权限 → 扣背包材料并投进石磨 → 把 Hero 派去拉磨。
     *
     * <p>投料发生在派活之前：投不进去就不派活，Hero 不会空转磨盘。
     */
    public static boolean requestMillstoneTask(HeroEntity hero, ServerPlayer player, BlockPos pos,
                                               ResourceLocation recipeId, int count) {
        if (hero == null || player == null || pos == null || recipeId == null
                || !(hero.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        boolean boundContract = false;
        for (ItemStack stack : player.getHandSlots()) {
            if (stack.getItem() instanceof HeroSummonItem
                    && stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).contains("BoundHero")
                    && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(HeroSummonItem.getOwnerUUID(stack))) {
                boundContract = true;
                break;
            }
        }
        if (!CookingRules.canManage(player.getUUID(), hero.getOwnerUUID(), boundContract,
                hero.isAlive() && !hero.isRemoved() && player.isAlive(), hero.level() == player.level())) {
            player.sendSystemMessage(hero.createNotYourHeroMessage());
            return false;
        }

        BlockPos center = millstoneCenter(level, pos);
        if (!level.hasChunkAt(center) || !isMillstoneStation(level, center)) {
            return false;
        }
        if (isMillstoneBusy(hero, center) || !canHostMillstone(hero, center)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.herobrine_companion.invite_millstone_busy"));
            return false;
        }

        int placed = placeMillstoneInput(hero, center, recipeId, count);
        if (placed <= 0) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.herobrine_companion.invite_millstone_no_input"));
            return false;
        }

        BlockPos previous = hero.getInvitedPos();
        if (previous != null && hero.getInvitedAction() == INVITED_ACTION_MILLSTONE && !previous.equals(center)) {
            releaseMillstone(hero, previous);
            endCookChunkTicket(hero);
        }

        hero.setInvitedPos(center);
        hero.setInvitedAction(INVITED_ACTION_MILLSTONE);
        hero.setMillstoneBound(false);
        beginCookChunkTicket(hero, center);
        hero.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.herobrine_companion.invite_millstone_" + (hero.getRandom().nextInt(3) + 1)));
        return true;
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
        Tag savedResult = result.saveOptional(hero.registryAccess());
        if (!(savedResult instanceof CompoundTag savedResultTag)) {
            return false;
        }
        data.putLong(AUTO_COOK_POS_KEY, pos.asLong());
        data.putInt(AUTO_COOK_TICKS_KEY, 90 + hero.getRandom().nextInt(80));
        data.put(AUTO_COOK_RESULT_KEY, savedResultTag);
        if (choice.recipeId() != null) {
            data.putString(AUTO_COOK_RECIPE_ID_KEY, choice.recipeId().toString());
        } else {
            data.remove(AUTO_COOK_RECIPE_ID_KEY);
        }
        new CookingDisplaySnapshot(choice.backendId(), choice.snapshot()).write(data);
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
        if (hero.level().isClientSide) return;
        if (data.contains(AUTO_COOK_POS_KEY)) {
            BlockPos station = BlockPos.of(data.getLong(AUTO_COOK_POS_KEY));
            CookingDisplaySnapshot snapshot = CookingDisplaySnapshot.read(data);
            if (!snapshot.data().isEmpty()) {
                backends.byId(snapshot.backendId()).ifPresent(backend ->
                        backend.restoreAutonomousDisplay(hero.level(), station, snapshot.data()));
            }
            for (CookingBackend backend : backends.all()) {
                if (backend.restoreLegacyDisplay(hero.level(), station, data)) break;
            }
        }
        data.remove(AUTO_COOK_POS_KEY);
        data.remove(AUTO_COOK_TICKS_KEY);
        data.remove(AUTO_COOK_RESULT_KEY);
        data.remove(AUTO_COOK_RECIPE_ID_KEY);
        CookingDisplaySnapshot.clear(data);
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

    private record AutonomousCookChoice(ItemStack result, @Nullable ResourceLocation recipeId,
                                        String backendId, CompoundTag snapshot) {
    }

    private static List<CookOption> autonomousOptions(CookingBackend backend, Level level, BlockPos pos) {
        return backend.autonomousOptions(level, pos).stream()
                .filter(option -> option != null && !option.result().isEmpty() && option.result().has(DataComponents.FOOD)).toList();
    }

    private static List<ItemStack> getAutonomousCookResults(HeroEntity hero, BlockPos pos) {
        return backend(hero, pos).map(value -> autonomousOptions(value, hero.level(), pos).stream()
                .map(CookOption::result).toList()).orElse(List.of());
    }

    @Nullable
    private static AutonomousCookChoice selectAutonomousCookChoice(HeroEntity hero, BlockPos pos) {
        CookingBackend backend = backend(hero, pos).orElse(null);
        if (backend == null) return null;
        List<CookOption> options = autonomousOptions(backend, hero.level(), pos);
        if (options.isEmpty()) return null;
        CookOption choice = options.get(hero.getRandom().nextInt(options.size()));
        CompoundTag snapshot = backend.beginAutonomousDisplay(hero.level(), pos, choice);
        return new AutonomousCookChoice(choice.result(), choice.recipeId(), backend.id(), snapshot);
    }

    private static boolean tickAutonomousCooking(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!isCookwareStation(hero.level(), pos)) {
            clearAutonomousCooking(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);
            return false;
        }

        CompoundTag data = hero.getPersistentData();
        ItemStack cookedResult = ItemStack.parse(hero.registryAccess(), data.getCompound(AUTO_COOK_RESULT_KEY)).orElse(ItemStack.EMPTY);
        if (cookedResult.isEmpty()) {
            clearAutonomousCooking(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);
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
        hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);
        return false;
    }

    private static void finishAutonomousCooking(HeroEntity hero, ServerLevel level, ItemStack cookedResult) {
        Monster nearbyMonster = findNearbyMonster(hero);
        ServerPlayer owner = resolveOwner(hero, level);
        boolean ownerNearby = owner != null && owner.level() == hero.level() && owner.distanceToSqr(hero) <= 100.0D;
        CookingRules.SharingWeights weights = CookingRules.sharingWeights(hero.getTrustLevel(), ownerNearby, nearbyMonster != null);
        switch (weights.choose(hero.getRandom().nextInt(weights.total()))) {
            case HERO -> {
                startEatAnimation(hero, cookedResult);
                playEatFeedback(hero, level, cookedResult);
            }
            case MONSTER -> shareWithMonster(hero, level, nearbyMonster, cookedResult);
            case OWNER -> giveToOwner(hero, owner, cookedResult);
        }
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
