package com.whitecloud233.modid.herobrine_companion.entity.gift;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroGestureLibrary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * 赠礼定时任务 —— 延迟回礼交付、夜间引路灯、心照眷顾与生日烛光(服务端每 tick 快路径)。
 *
 * <p>单一职责:消费 HeroGiftProfile.pendingReturn(与 hero runtime 的烛轨/恩典节拍),
 * 到点执行交付/摆放/清除并反馈。无轨道/无待交付时零开销。
 */
public final class HeroGiftReturnService {

    private HeroGiftReturnService() {
    }

    public static final int ITEM_RETURN_READY_TICKS = 300;
    public static final int ITEM_RETURN_EXPIRE_TICKS = 24000;
    public static final int NIGHT_HINT_LIFETIME_TICKS = 100;
    public static final int BIRTHDAY_CANDLE_TICKS = 240;

    /** 安排一次延迟回礼(Bedrock schedule_item_return)。 */
    public static void scheduleItemReturn(HeroGiftProfile profile, String category, ServerPlayer player,
                                          long tick, boolean birthday, ItemStack customGift) {
        if (profile.pendingReturn != null) {
            return;
        }
        ItemStack gift = customGift;
        if (gift == null) {
            String itemName = HeroGiftCatalog.pickReturnGift(category, player.getRandom());
            gift = new ItemStack(requireItem(itemName));
        }
        profile.pendingReturn = new HeroGiftProfile.PendingReturn(
                "item_return",
                itemRegistryName(gift),
                gift.getCount(),
                gift.getDamageValue(),
                gift.hasCustomHoverName() ? gift.getHoverName().getString() : null,
                birthday,
                (int) tick + ITEM_RETURN_READY_TICKS,
                (int) tick + ITEM_RETURN_EXPIRE_TICKS,
                null, 0);
    }

    /** 服务端每 tick(挂 HeroEntity.tick 服务端分支)。 */
    public static void tick(HeroEntity hero) {
        ServerLevel level = (ServerLevel) hero.level();
        UUID heroUuid = hero.getUUID();
        long tick = level.getGameTime();
        if (tick > Integer.MAX_VALUE) {
            tick = Integer.MAX_VALUE;
        }
        int now = (int) tick;
        HeroGiftProfile profile = HeroOfferService.getHeroProfile(hero);

        // 手势收尾:服务端时间基(hero.tickCount)判断窗口是否结束,清 ID 与展示手持物,
        // 避免下一 tick 的手势 ID 与 visual 手持物残留
        if (hero.isOfferGestureActive()) {
            int elapsed = hero.tickCount - hero.getOfferGestureStartTick();
            if (elapsed < 0 || elapsed >= hero.getOfferGestureDurationTicks()) {
                hero.clearOfferGestureServer();
            }
        }

        // 生日烛光:到点结束(粒子本身由 tick 驱动)
        HeroGiftRuntimeState.HeroRuntime runtime = HeroGiftRuntimeState.hero(heroUuid);
        if (runtime.birthdayOrbitUntil > now) {
            HeroGiftFeedbackService.spawnBirthdayCandleParticles(level, hero);
        }

        tickGraceBlessing(hero, level, profile, runtime, now);

        HeroGiftProfile.PendingReturn pending = profile.pendingReturn;
        if (pending == null) {
            return;
        }
        switch (pending.type()) {
            case "night_hint_active" -> {
                if (now >= pending.cleanupTick()) {
                    HeroGiftFeedbackService.removeNightHint(level, pending.hintPos());
                    profile.pendingReturn = null;
                    commit(hero, profile);
                }
            }
            case "item_return" -> {
                if (now < pending.readyTick()) {
                    return;
                }
                ServerPlayer owner = resolveOwner(level, hero);
                if (owner == null) {
                    return;
                }
                ItemStack item = buildReturnItem(pending);
                if (item.isEmpty() || !owner.getInventory().add(item)) {
                    if (!item.isEmpty()) {
                        owner.drop(item, true);
                    }
                }
                String code = pending.birthday() ? "birthday_return" : "return_item";
                HeroGiftReactionService.sendReaction(hero, owner, code, "default", 0, null,
                        itemRegistryName(item), null);
                // 温柔递回手势
                HeroClip clip = HeroGestureLibrary.byName("give_back");
                if (clip != null) {
                    hero.playOfferGesture(HeroGestureLibrary.clipIdOf(clip));
                }
                HeroGiftFeedbackService.lookAtPlayer(hero, owner);
                profile.lastReactionCode = code;
                profile.pendingReturn = null;
                commit(hero, profile);
            }
            default -> {
                // night_hint(尚未激活):夜里且就绪 → 放置;过期清除
                if (now >= pending.expireTick()) {
                    profile.pendingReturn = null;
                    commit(hero, profile);
                    return;
                }
                if (now < pending.readyTick() || level.isDay()) {
                    return;
                }
                ServerPlayer owner = resolveOwner(level, hero);
                if (owner == null) {
                    return;
                }
                String pos = HeroGiftFeedbackService.placeNightHint(level, owner);
                if (pos == null) {
                    return;
                }
                HeroGiftReactionService.sendReaction(hero, owner, "night_return", "default", 0, null,
                        null, null);
                profile.pendingReturn = new HeroGiftProfile.PendingReturn(
                        "night_hint_active", "", 0, 0, null, false,
                        pending.readyTick(), pending.expireTick(), pos, now + NIGHT_HINT_LIFETIME_TICKS);
                commit(hero, profile);
            }
        }
    }

