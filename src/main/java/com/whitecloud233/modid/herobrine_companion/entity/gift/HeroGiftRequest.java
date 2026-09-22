package com.whitecloud233.modid.herobrine_companion.entity.gift;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/**
 * 请求系统 —— Bedrock hero_player_offer_request.py 的纯逻辑移植。
 *
 * <p>单一职责:请求池与匹配/过期规则。活动请求存于英雄档案(activeRequest),
 * 生成/判定由 HeroOfferService 编排。请求永远是谦卑、有意义的物品。
 */
public final class HeroGiftRequest {

    private HeroGiftRequest() {
    }

    public static final int REQUEST_EXPIRE_TICKS = 24000;
    public static final double REQUEST_ASK_CHANCE = 0.70;
    public static final double REQUEST_GENERATE_CHANCE = 0.08;

    /** 请求条目。 */
    public record Request(String itemName, String category, String label) {
    }

    public static final List<Request> REQUEST_POOL = List.of(
            new Request("minecraft:oak_sapling", "repair", "一棵树苗"),
            new Request("minecraft:torch", "repair", "一根火把"),
            new Request("minecraft:bread", "cooked", "一片面包"),
            new Request("minecraft:cooked_porkchop", "cooked", "一块熟肉"),
            new Request("minecraft:bone", "night", "一块骨头"),
            new Request("minecraft:gunpowder", "night", "一份火药"),
            new Request("minecraft:iron_pickaxe", "old", "一把旧铁镐"),
            new Request("minecraft:book", "old", "一本书"),
            new Request("minecraft:rotten_flesh", "rotten", "一块腐肉"),
            new Request("minecraft:chorus_fruit", "rift", "一颗紫颂果"));

    /**
     * 按玩家已接触的赠礼类别过滤候选;历史为空时不出普通请求,
     * force=true(至珍索取)回退全池。
     */
    public static Request pickRequest(Set<String> seenCategories, RandomSource random, boolean force) {
        if ((seenCategories == null || seenCategories.isEmpty()) && !force) {
            return null;
        }
        List<Request> pool;
        if (force) {
            pool = REQUEST_POOL;
        } else {
            pool = REQUEST_POOL.stream()
                    .filter(r -> seenCategories.contains(r.category()))
                    .toList();
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** 递交物是否达成活动请求。 */
    public static boolean matchRequest(com.whitecloud233.modid.herobrine_companion.entity.gift.HeroGiftProfile.ActiveRequest activeRequest, HeroGiftOffer offer) {
        return activeRequest != null && !activeRequest.itemName().isEmpty()
                && activeRequest.itemName().equals(offer.itemName());
    }

    /** 过期判定(expireTick 由档案层写成)。 */
    public static boolean isExpired(com.whitecloud233.modid.herobrine_companion.entity.gift.HeroGiftProfile.ActiveRequest activeRequest, int tick) {
        return activeRequest != null && activeRequest.expireTick() > 0 && tick > activeRequest.expireTick();
    }
}