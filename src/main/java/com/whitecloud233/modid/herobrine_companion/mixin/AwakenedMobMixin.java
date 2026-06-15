package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobBrain;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedPlayerMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(Mob.class)
public abstract class AwakenedMobMixin implements AwakenedMobAccessor {
    @Unique
    private static final String HEROBRINE_COMPANION_AWAKENED_TAG = "HerobrineCompanionAwakened";
    @Unique
    private static final String HEROBRINE_COMPANION_AWAKENING_INITIALIZED_TAG = "HerobrineCompanionAwakeningInitialized";
    @Unique
    private static final String HEROBRINE_COMPANION_AWAKENED_NAME_TAG = "HerobrineCompanionAwakenedName";
    @Unique
    private static final String HEROBRINE_COMPANION_PLAYER_MEMORIES_TAG = "HerobrineCompanionPlayerMemories";
    @Unique
    private static final EntityDataAccessor<Boolean> HEROBRINE_COMPANION_AWAKENED_VISUAL =
            SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private boolean herobrineCompanion$awakenedMob;
    @Unique
    private boolean herobrineCompanion$awakeningInitialized;
    @Unique
    private String herobrineCompanion$awakenedMobName = "";
    @Unique
    private long herobrineCompanion$nextAmbientSpeechGameTime;
    @Unique
    private long herobrineCompanion$nextPlayerInteractionGameTime;
    @Unique
    private long herobrineCompanion$nextHeroInteractionGameTime;
    @Unique
    private long herobrineCompanion$nextPeerInteractionGameTime;
    @Unique
    private final Map<UUID, AwakenedPlayerMemory> herobrineCompanion$playerMemories = new HashMap<>();

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void herobrineCompanion$defineAwakenedVisualData(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        self.getEntityData().define(HEROBRINE_COMPANION_AWAKENED_VISUAL, false);
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void herobrineCompanion$runAwakenedMobBrain(CallbackInfo ci) {
        AwakenedMobBrain.serverTick((Mob) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void herobrineCompanion$saveAwakenedState(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(HEROBRINE_COMPANION_AWAKENED_TAG, herobrineCompanion$awakenedMob);
        tag.putBoolean(HEROBRINE_COMPANION_AWAKENING_INITIALIZED_TAG, herobrineCompanion$awakeningInitialized);
        if (!herobrineCompanion$awakenedMobName.isEmpty()) {
            tag.putString(HEROBRINE_COMPANION_AWAKENED_NAME_TAG, herobrineCompanion$awakenedMobName);
        }
        if (!herobrineCompanion$playerMemories.isEmpty()) {
            ListTag memoryList = new ListTag();
            for (AwakenedPlayerMemory memory : herobrineCompanion$playerMemories.values()) {
                memoryList.add(memory.save());
            }
            tag.put(HEROBRINE_COMPANION_PLAYER_MEMORIES_TAG, memoryList);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void herobrineCompanion$loadAwakenedState(CompoundTag tag, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        herobrineCompanion$awakenedMob = tag.getBoolean(HEROBRINE_COMPANION_AWAKENED_TAG);
        herobrineCompanion$awakeningInitialized = tag.getBoolean(HEROBRINE_COMPANION_AWAKENING_INITIALIZED_TAG);
        herobrineCompanion$awakenedMobName = tag.getString(HEROBRINE_COMPANION_AWAKENED_NAME_TAG);
        self.getEntityData().set(HEROBRINE_COMPANION_AWAKENED_VISUAL, herobrineCompanion$awakenedMob);
        herobrineCompanion$playerMemories.clear();

        ListTag memoryList = tag.getList(HEROBRINE_COMPANION_PLAYER_MEMORIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < memoryList.size(); i++) {
            AwakenedPlayerMemory memory = AwakenedPlayerMemory.load(memoryList.getCompound(i));
            if (memory != null) {
                herobrineCompanion$playerMemories.put(memory.playerId(), memory);
            }
        }

        if (herobrineCompanion$awakenedMob && !herobrineCompanion$awakenedMobName.isEmpty() && !self.hasCustomName()) {
            self.setCustomName(Component.literal(herobrineCompanion$awakenedMobName));
        }
    }

    @Override
    public boolean herobrineCompanion$isAwakenedMob() {
        Mob self = (Mob) (Object) this;
        return herobrineCompanion$awakenedMob || self.getEntityData().get(HEROBRINE_COMPANION_AWAKENED_VISUAL);
    }

    @Override
    public void herobrineCompanion$setAwakenedMob(boolean awakened) {
        this.herobrineCompanion$awakenedMob = awakened;
        Mob self = (Mob) (Object) this;
        self.getEntityData().set(HEROBRINE_COMPANION_AWAKENED_VISUAL, awakened);
    }

    @Override
    public boolean herobrineCompanion$isAwakeningInitialized() {
        return herobrineCompanion$awakeningInitialized;
    }

    @Override
    public void herobrineCompanion$setAwakeningInitialized(boolean initialized) {
        this.herobrineCompanion$awakeningInitialized = initialized;
    }

    @Override
    public String herobrineCompanion$getAwakenedMobName() {
        return herobrineCompanion$awakenedMobName;
    }

    @Override
    public void herobrineCompanion$setAwakenedMobName(String name) {
        this.herobrineCompanion$awakenedMobName = name == null ? "" : name;
    }

    @Override
    public long herobrineCompanion$getNextAmbientSpeechGameTime() {
        return herobrineCompanion$nextAmbientSpeechGameTime;
    }

    @Override
    public void herobrineCompanion$setNextAmbientSpeechGameTime(long gameTime) {
        this.herobrineCompanion$nextAmbientSpeechGameTime = gameTime;
    }

    @Override
    public long herobrineCompanion$getNextPlayerInteractionGameTime() {
        return herobrineCompanion$nextPlayerInteractionGameTime;
    }

    @Override
    public void herobrineCompanion$setNextPlayerInteractionGameTime(long gameTime) {
        this.herobrineCompanion$nextPlayerInteractionGameTime = gameTime;
    }

    @Override
    public long herobrineCompanion$getNextHeroInteractionGameTime() {
        return herobrineCompanion$nextHeroInteractionGameTime;
    }

    @Override
    public void herobrineCompanion$setNextHeroInteractionGameTime(long gameTime) {
        this.herobrineCompanion$nextHeroInteractionGameTime = gameTime;
    }

    @Override
    public long herobrineCompanion$getNextPeerInteractionGameTime() {
        return herobrineCompanion$nextPeerInteractionGameTime;
    }

    @Override
    public void herobrineCompanion$setNextPeerInteractionGameTime(long gameTime) {
        this.herobrineCompanion$nextPeerInteractionGameTime = gameTime;
    }

    @Override
    public AwakenedPlayerMemory herobrineCompanion$getOrCreatePlayerMemory(UUID playerId) {
        return herobrineCompanion$playerMemories.computeIfAbsent(playerId, AwakenedPlayerMemory::new);
    }

    @Override
    public Collection<AwakenedPlayerMemory> herobrineCompanion$getPlayerMemories() {
        return herobrineCompanion$playerMemories.values();
    }
}