    /** 心照眷顾:四桶全破后,玩家在旁且附近有怪物时给予常驻效果套(防抖 60 秒)。 */
    private static void tickGraceBlessing(HeroEntity hero, ServerLevel level, HeroGiftProfile profile,
                                          HeroGiftRuntimeState.HeroRuntime runtime, int now) {
        if (profile.discoveredFavorites.size() < HeroGiftSecret.BUCKETS.size()) {
            return;
        }
        if (hero.getMindState() == com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState.PROTECTOR) {
            return;
        }
        if (now < runtime.nightGraceCooldownUntil) {
            return;
        }
        UUID ownerUuid = hero.getOwnerUUID();
        if (ownerUuid == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null || owner.level() != level || owner.distanceToSqr(hero) > 24.0D * 24.0D) {
            return;
        }
        boolean nearbyMonster = !level.getEntitiesOfClass(Mob.class,
                owner.getBoundingBox().inflate(16.0D),
                m -> m != hero && m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
        if (!nearbyMonster) {
            return;
        }
        HeroGiftFeedbackService.applyNightGrace(owner);
        runtime.nightGraceCooldownUntil = now + HeroGiftSecret.NIGHT_GRACE_REAPPLY_SECONDS * 20L;
        if (!runtime.nightGraceBlessed) {
            runtime.nightGraceBlessed = true;
            HeroGiftReactionService.sendReaction(hero, owner, "grace_blessing", "default", 0, null,
                    null, null);
        }
    }

    private static ServerPlayer resolveOwner(ServerLevel level, HeroEntity hero) {
        UUID ownerUuid = hero.getOwnerUUID();
        if (ownerUuid == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    private static ItemStack buildReturnItem(HeroGiftProfile.PendingReturn pending) {
        ItemStack stack = new ItemStack(requireItem(pending.itemName()), pending.count());
        if (pending.aux() > 0) {
            stack.setDamageValue(pending.aux());
        }
        if (pending.customName() != null && !pending.customName().isBlank()) {
            stack.setHoverName(net.minecraft.network.chat.Component.literal(pending.customName()));
        }
        return stack;
    }

    /** 按注册名取物品;未知名/空气回退面包。 */
    public static net.minecraft.world.item.Item requireItem(String itemName) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(itemName);
        if (key != null) {
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(key);
            if (item != null && item != Items.AIR) {
                return item;
            }
        }
        return Items.BREAD;
    }

    private static String itemRegistryName(ItemStack stack) {
        net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static void commit(HeroEntity hero, HeroGiftProfile profile) {
        HeroOfferService.setHeroProfile(hero, profile);
    }
}