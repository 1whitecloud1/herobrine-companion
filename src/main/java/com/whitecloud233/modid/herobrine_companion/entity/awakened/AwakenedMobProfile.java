package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import net.minecraft.world.item.Item;

import java.util.List;

public record AwakenedMobProfile(
        float awakeningChance,
        List<String> names,
        List<String> ambientKeys,
        List<String> firstMeetKeys,
        List<String> repeatMeetKeys,
        List<String> requestKeys,
        List<String> playerInteractionKeys,
        List<String> giftKeys,
        List<String> reminderKeys,
        List<String> hostileKeys,
        List<String> heroInteractionKeys,
        Item preferredItem,
        int preferredItemCount,
        Item rewardItem,
        int rewardItemCount
) {
}
