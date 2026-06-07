package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialogueManager;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialoguePrompts;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialogueSpec;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AwakenedMobBrain {
    private static final double HERO_INFLUENCE_RANGE = 18.0D;
    private static final double AUDIENCE_RANGE = 10.0D;
    private static final double WARNING_RANGE = 8.0D;
    private static final double AWAKENED_POPULATION_RADIUS = 96.0D;
    private static final double PACK_CEASEFIRE_RANGE = 16.0D;
    private static final int MIN_SPEECH_TICKS = 60;
    private static final int MAX_SPEECH_TICKS = 120;
    private static final int FIRST_SIGHT_CEASEFIRE_TICKS = 100;
    private static final int INTERACTION_CEASEFIRE_TICKS = 180;
    private static final int GIFT_CEASEFIRE_TICKS = 2400;
    private static final int MAX_AWAKENED_IN_LOADED_AREA = 6;
    private static final int HERO_AI_MIN_COOLDOWN = 520;
    private static final int HERO_AI_MAX_COOLDOWN = 860;
    private static final List<String> HERO_REPLY_KEYS = List.of(
            "message.herobrine_companion.awakened_mob.hero_reply.0",
            "message.herobrine_companion.awakened_mob.hero_reply.1",
            "message.herobrine_companion.awakened_mob.hero_reply.2",
            "message.herobrine_companion.awakened_mob.hero_reply.3",
            "message.herobrine_companion.awakened_mob.hero_reply.4",
            "message.herobrine_companion.awakened_mob.hero_reply.5",
            "message.herobrine_companion.awakened_mob.hero_reply.6",
            "message.herobrine_companion.awakened_mob.hero_reply.7",
            "message.herobrine_companion.awakened_mob.hero_reply.8",
            "message.herobrine_companion.awakened_mob.hero_reply.9",
            "message.herobrine_companion.awakened_mob.hero_reply.10",
            "message.herobrine_companion.awakened_mob.hero_reply.11"
    );


    private AwakenedMobBrain() {
    }

    public static void serverTick(Mob mob) {
        if (mob.level().isClientSide || !mob.isAlive()) {
            return;
        }

        AwakenedMobProfile profile = AwakenedMobProfiles.get(mob);
        if (profile == null) {
            return;
        }

        AwakenedMobAccessor access = (AwakenedMobAccessor) mob;
        if (!access.herobrineCompanion$isAwakeningInitialized()) {
            initializeAwakening(mob, access, profile);
        }

        if (!access.herobrineCompanion$isAwakenedMob() || mob.tickCount % 20 != 0) {
            return;
        }

        if (hasPreferredAwakenedPeerOfSameFamilyInChunk(mob)) {
            clearAwakening(mob, access);
            return;
        }

        enforceForcedAwakenedName(mob, access);

        long now = mob.level().getGameTime();
        rememberNearbyPlayers(mob, profile, access, now);
        maybeWarnNearbyPlayer(mob, profile, access, now);

        HeroEntity nearbyHero = findNearbyHero(mob);
        if (nearbyHero != null && now >= access.herobrineCompanion$getNextHeroInteractionGameTime()) {
            requestHeroExchange(mob, nearbyHero, profile);
            access.herobrineCompanion$setNextHeroInteractionGameTime(now + randomBetween(mob.getRandom(), HERO_AI_MIN_COOLDOWN, HERO_AI_MAX_COOLDOWN));
            access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 120, 220));
            return;
        }

        if (now < access.herobrineCompanion$getNextAmbientSpeechGameTime() || !hasNearbyPlayerAudience(mob)) {
            return;
        }

        speak(mob, Component.translatable(randomEntry(profile.ambientKeys(), mob.getRandom())));
        access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 320, 560));
    }

    public static boolean handlePlayerInteraction(Player player, Entity target, ItemStack heldItem) {
        if (!(target instanceof Mob mob) || mob.level().isClientSide) {
            return false;
        }

        AwakenedMobProfile profile = AwakenedMobProfiles.get(mob);
        if (profile == null) {
            return false;
        }

        AwakenedMobAccessor access = (AwakenedMobAccessor) mob;
        if (!access.herobrineCompanion$isAwakenedMob()) {
            return false;
        }

        long now = mob.level().getGameTime();
        AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        boolean newEncounter = memory.touchSeen(now);
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);

        if (isPreferredGift(profile, heldItem) && memory.relationState() != AwakenedMobRelationState.HOSTILE) {
            if (now < access.herobrineCompanion$getNextPlayerInteractionGameTime()) {
                return true;
            }
            acceptGift(player, mob, profile, memory, heldItem, now);
            access.herobrineCompanion$setNextPlayerInteractionGameTime(now + randomBetween(mob.getRandom(), 100, 160));
            access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 220, 320));
            return true;
        }

        if (!heldItem.isEmpty() && !player.isShiftKeyDown()) {
            return false;
        }

        if (now < access.herobrineCompanion$getNextPlayerInteractionGameTime()) {
            return true;
        }

        if (memory.relationState().allowsCeasefire()) {
            grantFamilyCeasefire(mob, player, now, INTERACTION_CEASEFIRE_TICKS);
        }

        if (memory.relationState() == AwakenedMobRelationState.HOSTILE || memory.hasRecentAggression(now)) {
            speak(mob, Component.translatable(randomEntry(profile.hostileKeys(), mob.getRandom()), player.getDisplayName()));
        } else if (shouldRequestGift(memory, now)) {
            speak(mob, Component.translatable(randomEntry(profile.requestKeys(), mob.getRandom()), player.getDisplayName(), preferredGiftDescription(profile)));
            memory.setNextRequestGameTime(now + randomBetween(mob.getRandom(), 900, 1400));
            memory.adjustRelation(2);
        } else if (newEncounter && memory.encounterCount() == 1) {
            speakFirstMeetLine(mob, profile, memory, player);
            memory.adjustRelation(2);
        } else if (newEncounter) {
            speakRepeatMeetLine(mob, profile, memory, player);
            memory.adjustRelation(3);
        } else {
            speakDirectInteractionLine(mob, profile, memory, player);
            memory.adjustRelation(1);
        }

        access.herobrineCompanion$setNextPlayerInteractionGameTime(now + randomBetween(mob.getRandom(), 80, 140));
        access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 180, 260));
        return true;
    }

    public static void handlePlayerAttack(Player player, Entity target) {
        if (!(target instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }

        AwakenedMobProfile profile = AwakenedMobProfiles.get(mob);
        if (profile == null) {
            return;
        }

        long now = mob.level().getGameTime();
        AwakenedMobAccessor access = (AwakenedMobAccessor) mob;
        AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        if (!access.herobrineCompanion$isAwakenedMob() && !memory.isCeasefireActive(now)) {
            return;
        }
        memory.touchSeen(now);
        memory.adjustRelation(-28);
        memory.markAggression(now);
        mob.setTarget(player);

        if (now >= access.herobrineCompanion$getNextPlayerInteractionGameTime()) {
            speak(mob, Component.translatable(randomEntry(profile.hostileKeys(), mob.getRandom()), player.getDisplayName()));
            access.herobrineCompanion$setNextPlayerInteractionGameTime(now + randomBetween(mob.getRandom(), 80, 140));
        }
    }

    public static boolean shouldSuppressAggro(Mob mob, Player player) {
        AwakenedMobProfile profile = AwakenedMobProfiles.get(mob);
        if (profile == null) {
            return false;
        }

        AwakenedMobAccessor access = (AwakenedMobAccessor) mob;
        long now = mob.level().getGameTime();
        AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        boolean awakened = access.herobrineCompanion$isAwakenedMob();
        if (!awakened && !memory.isCeasefireActive(now)) {
            return false;
        }
        memory.touchSeen(now);

        if (memory.isCeasefireActive(now)) {
            return true;
        }

        if (memory.hasRecentAggression(now) || (awakened && memory.relationState() == AwakenedMobRelationState.HOSTILE)) {
            return false;
        }

        if (!awakened) {
            return false;
        }

        if (memory.relationState().suppressesAggro() || findNearbyHero(mob) != null) {
            return true;
        }

        if (memory.encounterCount() <= 1) {
            grantFamilyCeasefire(mob, player, now, FIRST_SIGHT_CEASEFIRE_TICKS);
            return true;
        }

        return false;
    }

    private static void initializeAwakening(Mob mob, AwakenedMobAccessor access, AwakenedMobProfile profile) {
        access.herobrineCompanion$setAwakeningInitialized(true);

        if (hasReachedLoadedAreaAwakenedCap(mob)) {
            return;
        }

        if (hasAnyAwakenedPeerOfSameFamilyInChunk(mob)) {
            return;
        }

        float chance = profile.awakeningChance();
        if (findNearbyHero(mob) != null) {
            chance += 0.15F;
        }

        if (mob.getRandom().nextFloat() > chance) {
            return;
        }

        access.herobrineCompanion$setAwakenedMob(true);
        access.herobrineCompanion$setNextAmbientSpeechGameTime(mob.level().getGameTime() + randomBetween(mob.getRandom(), 80, 180));
        access.herobrineCompanion$setNextHeroInteractionGameTime(mob.level().getGameTime() + randomBetween(mob.getRandom(), 120, 260));

        String forcedName = AwakenedMobProfiles.getForcedAwakenedName(mob);
        if (forcedName != null) {
            access.herobrineCompanion$setAwakenedMobName(forcedName);
            mob.setCustomName(Component.literal(forcedName));
        } else if (mob.hasCustomName()) {
            access.herobrineCompanion$setAwakenedMobName(mob.getName().getString());
        } else {
            String name = randomEntry(profile.names(), mob.getRandom());
            access.herobrineCompanion$setAwakenedMobName(name);
            mob.setCustomName(Component.literal(name));
        }

        mob.setPersistenceRequired();
    }

    private static void enforceForcedAwakenedName(Mob mob, AwakenedMobAccessor access) {
        String forcedName = AwakenedMobProfiles.getForcedAwakenedName(mob);
        if (forcedName == null) {
            return;
        }

        if (!forcedName.equals(access.herobrineCompanion$getAwakenedMobName())) {
            access.herobrineCompanion$setAwakenedMobName(forcedName);
        }

        if (mob.getCustomName() == null || !forcedName.equals(mob.getCustomName().getString())) {
            mob.setCustomName(Component.literal(forcedName));
        }
    }

    private static boolean hasReachedLoadedAreaAwakenedCap(Mob mob) {
        AABB searchBox = mob.getBoundingBox().inflate(AWAKENED_POPULATION_RADIUS);
        int awakenedCount = mob.level().getEntitiesOfClass(Mob.class, searchBox, candidate -> {
            if (candidate == mob || !candidate.isAlive()) {
                return false;
            }
            if (!AwakenedMobProfiles.supports(candidate)) {
                return false;
            }
            return candidate instanceof AwakenedMobAccessor accessor && accessor.herobrineCompanion$isAwakenedMob();
        }).size();
        return awakenedCount >= MAX_AWAKENED_IN_LOADED_AREA;
    }

    private static boolean hasAnyAwakenedPeerOfSameFamilyInChunk(Mob mob) {
        return hasAwakenedPeerOfSameFamilyInChunk(mob, false);
    }

    private static boolean hasPreferredAwakenedPeerOfSameFamilyInChunk(Mob mob) {
        return hasAwakenedPeerOfSameFamilyInChunk(mob, true);
    }

    private static boolean hasAwakenedPeerOfSameFamilyInChunk(Mob mob, boolean requirePreferredPeer) {
        ChunkPos chunkPos = mob.chunkPosition();
        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        double maxX = chunkPos.getMaxBlockX() + 1.0D;
        double maxZ = chunkPos.getMaxBlockZ() + 1.0D;
        AABB searchBox = new AABB(
                minX,
                mob.level().getMinBuildHeight(),
                minZ,
                maxX,
                mob.level().getMaxBuildHeight(),
                maxZ
        );

        return !mob.level().getEntitiesOfClass(Mob.class, searchBox, candidate -> {
            if (candidate == mob || !candidate.isAlive()) {
                return false;
            }
            if (!AwakenedMobProfiles.sharesFamily(mob, candidate)) {
                return false;
            }
            if (!(candidate instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
                return false;
            }
            return !requirePreferredPeer || candidate.getUUID().compareTo(mob.getUUID()) < 0;
        }).isEmpty();
    }

    private static void clearAwakening(Mob mob, AwakenedMobAccessor access) {
        access.herobrineCompanion$setAwakenedMob(false);
        access.herobrineCompanion$setAwakenedMobName("");
    }

    private static void rememberNearbyPlayers(Mob mob, AwakenedMobProfile profile, AwakenedMobAccessor access, long now) {
        if (now < access.herobrineCompanion$getNextAmbientSpeechGameTime()) {
            for (Player player : findNearbyPlayers(mob)) {
                access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID()).touchSeen(now);
            }
            return;
        }

        for (Player player : findNearbyPlayers(mob)) {
            AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
            if (!memory.touchSeen(now)) {
                continue;
            }

            if (memory.relationState() == AwakenedMobRelationState.HOSTILE || memory.hasRecentAggression(now)) {
                continue;
            }

            if (memory.encounterCount() == 1) {
                speakFirstMeetLine(mob, profile, memory, player);
            } else if (mob.getRandom().nextFloat() < 0.65F) {
                speakRepeatMeetLine(mob, profile, memory, player);
            } else {
                continue;
            }

            access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 220, 320));
            return;
        }
    }

    private static void maybeWarnNearbyPlayer(Mob mob, AwakenedMobProfile profile, AwakenedMobAccessor access, long now) {
        Player player = findWarningTarget(mob, access, now);
        if (player == null || now < access.herobrineCompanion$getNextAmbientSpeechGameTime()) {
            return;
        }

        AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        if (now < memory.nextReminderGameTime()) {
            return;
        }

        if (player.getHealth() / Math.max(1.0F, player.getMaxHealth()) > 0.45F && !hasNearbyThreat(mob, player)) {
            return;
        }

        speak(mob, Component.translatable(randomEntry(profile.reminderKeys(), mob.getRandom()), player.getDisplayName()));
        memory.setNextReminderGameTime(now + randomBetween(mob.getRandom(), 200, 360));
        access.herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 180, 240));
    }

    private static Player findWarningTarget(Mob mob, AwakenedMobAccessor access, long now) {
        return findNearbyPlayers(mob).stream()
                .filter(player -> {
                    AwakenedPlayerMemory memory = access.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
                    return !memory.hasRecentAggression(now)
                            && memory.relationState().ordinal() >= AwakenedMobRelationState.CURIOUS.ordinal();
                })
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static boolean hasNearbyThreat(Mob speaker, Player player) {
        return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WARNING_RANGE),
                mob -> mob != speaker && mob.isAlive() && mob.getTarget() == player).isEmpty();
    }

    private static void acceptGift(Player player, Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory, ItemStack heldItem, long now) {
        ItemStack acceptedGift = heldItem.copy();
        acceptedGift.setCount(profile.preferredItemCount());
        if (!player.getAbilities().instabuild) {
            heldItem.shrink(profile.preferredItemCount());
        }

        memory.recordGift();
        memory.adjustRelation(memory.preferredItemGifts() == 1 ? 20 : 12);
        grantFamilyCeasefire(mob, player, now, GIFT_CEASEFIRE_TICKS);
        memory.setNextRequestGameTime(now + randomBetween(mob.getRandom(), 1800, 2600));

        speakGiftLine(mob, profile, memory, player, acceptedGift);
        if (mob.getRandom().nextFloat() < 0.6F || memory.preferredItemGifts() == 1) {
            mob.spawnAtLocation(new ItemStack(profile.rewardItem(), profile.rewardItemCount()));
        }
    }

    private static void grantFamilyCeasefire(Mob source, Player player, long now, int durationTicks) {
        AwakenedMobAccessor sourceAccess = (AwakenedMobAccessor) source;
        sourceAccess.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID()).grantCeasefire(now, durationTicks);
        clearPlayerTarget(source, player);

        AABB searchBox = source.getBoundingBox().inflate(PACK_CEASEFIRE_RANGE);
        for (Mob candidate : source.level().getEntitiesOfClass(Mob.class, searchBox,
                mob -> mob.isAlive() && mob != source && AwakenedMobProfiles.sharesFamily(source, mob))) {
            if (!(candidate instanceof AwakenedMobAccessor accessor)) {
                continue;
            }
            accessor.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID()).grantCeasefire(now, durationTicks);
            clearPlayerTarget(candidate, player);
        }
    }

    private static void speakFirstMeetLine(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory, Player player) {
        String fallbackKey = randomEntry(profile.firstMeetKeys(), mob.getRandom());
        if (!requestDialogueToPlayer(mob, profile, memory, player,
                ActorDialoguePrompts.buildAwakenedEncounterPrompt(mob, player, memory, true),
                fallbackKey, List.of(player.getDisplayName().getString()))) {
            speak(mob, Component.translatable(fallbackKey, player.getDisplayName()));
        }
    }

    private static void speakRepeatMeetLine(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory, Player player) {
        String fallbackKey = randomEntry(profile.repeatMeetKeys(), mob.getRandom());
        if (!requestDialogueToPlayer(mob, profile, memory, player,
                ActorDialoguePrompts.buildAwakenedEncounterPrompt(mob, player, memory, false),
                fallbackKey, List.of(player.getDisplayName().getString()))) {
            speak(mob, Component.translatable(fallbackKey, player.getDisplayName()));
        }
    }

    private static void speakDirectInteractionLine(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory, Player player) {
        String fallbackKey = randomEntry(profile.playerInteractionKeys(), mob.getRandom());
        if (!requestDialogueToPlayer(mob, profile, memory, player,
                ActorDialoguePrompts.buildAwakenedConversationPrompt(mob, player, memory),
                fallbackKey, List.of(player.getDisplayName().getString()))) {
            speak(mob, Component.translatable(fallbackKey, player.getDisplayName()));
        }
    }

    private static void speakGiftLine(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory, Player player, ItemStack gift) {
        String giftName = preferredGiftDescription(profile).getString();
        String fallbackKey = randomEntry(profile.giftKeys(), mob.getRandom());
        List<String> fallbackArgs = List.of(player.getDisplayName().getString(), giftName);
        if (!requestDialogueToPlayer(mob, profile, memory, player,
                ActorDialoguePrompts.buildAwakenedGiftPrompt(mob, player, gift, memory),
                fallbackKey, fallbackArgs)) {
            speak(mob, Component.translatable(fallbackKey, player.getDisplayName(), preferredGiftDescription(profile)));
        }
    }

    private static boolean requestDialogueToPlayer(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory,
                                                   Player player, String userPrompt, String fallbackKey,
                                                   List<String> fallbackArgs) {
        if (!Config.awakenedMobAiDialogueEnabled) {
            return false;
        }

        ServerPlayer tokenOwner = findHeroOwnerAudience(mob);
        if (tokenOwner == null) {
            return false;
        }

        ActorDialogueManager.INSTANCE.requestDialogue(new ActorDialogueSpec(
                mob,
                ActorDialogueManager.createScopeId("awakened-player:" + mob.getUUID() + ":" + player.getUUID()),
                ActorDialoguePrompts.awakenedMobPersona(mob, profile, memory),
                userPrompt,
                buildFallbackSeed(fallbackKey, fallbackArgs),
                fallbackKey,
                fallbackArgs,
                tokenOwner,
                null
        ));
        return true;
    }

    private static void requestHeroExchange(Mob mob, HeroEntity hero, AwakenedMobProfile profile) {
        ServerPlayer preferredAudience = findHeroOwnerAudience(mob, hero);
        String mobFallbackKey = randomEntry(profile.heroInteractionKeys(), mob.getRandom());
        if (!Config.awakenedMobAiDialogueEnabled) {
            speak(mob, Component.translatable(mobFallbackKey));
            speakHeroFallbackReply(hero, mob);
            return;
        }

        if (preferredAudience == null) {
            speak(mob, Component.translatable(mobFallbackKey));
            speakHeroFallbackReply(hero, mob);
            return;
        }

        UUID scopeId = ActorDialogueManager.createScopeId("awakened-hero:" + mob.getUUID() + ":" + hero.getUUID());
        ActorDialogueManager.INSTANCE.requestDialogue(new ActorDialogueSpec(
                mob,
                scopeId,
                ActorDialoguePrompts.awakenedMobPersona(mob, profile,
                        preferredAudience == null ? null : ((AwakenedMobAccessor) mob).herobrineCompanion$getOrCreatePlayerMemory(preferredAudience.getUUID())),
                ActorDialoguePrompts.buildAwakenedHeroScene(mob, hero),
                buildFallbackSeed(mobFallbackKey, List.of()),
                mobFallbackKey,
                List.of(),
                preferredAudience,
                awakenedLine -> buildHeroReplySpec(hero, mob, preferredAudience, scopeId, awakenedLine)
        ));
    }

    private static ActorDialogueSpec buildHeroReplySpec(HeroEntity hero, Mob mob, ServerPlayer preferredAudience,
                                                        UUID scopeId, String awakenedLine) {
        String fallbackKey = HERO_REPLY_KEYS.get(mob.getRandom().nextInt(HERO_REPLY_KEYS.size()));
        List<String> fallbackArgs = List.of(mob.getName().getString());
        return new ActorDialogueSpec(
                hero,
                scopeId,
                ActorDialoguePrompts.herobrinePersona(hero, preferredAudience),
                ActorDialoguePrompts.buildHerobrineReplyToAwakened(hero, mob, awakenedLine, preferredAudience),
                buildFallbackSeed(fallbackKey, fallbackArgs),
                fallbackKey,
                fallbackArgs,
                preferredAudience,
                null
        );
    }

    private static boolean shouldRequestGift(AwakenedPlayerMemory memory, long now) {
        return memory.nextRequestGameTime() <= now
                && memory.relationState() != AwakenedMobRelationState.HOSTILE
                && (!memory.hasRecentAggression(now) || memory.preferredItemGifts() == 0);
    }

    private static boolean isPreferredGift(AwakenedMobProfile profile, ItemStack stack) {
        return !stack.isEmpty() && stack.is(profile.preferredItem()) && stack.getCount() >= profile.preferredItemCount();
    }

    private static Component preferredGiftDescription(AwakenedMobProfile profile) {
        Component itemName = profile.preferredItem().getDescription();
        if (profile.preferredItemCount() <= 1) {
            return itemName;
        }
        return Component.literal(profile.preferredItemCount() + "x ").append(itemName);
    }

    private static void clearPlayerTarget(Mob mob, Player player) {
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
    }

    private static ServerPlayer findHeroOwnerAudience(Mob mob) {
        HeroEntity nearbyHero = findNearbyHero(mob);
        return nearbyHero == null ? null : findHeroOwnerAudience(mob, nearbyHero);
    }

    private static ServerPlayer findHeroOwnerAudience(Mob mob, HeroEntity hero) {
        UUID ownerId = hero.getOwnerUUID();
        if (ownerId != null) {
            Player owner = mob.level().getPlayerByUUID(ownerId);
            if (owner instanceof ServerPlayer serverPlayer
                    && !serverPlayer.isChangingDimension()
                    && !serverPlayer.isRemoved()) {
                return serverPlayer;
            }
        }
        return null;
    }

    private static void speakHeroFallbackReply(HeroEntity hero, Mob mob) {
        String fallbackKey = HERO_REPLY_KEYS.get(mob.getRandom().nextInt(HERO_REPLY_KEYS.size()));
        if (hero instanceof SpeechBubbleAccessor accessor) {
            Component line = Component.translatable(fallbackKey, mob.getName());
            accessor.herobrineCompanion$showSpeechBubble(line, computeSpeechDuration(line));
        }
    }

    private static String buildFallbackSeed(String fallbackKey, List<String> fallbackArgs) {
        Object[] args = fallbackArgs.toArray(new Object[0]);
        return Component.translatable(fallbackKey, args).getString();
    }

    private static void speak(Mob mob, Component component) {
        if (mob instanceof SpeechBubbleAccessor accessor) {
            accessor.herobrineCompanion$showSpeechBubble(component, computeSpeechDuration(component));
        }
    }

    private static int computeSpeechDuration(Component component) {
        int duration = MIN_SPEECH_TICKS + component.getString().length() * 2;
        return Math.min(MAX_SPEECH_TICKS, Math.max(MIN_SPEECH_TICKS, duration));
    }

    private static boolean hasNearbyPlayerAudience(Mob mob) {
        return !findNearbyPlayers(mob).isEmpty();
    }

    private static List<Player> findNearbyPlayers(Mob mob) {
        return mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(AUDIENCE_RANGE),
                player -> player.isAlive() && !player.isSpectator() && !(player instanceof ServerPlayer serverPlayer && serverPlayer.isChangingDimension()));
    }

    private static HeroEntity findNearbyHero(Mob mob) {
        List<HeroEntity> heroes = mob.level().getEntitiesOfClass(HeroEntity.class, mob.getBoundingBox().inflate(HERO_INFLUENCE_RANGE),
                hero -> hero.isAlive());
        return heroes.isEmpty() ? null : heroes.get(0);
    }

    private static String randomEntry(List<String> entries, RandomSource random) {
        return entries.get(random.nextInt(entries.size()));
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
