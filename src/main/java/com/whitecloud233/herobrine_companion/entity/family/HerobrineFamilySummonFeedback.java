package com.whitecloud233.herobrine_companion.entity.family;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfile;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfiles;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class HerobrineFamilySummonFeedback {
    private static final int MIN_SPEECH_TICKS = 60;
    private static final int MAX_SPEECH_TICKS = 120;

    private HerobrineFamilySummonFeedback() {
    }

    public static void announceStart(HeroEntity hero, HerobrineFamilySummonStructure structure) {
        HerobrineFamilyMemberType type = structure.memberType();
        sendOwnerMessage(hero, Component.translatable("message.herobrine_companion.family_summon.start." + type.id()));
        showBubble(hero, Component.translatable("message.herobrine_companion.family_summon.bubble.start." + type.id()));
    }

    public static void announceFailure(HeroEntity hero, Component message) {
        sendOwnerMessage(hero, message);
        showBubble(hero, message);
    }

    public static void announceSuccess(HeroEntity hero, Mob summoned, HerobrineFamilyMemberType type) {
        sendOwnerMessage(hero, Component.translatable("message.herobrine_companion.family_summon.success." + type.id()));
        showBubble(hero, Component.translatable("message.herobrine_companion.family_summon.bubble.success." + type.id()));
        showSummonedIntro(hero, summoned, type);
    }

    public static Component lowTrust(HerobrineFamilyMemberType type, int currentTrust) {
        return Component.translatable("message.herobrine_companion.family_summon.failure.low_trust",
                memberName(type), type.requiredTrust(), currentTrust);
    }

    public static Component cooldown(HerobrineFamilyMemberType type, long remainingSeconds) {
        return Component.translatable("message.herobrine_companion.family_summon.failure.cooldown",
                memberName(type), remainingSeconds);
    }

    public static Component alreadyExists(HerobrineFamilyMemberType type) {
        return Component.translatable("message.herobrine_companion.family_summon.failure.exists",
                memberName(type));
    }

    public static Component noSpace() {
        return Component.translatable("message.herobrine_companion.family_summon.failure.no_space");
    }

    public static Component busy() {
        return Component.translatable("message.herobrine_companion.family_summon.failure.busy");
    }

    public static Component structureBroken() {
        return Component.translatable("message.herobrine_companion.family_summon.failure.structure_broken");
    }

    public static Component movedAway() {
        return Component.translatable("message.herobrine_companion.family_summon.failure.hero_moved");
    }

    public static Component canceled() {
        return Component.translatable("message.herobrine_companion.family_summon.failure.canceled");
    }

    private static Component memberName(HerobrineFamilyMemberType type) {
        return Component.translatable("message.herobrine_companion.family_summon.member." + type.id());
    }

    private static void showSummonedIntro(HeroEntity hero, Mob summoned, HerobrineFamilyMemberType type) {
        AwakenedMobProfile profile = AwakenedMobProfiles.get(summoned);
        if (profile == null || hero.getOwnerPlayer() == null) {
            return;
        }

        Player owner = hero.getOwnerPlayer();
        List<String> firstMeetKeys = profile.firstMeetKeys();
        if (firstMeetKeys.isEmpty()) {
            return;
        }

        int lineIndex = Math.abs(summoned.getRandom().nextInt()) % firstMeetKeys.size();
        showBubble(summoned, Component.translatable(firstMeetKeys.get(lineIndex), owner.getDisplayName()));
        showBubble(hero, Component.translatable("message.herobrine_companion.family_summon.bubble.answer." + type.id()));
    }

    private static void sendOwnerMessage(HeroEntity hero, Component message) {
        Player ownerPlayer = hero.getOwnerPlayer();
        if (ownerPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message);
        }
    }

    private static void showBubble(Object speaker, Component line) {
        if (!(speaker instanceof SpeechBubbleAccessor accessor) || line == null) {
            return;
        }
        accessor.herobrineCompanion$showSpeechBubble(line, computeSpeechDuration(line));
    }

    private static int computeSpeechDuration(Component component) {
        int duration = MIN_SPEECH_TICKS + component.getString().length() * 2;
        return Math.min(MAX_SPEECH_TICKS, Math.max(MIN_SPEECH_TICKS, duration));
    }
}
