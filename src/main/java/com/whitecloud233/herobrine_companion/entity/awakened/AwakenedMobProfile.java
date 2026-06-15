package com.whitecloud233.herobrine_companion.entity.awakened;

import net.minecraft.world.item.Item;

import java.util.List;

public record AwakenedMobProfile(
        String root,
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
        List<String> peerSameFamilyKeys,
        List<String> peerCasualKeys,
        List<String> peerCollabKeys,
        List<String> peerGossipKeys,
        List<String> peerConflictKeys,
        List<String> peerScuffleKeys,
        List<String> peerAuthorityKeys,
        List<String> peerReplyKeys,
        Item preferredItem,
        int preferredItemCount,
        Item rewardItem,
        int rewardItemCount
) {
}
