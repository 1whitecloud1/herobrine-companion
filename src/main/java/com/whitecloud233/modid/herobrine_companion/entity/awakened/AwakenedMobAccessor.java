package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import java.util.Collection;
import java.util.UUID;

public interface AwakenedMobAccessor {
    boolean herobrineCompanion$isAwakenedMob();

    void herobrineCompanion$setAwakenedMob(boolean awakened);

    boolean herobrineCompanion$isAwakeningInitialized();

    void herobrineCompanion$setAwakeningInitialized(boolean initialized);

    String herobrineCompanion$getAwakenedMobName();

    void herobrineCompanion$setAwakenedMobName(String name);

    long herobrineCompanion$getNextAmbientSpeechGameTime();

    void herobrineCompanion$setNextAmbientSpeechGameTime(long gameTime);

    long herobrineCompanion$getNextPlayerInteractionGameTime();

    void herobrineCompanion$setNextPlayerInteractionGameTime(long gameTime);

    long herobrineCompanion$getNextHeroInteractionGameTime();

    void herobrineCompanion$setNextHeroInteractionGameTime(long gameTime);

    AwakenedPlayerMemory herobrineCompanion$getOrCreatePlayerMemory(UUID playerId);

    Collection<AwakenedPlayerMemory> herobrineCompanion$getPlayerMemories();
}
