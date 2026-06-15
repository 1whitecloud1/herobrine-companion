package com.whitecloud233.herobrine_companion.entity.awakened;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.dialogue.ActorDialogueManager;
import com.whitecloud233.herobrine_companion.entity.dialogue.ActorDialoguePrompts;
import com.whitecloud233.herobrine_companion.entity.dialogue.ActorDialogueSpec;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AwakenedMobPeerInteractionService {
    private static final double PEER_RANGE = 10.0D;
    private static final double AUDIENCE_RANGE = 14.0D;
    private static final double HERO_AWARENESS_RANGE = 18.0D;
    private static final int MIN_SPEECH_TICKS = 60;
    private static final int MAX_SPEECH_TICKS = 120;
    private static final int NORMAL_PEER_MIN_COOLDOWN = 700;
    private static final int NORMAL_PEER_MAX_COOLDOWN = 1200;
    private static final int SCUFFLE_MIN_COOLDOWN = 2400;
    private static final int SCUFFLE_MAX_COOLDOWN = 3200;
    private static final int PAIR_MIN_COOLDOWN = 1600;
    private static final int PAIR_MAX_COOLDOWN = 2400;
    private static final int REPLY_MIN_DELAY = 25;
    private static final int REPLY_MAX_DELAY = 45;
    private static final int CLEANUP_THRESHOLD = 256;

    private static final Map<PairKey, Long> PAIR_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, PendingReply> PENDING_REPLIES = new HashMap<>();
    private static final Map<SpecialLineKey, List<String>> SPECIAL_LINE_KEYS = createSpecialLineKeys();

    private AwakenedMobPeerInteractionService() {
    }

    public static boolean trySpeakWithPeer(Mob mob, AwakenedMobProfile profile, long now) {
        if (trySpeakPendingReply(mob, now)) {
            return true;
        }

        AwakenedMobAccessor access = (AwakenedMobAccessor) mob;
        if (now < access.herobrineCompanion$getNextPeerInteractionGameTime()) {
            return false;
        }

        Mob peer = findPeer(mob, now);
        if (peer == null) {
            return false;
        }

        ServerPlayer audience = findPlayerAudience(mob, peer);
        if (audience == null) {
            return false;
        }

        PairKey pairKey = PairKey.of(mob.getUUID(), peer.getUUID());
        if (PAIR_COOLDOWNS.getOrDefault(pairKey, 0L) > now) {
            return false;
        }

        AwakenedMobProfile peerProfile = AwakenedMobProfiles.get(peer);
        if (peerProfile == null) {
            return false;
        }

        Mob speaker = chooseSpeaker(mob, profile, peer, peerProfile);
        Mob listener = speaker == mob ? peer : mob;
        AwakenedMobProfile speakerProfile = speaker == mob ? profile : peerProfile;
        AwakenedMobProfile listenerProfile = listener == mob ? profile : peerProfile;
        if (listenerProfile == null) {
            return false;
        }

        AwakenedMobPeerScene scene = classifyScene(speaker, listener, speakerProfile, listenerProfile, now);
        String lineKey = selectLineKey(speakerProfile, listenerProfile, scene, speaker.getRandom());
        boolean requestedAi = requestAiPeerLine(speaker, listener, speakerProfile, listenerProfile, scene, lineKey, audience);
        if (!requestedAi) {
            speak(speaker, Component.translatable(lineKey, listener.getName()));
            scheduleReply(listener, speaker, listenerProfile, scene, now);
        }
        lookAtEachOther(speaker, listener);
        AwakenedMobPeerScuffleService.maybeStart(speaker, listener, speakerProfile, listenerProfile, scene, now);
        updateCooldowns(speaker, listener, pairKey, scene, now);
        cleanupExpiredCooldowns(now);
        return true;
    }

    private static Mob findPeer(Mob mob, long now) {
        AABB searchBox = mob.getBoundingBox().inflate(PEER_RANGE);
        return mob.level().getEntitiesOfClass(Mob.class, searchBox, candidate -> {
                    if (candidate == mob || !candidate.isAlive() || candidate.isRemoved()) {
                        return false;
                    }
                    AwakenedMobProfile profile = AwakenedMobProfiles.get(candidate);
                    if (profile == null || !(candidate instanceof AwakenedMobAccessor accessor)) {
                        return false;
                    }
                    return accessor.herobrineCompanion$isAwakenedMob()
                            && accessor.herobrineCompanion$getNextPeerInteractionGameTime() <= now;
                }).stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static ServerPlayer findPlayerAudience(Mob first, Mob second) {
        AABB searchBox = combinedSearchBox(first, second, AUDIENCE_RANGE);
        return first.level().getEntitiesOfClass(ServerPlayer.class, searchBox,
                player -> player.isAlive()
                        && !player.isSpectator()
                        && !player.isChangingDimension()).stream()
                .min(Comparator.comparingDouble(player ->
                        Math.min(first.distanceToSqr(player), second.distanceToSqr(player))))
                .orElse(null);
    }

    private static AABB combinedSearchBox(Mob first, Mob second, double inflate) {
        AABB firstBox = first.getBoundingBox();
        AABB secondBox = second.getBoundingBox();
        return new AABB(
                Math.min(firstBox.minX, secondBox.minX) - inflate,
                Math.min(firstBox.minY, secondBox.minY) - inflate,
                Math.min(firstBox.minZ, secondBox.minZ) - inflate,
                Math.max(firstBox.maxX, secondBox.maxX) + inflate,
                Math.max(firstBox.maxY, secondBox.maxY) + inflate,
                Math.max(firstBox.maxZ, secondBox.maxZ) + inflate
        );
    }

    private static Mob chooseSpeaker(Mob first, AwakenedMobProfile firstProfile, Mob second, AwakenedMobProfile secondProfile) {
        if (isAuthoritySpeaker(first, firstProfile) && !isAuthoritySpeaker(second, secondProfile)) {
            return first;
        }
        if (isAuthoritySpeaker(second, secondProfile) && !isAuthoritySpeaker(first, firstProfile)) {
            return second;
        }
        if (first.getTarget() == second) {
            return first;
        }
        if (second.getTarget() == first) {
            return second;
        }
        return first.getRandom().nextBoolean() ? first : second;
    }

    private static AwakenedMobPeerScene classifyScene(Mob speaker, Mob listener, AwakenedMobProfile speakerProfile,
                                                      AwakenedMobProfile listenerProfile, long now) {
        if (isAuthoritySpeaker(speaker, speakerProfile) && !isAuthoritySpeaker(listener, listenerProfile)) {
            return AwakenedMobPeerScene.AUTHORITY;
        }
        if (speaker.getTarget() == listener || listener.getTarget() == speaker) {
            return AwakenedMobPeerScene.SCUFFLE;
        }
        float closeConflictChance = AwakenedMobPeerRelationRules.closeConflictChance(speakerProfile, listenerProfile, 0.35F);
        if (speaker.distanceToSqr(listener) <= 9.0D && speaker.getRandom().nextFloat() < closeConflictChance) {
            return AwakenedMobPeerScene.CONFLICT;
        }
        LivingEntity speakerTarget = speaker.getTarget();
        if (speakerTarget != null && speakerTarget == listener.getTarget()) {
            return AwakenedMobPeerScene.COLLAB;
        }
        if (hasNearbyHero(speaker) || hasNearbyPlayerForGossip(speaker, listener, now)) {
            return AwakenedMobPeerScene.GOSSIP;
        }
        AwakenedMobPeerScene relationScene = AwakenedMobPeerRelationRules.pickDefaultScene(speakerProfile, listenerProfile, speaker.getRandom());
        if (relationScene != null) {
            return relationScene;
        }
        if (AwakenedMobProfiles.sharesFamily(speaker, listener) && speaker.getRandom().nextFloat() < 0.18F) {
            return AwakenedMobPeerScene.GOSSIP;
        }
        if (AwakenedMobProfiles.sharesFamily(speaker, listener)) {
            return AwakenedMobPeerScene.SAME;
        }
        return AwakenedMobPeerScene.CASUAL;
    }

    private static boolean hasNearbyHero(Mob mob) {
        return !mob.level().getEntitiesOfClass(HeroEntity.class, mob.getBoundingBox().inflate(HERO_AWARENESS_RANGE),
                HeroEntity::isAlive).isEmpty();
    }

    private static boolean hasNearbyPlayerForGossip(Mob first, Mob second, long now) {
        if (first.getRandom().nextFloat() >= 0.35F) {
            return false;
        }
        AABB searchBox = combinedSearchBox(first, second, AUDIENCE_RANGE);
        return !first.level().getEntitiesOfClass(Player.class, searchBox, player ->
                player.isAlive()
                        && !player.isSpectator()
                        && !(player instanceof ServerPlayer serverPlayer && serverPlayer.isChangingDimension())
                        && hasKnownPlayerMemory(first, player, now)).isEmpty();
    }

    private static boolean hasKnownPlayerMemory(Mob mob, Player player, long now) {
        AwakenedPlayerMemory memory = ((AwakenedMobAccessor) mob).herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        return memory.encounterCount() > 0 && !memory.hasRecentAggression(now);
    }

    private static boolean isHighAuthority(Mob mob) {
        EntityType<?> type = mob.getType();
        return type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
    }

    private static boolean isAuthoritySpeaker(Mob mob, AwakenedMobProfile profile) {
        if (isHighAuthority(mob)) {
            return true;
        }
        return profile != null && "ancient".equals(profile.root());
    }

    private static void lookAtEachOther(Mob speaker, Mob listener) {
        speaker.getLookControl().setLookAt(listener, 30.0F, 30.0F);
        listener.getLookControl().setLookAt(speaker, 30.0F, 30.0F);
    }

    private static void updateCooldowns(Mob speaker, Mob listener, PairKey pairKey, AwakenedMobPeerScene scene, long now) {
        int speakerCooldown = scene == AwakenedMobPeerScene.SCUFFLE
                ? randomBetween(speaker.getRandom(), SCUFFLE_MIN_COOLDOWN, SCUFFLE_MAX_COOLDOWN)
                : randomBetween(speaker.getRandom(), NORMAL_PEER_MIN_COOLDOWN, NORMAL_PEER_MAX_COOLDOWN);
        int listenerCooldown = randomBetween(listener.getRandom(), NORMAL_PEER_MIN_COOLDOWN, NORMAL_PEER_MAX_COOLDOWN);
        ((AwakenedMobAccessor) speaker).herobrineCompanion$setNextPeerInteractionGameTime(now + speakerCooldown);
        ((AwakenedMobAccessor) listener).herobrineCompanion$setNextPeerInteractionGameTime(now + listenerCooldown);
        ((AwakenedMobAccessor) speaker).herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(speaker.getRandom(), 220, 340));
        ((AwakenedMobAccessor) listener).herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(listener.getRandom(), 180, 300));
        PAIR_COOLDOWNS.put(pairKey, now + randomBetween(speaker.getRandom(), PAIR_MIN_COOLDOWN, PAIR_MAX_COOLDOWN));
    }

    private static void cleanupExpiredCooldowns(long now) {
        if (PAIR_COOLDOWNS.size() + PENDING_REPLIES.size() < CLEANUP_THRESHOLD) {
            return;
        }
        PAIR_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
        PENDING_REPLIES.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static boolean trySpeakPendingReply(Mob mob, long now) {
        PendingReply pendingReply = PENDING_REPLIES.get(mob.getUUID());
        if (pendingReply == null || pendingReply.dueAt() > now) {
            return false;
        }
        PENDING_REPLIES.remove(mob.getUUID());
        if (pendingReply.expiresAt() <= now || !hasPlayerAudience(mob)) {
            return false;
        }
        speak(mob, Component.translatable(pendingReply.lineKey(), pendingReply.targetName()));
        ((AwakenedMobAccessor) mob).herobrineCompanion$setNextAmbientSpeechGameTime(now + randomBetween(mob.getRandom(), 180, 300));
        return true;
    }

    private static boolean hasPlayerAudience(Mob mob) {
        AABB searchBox = mob.getBoundingBox().inflate(AUDIENCE_RANGE);
        return !mob.level().getEntitiesOfClass(Player.class, searchBox,
                player -> player.isAlive()
                        && !player.isSpectator()
                        && !(player instanceof ServerPlayer serverPlayer && serverPlayer.isChangingDimension())).isEmpty();
    }

    private static void scheduleReply(Mob listener, Mob speaker, AwakenedMobProfile listenerProfile,
                                      AwakenedMobPeerScene scene, long now) {
        if (scene == AwakenedMobPeerScene.SCUFFLE || listener.getRandom().nextFloat() >= 0.55F) {
            return;
        }
        String lineKey = randomEntry(listenerProfile.peerReplyKeys(), listener.getRandom());
        long dueAt = now + randomBetween(listener.getRandom(), REPLY_MIN_DELAY, REPLY_MAX_DELAY);
        PENDING_REPLIES.put(listener.getUUID(), new PendingReply(dueAt, dueAt + 80L, lineKey, speaker.getName()));
    }

    private static boolean requestAiPeerLine(Mob speaker, Mob listener, AwakenedMobProfile speakerProfile,
                                             AwakenedMobProfile listenerProfile, AwakenedMobPeerScene scene,
                                             String fallbackKey, ServerPlayer audience) {
        if (!shouldRequestAiPeerLine(speaker, scene, audience)) {
            return false;
        }

        List<String> fallbackArgs = List.of(listener.getName().getString());
        AwakenedPlayerMemory memory = ((AwakenedMobAccessor) speaker)
                .herobrineCompanion$getOrCreatePlayerMemory(audience.getUUID());
        ActorDialogueManager.INSTANCE.requestDialogue(new ActorDialogueSpec(
                speaker,
                ActorDialogueManager.createScopeId("awakened-peer:"
                        + speaker.getUUID() + ":" + listener.getUUID() + ":"
                        + scene.name().toLowerCase(Locale.ROOT)),
                ActorDialoguePrompts.awakenedMobPersona(speaker, speakerProfile, memory),
                ActorDialoguePrompts.buildAwakenedPeerScene(speaker, listener, speakerProfile, listenerProfile, scene, audience),
                buildFallbackSeed(fallbackKey, fallbackArgs),
                fallbackKey,
                fallbackArgs,
                audience,
                null
        ));
        return true;
    }

    private static boolean shouldRequestAiPeerLine(Mob speaker, AwakenedMobPeerScene scene, ServerPlayer audience) {
        if (!Config.awakenedMobAiDialogueEnabled || audience == null) {
            return false;
        }
        float chance = switch (scene) {
            case SAME -> 0.18F;
            case GOSSIP -> 0.28F;
            case CONFLICT -> 0.30F;
            case AUTHORITY -> 0.45F;
            default -> 0.0F;
        };
        return chance > 0.0F && speaker.getRandom().nextFloat() < chance;
    }

    private static String selectLineKey(AwakenedMobProfile speakerProfile, AwakenedMobProfile listenerProfile,
                                        AwakenedMobPeerScene scene, RandomSource random) {
        List<String> specialKeys = SPECIAL_LINE_KEYS.get(new SpecialLineKey(speakerProfile.root(), listenerProfile.root(), scene));
        if (specialKeys != null && !specialKeys.isEmpty()) {
            return randomEntry(specialKeys, random);
        }
        if (scene == AwakenedMobPeerScene.AUTHORITY) {
            List<String> commonAuthorityKeys = SPECIAL_LINE_KEYS.get(new SpecialLineKey(speakerProfile.root(), "common", scene));
            if (commonAuthorityKeys != null && !commonAuthorityKeys.isEmpty()) {
                return randomEntry(commonAuthorityKeys, random);
            }
        }
        return randomEntry(scene.keys(speakerProfile), random);
    }

    private static Map<SpecialLineKey, List<String>> createSpecialLineKeys() {
        Map<SpecialLineKey, List<String>> keys = new HashMap<>();
        putSpecial(keys, "zombie", "skeleton", AwakenedMobPeerScene.COLLAB, 2);
        putSpecial(keys, "skeleton", "zombie", AwakenedMobPeerScene.CASUAL, 2);
        putSpecial(keys, "creeper", "zombie", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "zombie", "creeper", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "spider", "skeleton", AwakenedMobPeerScene.COLLAB, 2);
        putSpecial(keys, "skeleton", "spider", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "witch", "raider", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "raider", "witch", AwakenedMobPeerScene.COLLAB, 2);
        putSpecial(keys, "piglin", "beast", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "beast", "piglin", AwakenedMobPeerScene.CASUAL, 2);
        putSpecial(keys, "blaze", "slime", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "slime", "blaze", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "guardian", "enderman", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "enderman", "guardian", AwakenedMobPeerScene.CONFLICT, 2);
        putSpecial(keys, "simmons", "common", AwakenedMobPeerScene.AUTHORITY, 2);
        putSpecial(keys, "jean", "common", AwakenedMobPeerScene.AUTHORITY, 2);

        putSpecial(keys, "zombie", "witch", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "witch", "zombie", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "zombie", "raider", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "raider", "zombie", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "zombie", "slime", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "slime", "zombie", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "skeleton", "creeper", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "creeper", "skeleton", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "skeleton", "phantom", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "phantom", "skeleton", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "skeleton", "raider", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "raider", "skeleton", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "creeper", "spider", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "spider", "creeper", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "creeper", "enderman", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "enderman", "creeper", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "creeper", "raider", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "raider", "creeper", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "spider", "enderman", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "enderman", "spider", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "spider", "witch", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "witch", "spider", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "spider", "phantom", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "phantom", "spider", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "enderman", "phantom", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "phantom", "enderman", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "witch", "slime", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "slime", "witch", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "witch", "blaze", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "blaze", "witch", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "raider", "piglin", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "piglin", "raider", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "raider", "beast", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "beast", "raider", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "slime", "guardian", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "guardian", "slime", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "slime", "piglin", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "piglin", "slime", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "blaze", "piglin", AwakenedMobPeerScene.COLLAB, 1);
        putSpecial(keys, "piglin", "blaze", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "blaze", "beast", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "beast", "blaze", AwakenedMobPeerScene.CASUAL, 1);
        putSpecial(keys, "piglin", "skeleton", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "skeleton", "piglin", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "ancient", "guardian", AwakenedMobPeerScene.AUTHORITY, 1);
        putSpecial(keys, "guardian", "ancient", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "jean", "phantom", AwakenedMobPeerScene.AUTHORITY, 1);
        putSpecial(keys, "phantom", "jean", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "jean", "enderman", AwakenedMobPeerScene.AUTHORITY, 1);
        putSpecial(keys, "enderman", "jean", AwakenedMobPeerScene.GOSSIP, 1);
        putSpecial(keys, "simmons", "jean", AwakenedMobPeerScene.CONFLICT, 1);
        putSpecial(keys, "jean", "simmons", AwakenedMobPeerScene.CASUAL, 1);
        return Map.copyOf(keys);
    }

    private static void putSpecial(Map<SpecialLineKey, List<String>> keys, String speakerRoot, String listenerRoot,
                                   AwakenedMobPeerScene scene, int count) {
        String sceneName = scene.name().toLowerCase(Locale.ROOT);
        String base = "message.herobrine_companion.awakened_mob." + speakerRoot + ".peer.to_" + listenerRoot + "." + sceneName;
        List<String> lineKeys = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> base + "." + index)
                .toList();
        keys.put(new SpecialLineKey(speakerRoot, listenerRoot, scene), lineKeys);
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

    private static String randomEntry(List<String> entries, RandomSource random) {
        return entries.get(random.nextInt(entries.size()));
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private record PairKey(UUID first, UUID second) {
        private static PairKey of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new PairKey(first, second) : new PairKey(second, first);
        }
    }

    private record PendingReply(long dueAt, long expiresAt, String lineKey, Component targetName) {
    }

    private record SpecialLineKey(String speakerRoot, String listenerRoot, AwakenedMobPeerScene scene) {
    }
}
