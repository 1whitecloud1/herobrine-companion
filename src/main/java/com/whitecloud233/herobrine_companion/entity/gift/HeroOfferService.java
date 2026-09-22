package com.whitecloud233.herobrine_companion.entity.gift;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.herobrine_companion.entity.visual.HeroGestureLibrary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 赠礼总编排 —— Bedrock hero_player_offer_service 的移植。
 *
 * <p>单一职责:串起 入口校验 → 目录分类 → 口味/秘密/请求/生日 → 评估 → 消耗/信任/
 * 档案/回礼/台词/表现 的完整闭环。所有具体能力(评分、口味、秘密、台词、表现、定时任务)
 * 委托给各自的单一职责类,本类只做编排与裁决后的状态写入。
 */
public final class HeroOfferService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HeroOfferService() {
    }

    // 与 Bedrock 常量一致
    public static final double OFFER_DISTANCE_SQ = 64.0D;
    public static final int SHARED_MEAL_WINDOW_TICKS = 240;
    public static final int BIRTHDAY_MONTH = 8;
    public static final int BIRTHDAY_DAY = 30;
    public static final String BIRTHDAY_ITEM = "minecraft:cake";
    public static final double RETURN_GIFT_BASE_CHANCE = 0.12;
    public static final double RETURN_GIFT_REMEMBERED_BONUS = 0.10;
    public static final double RETURN_GIFT_FULFILLED_BONUS = 0.20;
    public static final double RETURN_GIFT_SECRET_BONUS = 0.30;

    private static final String HERO_PROFILE_KEY = "GiftFoodProfile";

    // ------------------------------------------------------------------
    // 档案存取
    // ------------------------------------------------------------------

    public static HeroGiftProfile getHeroProfile(HeroEntity hero) {
        CompoundTag tag = hero.getPersistentData().getCompound(HERO_PROFILE_KEY);
        return HeroGiftProfileStorage.loadHeroProfile(tag);
    }

    public static void setHeroProfile(HeroEntity hero, HeroGiftProfile profile) {
        hero.getPersistentData().put(HERO_PROFILE_KEY, HeroGiftProfileStorage.saveHeroProfile(profile));
    }

    private static HeroPlayerGiftProfile getPlayerProfile(ServerLevel level, UUID uuid) {
        return HeroGiftProfileStorage.loadPlayerProfile(level, uuid);
    }

    private static void storePlayerProfile(ServerLevel level, UUID uuid, HeroPlayerGiftProfile profile) {
        HeroGiftProfileStorage.storePlayerProfile(level, uuid, profile);
    }

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /** UI 动作入口(Bedrock handle_ui_offer_action)。 */
    public static boolean handleUIOfferAction(HeroEntity hero, ServerPlayer player, String action) {
        if (!"offer_carried_item_to_hero".equals(action) && !"offer_food_to_hero".equals(action)) {
            return false;
        }
        return handleOffer(hero, player, "offer_food_to_hero".equals(action));
    }

    /** 潜行 + 手持物品快捷入口(Bedrock handle_direct_interact_offer)。 */
    public static boolean handleDirectInteractOffer(HeroEntity hero, ServerPlayer player) {
        if (!player.isCrouching()) {
            return false;
        }
        if (player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (hero.getOwnerUUID() != null && !hero.getOwnerUUID().equals(player.getUUID())) {
            return false;
        }
        return handleOffer(hero, player, false);
    }

    // ------------------------------------------------------------------
    // 主编排(等价 Bedrock _handle_offer)
    // ------------------------------------------------------------------

    public static boolean handleOffer(HeroEntity hero, ServerPlayer player, boolean foodOnly) {
        if (hero.level().isClientSide || player.level() != hero.level()) {
            return false;
        }
        double distSq = hero.distanceToSqr(player);
        if (distSq > OFFER_DISTANCE_SQ) {
            HeroGiftReactionService.sendLocalLine(hero, player, "too_far", null, 0, null);
            return false;
        }
        long now = hero.level().getGameTime();
        HeroGiftRuntimeState.HeroRuntime runtime = HeroGiftRuntimeState.hero(hero.getUUID());
        if (now < runtime.cooldownUntil) {
            HeroGiftReactionService.sendLocalLine(hero, player, "cooldown", null, 0, null);
            return true;
        }
        int tick = capInt(now);
        ItemStack item = player.getMainHandItem();
        HeroGiftProfile profile = getHeroProfile(hero);
        HeroPlayerGiftProfile playerProfile = getPlayerProfile((ServerLevel) hero.level(), player.getUUID());

        HeroGiftCatalog.Classified classified = HeroGiftCatalog.classify(item);
        boolean isEmpty = "empty".equals(classified.kind());
        int repeatCount;
        if (isEmpty) {
            repeatCount = 1;
        } else if (classified.itemName().equals(profile.lastOfferItemName)) {
            repeatCount = profile.repeatOfferCount + 1;
        } else {
            repeatCount = 1;
        }
        int hunger = player.getFoodData().getFoodLevel();
        boolean isNight = !hero.level().isDay();
        HeroGiftBehaviorContext.Recent recent = HeroGiftBehaviorContext.of(player.getUUID()).counts(tick);
        int tasteLevel;
        if (isEmpty) {
            tasteLevel = 0;
        } else {
            tasteLevel = HeroGiftTaste.levelOf(profile.favoriteSignals, profile.tabooSignals,
                    classified.itemName(), tick);
        }
        HeroGiftOffer offer = new HeroGiftOffer(
                classified.itemName(), classified.kind(), classified.category(),
                item.getCount(), item.getDamageValue(),
                isNamed(item), classified.isFood(),
                hunger <= 6, classified.isFood() && nearFire(hero.level(), player), isNight,
                repeatCount, profile.emptyOfferCount, profile.premiumOfferCount, profile.zenithOfferCount,
                tasteLevel,
                recent.recentViolence(), recent.recentCare(), recent.recentVillageHarm(),
                classified.memory(), classified.warmth(), classified.restraint(), classified.rift(),
                classified.pollution(), classified.suspicion(), classified.valueTier(),
                false, false, false);

        if (foodOnly && !"food".equals(offer.kind())) {
            HeroGiftReactionService.sendLocalLine(hero, player, "food_required", null, 0, null);
            runtime.cooldownUntil = now + 40;
            return true;
        }

        // 秘密喜好:惰性生成,一次定型
        profile.secretFavorites.putAll(HeroGiftSecret.ensureFavorites(profile.secretFavorites, player.getRandom()));

        // 请求:过期/匹配
        boolean expiredRequest = false;
        if (HeroGiftRequest.isExpired(profile.activeRequest, tick)) {
            profile.activeRequest = null;
            expiredRequest = true;
        } else if (HeroGiftRequest.matchRequest(profile.activeRequest, offer)) {
            offer = withFlags(offer, false, true, false);
        }
        boolean requestAsk = offer.valueTier() == 3 && profile.activeRequest == null
                && offer.zenithCount() == 0 && !offer.requestMatch()
                && player.getRandom().nextDouble() < HeroGiftRequest.REQUEST_ASK_CHANCE;
        if (requestAsk) {
            offer = withFlags(offer, true, offer.requestMatch(), false);
        }
        // 心照:递交到未识破的心头好
        String secretBucket = null;
        if (!isEmpty) {
            String bucket = HeroGiftSecret.isFavorite(profile.secretFavorites, offer.itemName());
            if (bucket != null && !profile.discoveredFavorites.contains(bucket)) {
                secretBucket = bucket;
                offer = withFlags(offer, offer.requestAsk(), offer.requestMatch(), true);
            }
        }
        HeroGiftResult result = HeroGiftEvaluator.evaluate(offer);
        if (secretBucket != null && !result.accepted()) {
            secretBucket = null;
            offer = withFlags(offer, offer.requestAsk(), offer.requestMatch(), false);
        }

        // 生日彩蛋:8/30 递交蛋糕且接受
        boolean birthdayHit = result.accepted() && BIRTHDAY_ITEM.equals(offer.itemName()) && isBirthday();
        String birthdayKey = birthdayHit ? birthdayKey() : null;
        boolean birthdayFirst = birthdayKey != null && !birthdayKey.equals(profile.lastBirthdayKey);
        if (birthdayHit && birthdayKey != null) {
            profile.lastBirthdayKey = birthdayKey;
        }
        boolean birthdayAgain = false;
        if (birthdayFirst && birthdayKey != null) {
            String previousYear = String.valueOf(Integer.parseInt(birthdayKey.substring(0, 4)) - 1);
            birthdayAgain = profile.birthdayYears.contains(previousYear);
            if (!profile.birthdayYears.contains(birthdayKey.substring(0, 4))) {
                profile.birthdayYears.add(birthdayKey.substring(0, 4));
                profile.birthdayYears.sort(String::compareTo);
                while (profile.birthdayYears.size() > 8) {
                    profile.birthdayYears.remove(0);
                }
            }
            profile.birthdayCount++;
        }

        // 消耗校验:服务端重读主手,必须与刚才那件一致
        if (result.consume() && !consumeExactCarried(player, item)) {
            HeroGiftReactionService.sendLocalLine(hero, player, "item_changed", null, 0, null);
            return true;
        }

        // 信任
        int trustDelta = result.trustDelta();
        if (birthdayFirst) {
            trustDelta += 3;
        }
        if (secretBucket != null) {
            trustDelta += 1;
        }
        if (trustDelta != 0) {
            hero.increaseTrust(trustDelta);
        }

        // 口味采样
        if (!isEmpty) {
            HeroGiftTaste.updateMemory(profile.favoriteSignals, profile.tabooSignals, offer, result, tick);
        }

        // 情绪:仅接受系,玩家级冷却;心照/生日不掷
        String mood = null;
        if (secretBucket == null && !birthdayHit && result.accepted()
                && now >= runtime.moodCooldownUntil) {
            mood = HeroGiftMood.rollMood(result, offer, player.getRandom());
            if (mood != null) {
                runtime.moodCooldownUntil = now + HeroGiftMood.COOLDOWN_TICKS;
            }
        }

        if ("request_fulfilled".equals(result.code())) {
            profile.activeRequest = null;
            runtime.requestCooldownUntil = now + HeroGiftRequest.REQUEST_EXPIRE_TICKS;
        }

        // 请求生成:接受后低概率;完成冷却期内安静
        if (result.accepted() && profile.activeRequest == null
                && now >= runtime.requestCooldownUntil
                && (requestAsk || player.getRandom().nextDouble() < HeroGiftRequest.REQUEST_GENERATE_CHANCE)) {
            HeroGiftRequest.Request picked = HeroGiftRequest.pickRequest(
                    collectSeenCategories(profile, playerProfile), player.getRandom(), false);
            if (picked != null) {
                profile.activeRequest = new HeroGiftProfile.ActiveRequest(
                        picked.itemName(), picked.category(), picked.label(),
                        tick, tick + HeroGiftRequest.REQUEST_EXPIRE_TICKS);
            }
        }

        // 回礼:谦卑物品,延迟交付
        if (result.accepted() && profile.pendingReturn == null) {
            double chance = RETURN_GIFT_BASE_CHANCE;
            boolean birthdayGift = false;
            if (birthdayFirst) {
                chance = 1.0D;
                birthdayGift = true;
            }
            if ("remembered".equals(result.code())) {
                chance += RETURN_GIFT_REMEMBERED_BONUS;
            } else if ("request_fulfilled".equals(result.code())) {
                chance += RETURN_GIFT_FULFILLED_BONUS;
            }
            if (secretBucket != null) {
                chance += RETURN_GIFT_SECRET_BONUS;
            }
            if ("delighted".equals(mood)) {
                chance *= 2;
            }
            if (player.getRandom().nextDouble() < chance) {
                ItemStack custom = birthdayGift ? birthdayKeepsake() : null;
                HeroGiftReturnService.scheduleItemReturn(profile, offer.category(), player,
                        now, birthdayGift, custom);
            }
        }

        // 台词
        String reactionText;
        String reactionCode;
        String extraHint = null;
        if (birthdayHit) {
            reactionCode = "birthday";
        } else if (secretBucket != null) {
            reactionCode = "secret_hit";
        } else if (expiredRequest) {
            reactionCode = "request_expired";
        } else {
            reactionCode = result.code();
            if ("judged".equals(reactionCode)) {
                extraHint = "这份递交背后是污染与破坏的证据。";
            }
        }
        reactionText = HeroGiftReactionService.sendReaction(hero, player, reactionCode,
                offer.category(), tasteLevel, mood, offer.itemName(), extraHint);

        // 档案记录(record_offer)
        recordOffer(profile, playerProfile, offer, result, tick, reactionText, isEmpty);

        // 心照命中:恩典 + 发现的桶
        if (secretBucket != null && !profile.discoveredFavorites.contains(secretBucket)) {
            profile.discoveredFavorites.add(secretBucket);
            HeroGiftFeedbackService.applyGraceBuff(player,
                    HeroGiftSecret.graceBuffPlan(profile.discoveredFavorites.size()));
            if (profile.discoveredFavorites.size() >= HeroGiftSecret.BUCKETS.size()) {
                HeroGiftReactionService.sendLocalLine(hero, player, "secret_all", null, 0, null);
            }
        }

        // 夜间引路灯(pendingReturn 槽:与回礼互斥,先到先得)
        if (result.pendingReturn() && profile.pendingReturn == null) {
            profile.pendingReturn = new HeroGiftProfile.PendingReturn("night_hint", "", 0, 0, null,
                    false, tick + HERO_PENDING_READY_TICKS, tick + HERO_PENDING_EXPIRE_TICKS, null, 0);
        }

        // 返回物品(最后一口等情况)
        String returnItem = result.returnItemName();
        if (returnItem != null && !returnItem.isBlank()) {
            ItemStack stack = new ItemStack(HeroGiftReturnService.requireItem(returnItem));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, true);
            }
        }

        // 共享餐热窗
        if (result.accepted() && "food".equals(offer.kind())) {
            HeroGiftRuntimeState.setSharedMeal(player.getUUID(),
                    new HeroGiftRuntimeState.SharedMeal(hero.getUUID(), now, now + SHARED_MEAL_WINDOW_TICKS));
        }

        // 模糊线索(见 Bedrock 注释:焦点桶 + 保底)
        if (secretBucket == null && result.accepted() && !requestAsk
                && !offer.requestMatch() && !expiredRequest && !isEmpty) {
            HeroGiftSecret.recordMiss(profile.hintProgress, profile.secretFavorites, offer.itemName());
            boolean forceHint = HeroGiftSecret.guaranteePending(offer.itemName(),
                    profile.secretFavorites, profile.discoveredFavorites, profile.hintedFavorites,
                    profile.hintFocus, profile.hintProgress);
            boolean hintRollReady = now >= runtime.hintCooldownUntil
                    && player.getRandom().nextDouble() < HeroGiftSecret.HINT_CHANCE;
            if (forceHint || hintRollReady) {
                HeroGiftSecret.HintPick hint = HeroGiftSecret.pickHint(offer.itemName(),
                        profile.secretFavorites, profile.discoveredFavorites, player.getRandom(),
                        profile.hintedFavorites, profile.hintProgress, profile.hintFocus);
                if (hint.poolKey() != null) {
                    // 模糊线索:同一次托付的附加句,仅本地显示(不占 LLM 配额)
                    HeroGiftReactionService.sendLocalLine(hero, player, hint.poolKey(), null, 0, null);
                    runtime.hintCooldownUntil = now + HeroGiftSecret.HINT_COOLDOWN_TICKS;
                    if (hint.revealAxis() && hint.targetBucket() != null
                            && !profile.hintedFavorites.contains(hint.targetBucket())) {
                        profile.hintedFavorites.add(hint.targetBucket());
                    }
                    if (hint.targetBucket() != null && !hint.targetBucket().equals(profile.hintFocus)) {
                        profile.hintFocus = hint.targetBucket();
                    }
                }
            }
        }

        runtime.cooldownUntil = now + result.cooldownTicks();

        // 表现:音效/粒子 + 手势
        HeroGiftFeedbackService.playOfferFeedback(hero, player, result, mood);
        playGestureForResult(hero, result, offer, mood, birthdayHit, secretBucket != null);
        HeroGiftFeedbackService.lookAtPlayer(hero, player);

        // 生日第二幕:许愿台词与仪式音效(烛光轨道由 HeroGiftReturnService.tick 驱动)
        if (birthdayFirst) {
            if (birthdayAgain) {
                // 附加句:仅本地显示,避免同一次托付连发多个 LLM 请求
                HeroGiftReactionService.sendLocalLine(hero, player, "birthday_again", null, 0, null);
            }
            HeroGiftReactionService.sendLocalLine(hero, player, "birthday_wish", null, 0, null);
            if (hero.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, hero.getX(), hero.getY() + 1.1D, hero.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.2F);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        hero.getX(), hero.getY() + 1.1D, hero.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
            }
            runtime.birthdayOrbitUntil = now + HeroGiftReturnService.BIRTHDAY_CANDLE_TICKS;
        }

        storePlayerProfile((ServerLevel) hero.level(), player.getUUID(), playerProfile);
        setHeroProfile(hero, profile);
        return true;
    }

    private static final int HERO_PENDING_READY_TICKS = 200;
    private static final int HERO_PENDING_EXPIRE_TICKS = 24000;

    // ------------------------------------------------------------------
    // 共享餐(等价 on_player_eat_food)
    // ------------------------------------------------------------------

    public static void onPlayerEatFood(ServerPlayer player, ItemStack eaten) {
        HeroPlayerGiftProfile playerProfile = getPlayerProfile(player.serverLevel(), player.getUUID());
        playerProfile.lastFoodEatenTick = capInt(player.level().getGameTime());
        playerProfile.lastFoodEatenItemName = HeroGiftCatalog.itemName(eaten);
        HeroGiftRuntimeState.SharedMeal meal = HeroGiftRuntimeState.getSharedMeal(player.getUUID());
        if (meal != null && player.level().getGameTime() <= meal.expireTick()) {
            if (player.serverLevel().getEntity(meal.heroId()) instanceof HeroEntity hero
                    && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                if (playerProfile.lastSharedMealTick < meal.offerTick()) {
                    playerProfile.lastSharedMealTick = capInt(player.level().getGameTime());
                    HeroGiftProfile profile = getHeroProfile(hero);
                    profile.warmthTaste += 1;
                    hero.increaseTrust(1);
                    HeroGiftReactionService.sendReaction(hero, player, "shared_meal", null, 0, null,
                            null, null);
                    setHeroProfile(hero, profile);
                }
            }
        }
        HeroGiftRuntimeState.setSharedMeal(player.getUUID(), null);
        storePlayerProfile(player.serverLevel(), player.getUUID(), playerProfile);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private static HeroGiftOffer withFlags(HeroGiftOffer offer, boolean requestAsk,
                                           boolean requestMatch, boolean secretHit) {
        return new HeroGiftOffer(offer.itemName(), offer.kind(), offer.category(), offer.count(),
                offer.auxValue(), offer.isNamed(), offer.isFood(), offer.playerHungerLow(),
                offer.nearFire(), offer.isNight(), offer.repeatCount(), offer.emptyOfferCount(),
                offer.premiumCount(), offer.zenithCount(), offer.tasteLevel(),
                offer.recentViolence(), offer.recentCare(), offer.recentVillageHarm(),
                offer.memory(), offer.warmth(), offer.restraint(), offer.rift(),
                offer.pollution(), offer.suspicion(), offer.valueTier(),
                requestAsk, requestMatch, secretHit);
    }

    private static boolean consumeExactCarried(ServerPlayer player, ItemStack expected) {
        ItemStack current = player.getMainHandItem();
        if (!HeroGiftCatalog.sameItem(current, expected)) {
            return false;
        }
        if (current.getCount() <= 0) {
            return false;
        }
        current.shrink(1);
        return true;
    }

    private static boolean isNamed(ItemStack stack) {
        return HeroGiftCatalog.hasCustomName(stack);
    }

    /** 食物且玩家附近有火源(营火/火/岩浆)。 */
    private static boolean nearFire(Level level, ServerPlayer player) {
        var pos = player.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    var state = level.getBlockState(pos.offset(dx, dy, dz));
                    if (state.is(Blocks.FIRE) || state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void playGestureForResult(HeroEntity hero, HeroGiftResult result, HeroGiftOffer offer,
                                             String mood, boolean birthdayHit, boolean secretHit) {
        String gestureName;
        if (birthdayHit) {
            gestureName = "birthday_lift";
        } else if (secretHit) {
            gestureName = "secret";
        } else {
            // resolved_gesture 同时覆盖 returned_last_bite/empty_return → give_back 等映射
            gestureName = HeroGestureLibrary.resolveGestureName(
                    result.code(), offer.kind(), offer.tasteLevel(), mood);
        }
        if (gestureName == null || gestureName.isBlank()) {
            LOGGER.debug("[HeroGift] offer result={} -> no gesture (no-animation code)", result.code());
            return;
        }
        HeroClip clip = HeroGestureLibrary.byName(gestureName);
        if (clip == null) {
            return;
        }
        // Bedrock 的 shown_item:接取/递回手势期间,祂手里会举着那件东西
        ItemStack shown = ItemStack.EMPTY;
        if (result.consume() && offer.itemName() != null && !offer.itemName().isBlank()) {
            shown = new ItemStack(HeroGiftReturnService.requireItem(offer.itemName()));
        } else if (result.returnItemName() != null && !result.returnItemName().isBlank()) {
            shown = new ItemStack(HeroGiftReturnService.requireItem(result.returnItemName()));
        }
        if (!shown.isEmpty()) {
            hero.setVisualMainHandItem(shown);
        }
        int clipId = HeroGestureLibrary.clipIdOf(clip);
        LOGGER.debug("[HeroGift] offer result={} -> gesture={} clip={}", result.code(), gestureName, clipId);
        hero.playOfferGesture(clipId);
    }

    private static void recordOffer(HeroGiftProfile profile, HeroPlayerGiftProfile playerProfile,
                                    HeroGiftOffer offer, HeroGiftResult result, int tick,
                                    String reactionText, boolean isEmpty) {
        String itemName = offer.itemName();
        String kind = offer.kind();
        boolean accepted = result.accepted();
        if (isEmpty) {
            profile.emptyOfferCount++;
        } else {
            if (itemName.equals(profile.lastOfferItemName)) {
                profile.repeatOfferCount = Math.max(1, offer.repeatCount());
            } else {
                profile.repeatOfferCount = 1;
            }
            profile.lastOfferItemName = itemName;
        }
        profile.lastOfferTick = tick;
        profile.lastReaction = reactionText;
        profile.lastReactionCode = result.code();
        if (offer.valueTier() == 2) {
            profile.premiumOfferCount++;
        } else if (offer.valueTier() == 3) {
            profile.zenithOfferCount++;
        }
        profile.memoryTaste = Math.max(0, profile.memoryTaste + result.tasteDelta().memoryTaste());
        profile.warmthTaste = Math.max(0, profile.warmthTaste + result.tasteDelta().warmthTaste());
        profile.restraintTaste = Math.max(0, profile.restraintTaste + result.tasteDelta().restraintTaste());
        profile.pollutionTaste = Math.max(0, profile.pollutionTaste + result.tasteDelta().pollutionTaste());
        profile.trustTaste = Math.max(0, profile.trustTaste + result.trustDelta());
        if ("food".equals(kind)) {
            if ("cooked".equals(offer.category())) {
                playerProfile.recentCookedFoodGiven++;
            } else if ("raw".equals(offer.category())) {
                playerProfile.recentRawFoodGiven++;
            } else if ("rotten".equals(offer.category())) {
                playerProfile.recentRottenFoodGiven++;
            }
        }
        if (offer.isNamed()) {
            playerProfile.recentNamedGiftGiven++;
        }
        playerProfile.recentKillCountBeforeGift = offer.recentViolence();
        playerProfile.recentVillageHarmBeforeGift = offer.recentVillageHarm();

        HeroGiftProfile.HistoryEntry entry = new HeroGiftProfile.HistoryEntry(
                tick, itemName, kind, offer.category(), result.code());
        if (accepted) {
            if ("gift".equals(kind)) {
                profile.totalGiftCount++;
            } else if ("food".equals(kind)) {
                profile.totalFoodCount++;
            }
            appendHistory(profile.acceptedHistory, entry, 12);
            playerProfile.lastAcceptedOfferTick = tick;
        } else {
            appendHistory(profile.rejectedHistory, entry, 12);
        }
        appendHistory(playerProfile.giftMemoryLedger, entry, 16);
    }

    private static void appendHistory(java.util.List<HeroGiftProfile.HistoryEntry> history,
                                      HeroGiftProfile.HistoryEntry entry, int limit) {
        history.add(entry);
        while (history.size() > limit) {
            history.remove(0);
        }
    }

    private static Set<String> collectSeenCategories(HeroGiftProfile profile,
                                                     HeroPlayerGiftProfile playerProfile) {
        Set<String> seen = new HashSet<>();
        for (HeroGiftProfile.HistoryEntry e : profile.acceptedHistory) {
            if (!e.category().isBlank()) {
                seen.add(e.category());
            }
        }
        for (HeroGiftProfile.HistoryEntry e : playerProfile.giftMemoryLedger) {
            if (!e.category().isBlank()) {
                seen.add(e.category());
            }
        }
        return seen;
    }

    public static boolean isBirthday() {
        LocalDate date = LocalDate.now();
        return date.getMonthValue() == BIRTHDAY_MONTH && date.getDayOfMonth() == BIRTHDAY_DAY;
    }

    public static String birthdayKey() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static int capInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /** 生日回礼:Bedrock 的 herobrine_companion:birthday_cake(无名之蛋糕)本体。 */
    private static ItemStack birthdayKeepsake() {
        return new ItemStack(com.whitecloud233.herobrine_companion.init.ModItems.BIRTHDAY_CAKE.get());
    }
}