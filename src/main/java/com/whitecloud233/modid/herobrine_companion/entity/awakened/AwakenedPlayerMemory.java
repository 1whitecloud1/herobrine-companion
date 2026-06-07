package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class AwakenedPlayerMemory {
    private static final String PLAYER_ID_TAG = "PlayerId";
    private static final String RELATION_SCORE_TAG = "RelationScore";
    private static final String ENCOUNTER_COUNT_TAG = "EncounterCount";
    private static final String PREFERRED_ITEM_GIFTS_TAG = "PreferredItemGifts";
    private static final String LAST_SEEN_GAME_TIME_TAG = "LastSeenGameTime";
    private static final String LAST_ENCOUNTER_GAME_TIME_TAG = "LastEncounterGameTime";
    private static final String CEASEFIRE_UNTIL_GAME_TIME_TAG = "CeasefireUntilGameTime";
    private static final String LAST_AGGRESSION_GAME_TIME_TAG = "LastAggressionGameTime";
    private static final String NEXT_REMINDER_GAME_TIME_TAG = "NextReminderGameTime";
    private static final String NEXT_REQUEST_GAME_TIME_TAG = "NextRequestGameTime";
    private static final int MIN_RELATION = -100;
    private static final int MAX_RELATION = 100;

    private final UUID playerId;
    private int relationScore;
    private int encounterCount;
    private int preferredItemGifts;
    private long lastSeenGameTime = Long.MIN_VALUE / 4;
    private long lastEncounterGameTime = Long.MIN_VALUE / 4;
    private long ceasefireUntilGameTime;
    private long lastAggressionGameTime = Long.MIN_VALUE / 4;
    private long nextReminderGameTime;
    private long nextRequestGameTime;

    public AwakenedPlayerMemory(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int relationScore() {
        return relationScore;
    }

    public AwakenedMobRelationState relationState() {
        return AwakenedMobRelationState.fromScore(relationScore);
    }

    public int encounterCount() {
        return encounterCount;
    }

    public int preferredItemGifts() {
        return preferredItemGifts;
    }

    public long lastEncounterGameTime() {
        return lastEncounterGameTime;
    }

    public long ceasefireUntilGameTime() {
        return ceasefireUntilGameTime;
    }

    public long nextReminderGameTime() {
        return nextReminderGameTime;
    }

    public long nextRequestGameTime() {
        return nextRequestGameTime;
    }

    public void adjustRelation(int delta) {
        relationScore = Math.max(MIN_RELATION, Math.min(MAX_RELATION, relationScore + delta));
    }

    public boolean touchSeen(long gameTime) {
        boolean newEncounter = gameTime - lastSeenGameTime > 200L;
        lastSeenGameTime = gameTime;
        if (newEncounter) {
            encounterCount++;
            lastEncounterGameTime = gameTime;
        }
        return newEncounter;
    }

    public void recordGift() {
        preferredItemGifts++;
    }

    public void grantCeasefire(long gameTime, int durationTicks) {
        ceasefireUntilGameTime = Math.max(ceasefireUntilGameTime, gameTime + Math.max(20, durationTicks));
    }

    public boolean isCeasefireActive(long gameTime) {
        return gameTime < ceasefireUntilGameTime;
    }

    public void markAggression(long gameTime) {
        lastAggressionGameTime = gameTime;
        ceasefireUntilGameTime = 0L;
    }

    public boolean hasRecentAggression(long gameTime) {
        return gameTime - lastAggressionGameTime < 2400L;
    }

    public void setNextReminderGameTime(long gameTime) {
        nextReminderGameTime = gameTime;
    }

    public void setNextRequestGameTime(long gameTime) {
        nextRequestGameTime = gameTime;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(PLAYER_ID_TAG, playerId);
        tag.putInt(RELATION_SCORE_TAG, relationScore);
        tag.putInt(ENCOUNTER_COUNT_TAG, encounterCount);
        tag.putInt(PREFERRED_ITEM_GIFTS_TAG, preferredItemGifts);
        tag.putLong(LAST_SEEN_GAME_TIME_TAG, lastSeenGameTime);
        tag.putLong(LAST_ENCOUNTER_GAME_TIME_TAG, lastEncounterGameTime);
        tag.putLong(CEASEFIRE_UNTIL_GAME_TIME_TAG, ceasefireUntilGameTime);
        tag.putLong(LAST_AGGRESSION_GAME_TIME_TAG, lastAggressionGameTime);
        tag.putLong(NEXT_REMINDER_GAME_TIME_TAG, nextReminderGameTime);
        tag.putLong(NEXT_REQUEST_GAME_TIME_TAG, nextRequestGameTime);
        return tag;
    }

    public static AwakenedPlayerMemory load(CompoundTag tag) {
        if (!tag.hasUUID(PLAYER_ID_TAG)) {
            return null;
        }

        AwakenedPlayerMemory memory = new AwakenedPlayerMemory(tag.getUUID(PLAYER_ID_TAG));
        memory.relationScore = tag.getInt(RELATION_SCORE_TAG);
        memory.encounterCount = tag.getInt(ENCOUNTER_COUNT_TAG);
        memory.preferredItemGifts = tag.getInt(PREFERRED_ITEM_GIFTS_TAG);
        memory.lastSeenGameTime = tag.getLong(LAST_SEEN_GAME_TIME_TAG);
        memory.lastEncounterGameTime = tag.getLong(LAST_ENCOUNTER_GAME_TIME_TAG);
        memory.ceasefireUntilGameTime = tag.getLong(CEASEFIRE_UNTIL_GAME_TIME_TAG);
        memory.lastAggressionGameTime = tag.getLong(LAST_AGGRESSION_GAME_TIME_TAG);
        memory.nextReminderGameTime = tag.getLong(NEXT_REMINDER_GAME_TIME_TAG);
        memory.nextRequestGameTime = tag.getLong(NEXT_REQUEST_GAME_TIME_TAG);
        return memory;
    }
}
