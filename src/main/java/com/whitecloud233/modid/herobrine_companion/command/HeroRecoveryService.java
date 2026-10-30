package com.whitecloud233.modid.herobrine_companion.command;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroInvitationHelper;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.fight.ChallengeSession;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.UUID;

/** Server-only recovery of the executing player's companion and its saved profile. */
public final class HeroRecoveryService {
    /** Consumed by the join listener; cleanup belongs to this service after addFreshEntity succeeds. */
    public static final String TAG_STAGED_RECOVERY = "hc_command_recovery_staged";

    public record Result(String message, HeroEntity hero) {}

    private HeroRecoveryService() {}

    public static Result recover(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HeroWorldData data = HeroWorldData.get(level);
        UUID owner = player.getUUID();
        HeroEntity existing = HeroSummonItem.findHeroInAnyDimension(level.getServer(), owner);
        if (!player.isAlive() || ChallengeSession.isPlayerInChallenge(player)
                || ChallengeSession.isPlayerFakeOutPhase(player)
                || level.dimension() == ModStructures.END_RING_DIMENSION_KEY
                || ChallengeSession.isHeroInChallenge(existing) || ChallengeSession.isHeroFakeOutPhase(existing)) {
            return new Result("blocked", null);
        }
        Vec3 destination = findSafePosition(level, player.blockPosition());
        if (destination == null) return new Result("no_space", null);

        // A remembered chunk may contain the unloaded original. Load only that one chunk.
        if (existing == null) {
            GlobalPos last = data.getLastKnownHeroPos(owner);
            if (last != null) {
                ServerLevel previousLevel = level.getServer().getLevel(last.dimension());
                if (previousLevel != null) previousLevel.getChunkAt(last.pos());
                existing = HeroSummonItem.findHeroInAnyDimension(level.getServer(), owner);
            }
        }
        if (ChallengeSession.isHeroInChallenge(existing) || ChallengeSession.isHeroFakeOutPhase(existing))
            return new Result("blocked", null);

        HeroEntity recovered = existing;
        boolean rebuild = existing == null;
        if (existing == null || existing.level() != level) {
            recovered = ModEntities.HERO.get().create(level);
            if (recovered == null) return new Result("failed", null);
            if (existing != null) {
                HeroStateManager.backupToGlobal(existing);
                CompoundTag entityData = new CompoundTag();
                existing.saveWithoutId(entityData);
                entityData.remove("UUID");
                recovered.load(entityData);
                recovered.setUUID(UUID.randomUUID());
                CompoundTag brainData = new CompoundTag();
                existing.getHeroBrain().save(brainData);
                recovered.getHeroBrain().load(brainData);
            } else {
                recovered.moveTo(destination);
                recovered.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(destination)),
                        MobSpawnType.TRIGGERED, null, null);
            }
            recovered.setOwnerUUID(owner);
            // Load player snapshots on a copy: don't consume the originals before spawning succeeds.
            int savedTrust = data.getTrust(owner);
            if (rebuild) {
                CompoundTag snapshot = player.getPersistentData().getCompound("HeroCombatRespawnData");
                if (snapshot.isEmpty()) snapshot = player.getPersistentData().getCompound("HeroRespawnData");
                if (!snapshot.isEmpty()) {
                    CompoundTag copy = snapshot.copy();
                    for (String key : new String[]{"UUID", "Pos", "Motion", "Rotation", "Passengers"}) copy.remove(key);
                    recovered.load(copy);
                    recovered.setUUID(UUID.randomUUID());
                    recovered.setOwnerUUID(owner);
                }
                data.setTrust(owner, savedTrust);
                HeroStateManager.restoreFromGlobal(recovered, player);
                CompoundTag brain = data.getTempBrainData(owner);
                if (brain != null) recovered.getHeroBrain().load(brain);
            }
            HeroDataHandler.syncGlobalTrust(recovered);
            resetMotion(recovered, false);
            recovered.moveTo(destination.x, destination.y, destination.z, player.getYRot(), 0);
            if (recovered.getHealth() <= 0) recovered.setHealth(recovered.getMaxHealth());
            recovered.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
            recovered.addTag(TAG_STAGED_RECOVERY);
            if (!level.addFreshEntity(recovered)) return new Result("failed", null);
            recovered.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
        } else {
            resetMotion(recovered, true);
            recovered.teleportTo(level, destination.x, destination.y, destination.z,
                    Collections.emptySet(), recovered.getYRot(), recovered.getXRot());
        }

        data.setActiveHeroUUID(owner, recovered.getUUID());
        data.setLastKnownHeroPos(owner, GlobalPos.of(level.dimension(), recovered.blockPosition()));
        data.setRespawnCooldown(owner, level, 0);
        recovered.markTeleportFollowHold();
        if (existing != null && existing != recovered) {
            resetMotion(existing, true);
            existing.discard();
        }
        // Once the canonical replacement exists, discard only this owner's loaded duplicates.
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            for (HeroEntity other : loadedLevel.getEntities(ModEntities.HERO.get(),
                    hero -> owner.equals(hero.getOwnerUUID()))) {
                if (other != recovered && !ChallengeSession.isHeroInChallenge(other)) {
                    resetMotion(other, true);
                    other.discard();
                }
            }
        }
        for (String key : new String[]{"HeroPendingRespawn", "HeroCombatRespawnData", "HeroRespawnData"}) {
            player.getPersistentData().remove(key);
        }
        data.setTempBrainData(owner, null);
        HeroStateManager.backupToGlobal(recovered);
        return new Result(rebuild ? "rebuilt" : "recalled", recovered);
    }

    private static void resetMotion(HeroEntity hero, boolean releaseWork) {
        hero.stopRiding();
        if (releaseWork && hero.getInvitedAction() == CookingService.INVITED_ACTION_MILLSTONE) {
            CookingService.releaseMillstone(hero, hero.getInvitedPos());
            CookingService.endCookChunkTicket(hero);
        }
        hero.setInvitedPos(null);
        hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);
        hero.getNavigation().stop();
        hero.setTarget(null);
        hero.setLastHurtByMob(null);
        hero.setDeltaMovement(Vec3.ZERO);
        hero.setFallDistance(0);
        hero.setFloating(false);
        hero.noPhysics = false;
        hero.setNoGravity(false);
        hero.setNoAi(false);
    }

    private static Vec3 findSafePosition(ServerLevel level, BlockPos origin) {
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (feet.getY() < level.getMinBuildHeight() + 1
                                || feet.getY() + 2 >= level.getMaxBuildHeight()
                                || !level.getWorldBorder().isWithinBounds(feet)
                                || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                                || !level.getFluidState(feet).isEmpty()
                                || !level.getFluidState(feet.above()).isEmpty()) continue;
                        Vec3 pos = Vec3.atBottomCenterOf(feet);
                        AABB box = new AABB(pos.x - .3, pos.y, pos.z - .3, pos.x + .3, pos.y + 1.95, pos.z + .3);
                        if (level.noCollision(box)) return pos;
                    }
                }
            }
        }
        return null;
    }
}
