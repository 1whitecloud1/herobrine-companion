package com.whitecloud233.modid.herobrine_companion.entity.family;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HerobrineFamilySummonManager {
    private static final String RITUAL_ACTIVE_TAG = "HerobrineFamilySummonActive";
    private static final String RITUAL_TYPE_TAG = "HerobrineFamilySummonType";
    private static final String RITUAL_CENTER_TAG = "HerobrineFamilySummonCenter";
    private static final String RITUAL_END_TIME_TAG = "HerobrineFamilySummonEndTime";
    private static final String NEXT_FAILURE_MESSAGE_TIME_TAG = "HerobrineFamilySummonNextFailureMessageTime";
    private static final int RITUAL_DURATION_TICKS = 60;
    private static final int FAILURE_MESSAGE_COOLDOWN_TICKS = 80;

    private HerobrineFamilySummonManager() {
    }

    public static void serverTick(HeroEntity hero, ServerLevel level) {
        if (hero.getOwnerUUID() == null || !hero.isAlive()) {
            clearRitualState(hero);
            return;
        }

        if (isRitualActive(hero)) {
            tickActiveRitual(hero, level);
            return;
        }

        if (hero.tickCount % 10 != 0) {
            return;
        }

        HerobrineFamilySummonStructure structure = HerobrineFamilyStructureDetector.findNearbyStructure(level, hero.blockPosition());
        if (structure == null) {
            return;
        }

        if (HerobrineFamilySummonValidator.validateContinuation(hero) != null) {
            return;
        }

        if (!isHeroNearStand(hero, structure.heroStandPos())) {
            hero.getNavigation().moveTo(
                    structure.heroStandPos().getX() + 0.5D,
                    structure.heroStandPos().getY(),
                    structure.heroStandPos().getZ() + 0.5D,
                    1.0D
            );
            hero.getLookControl().setLookAt(
                    structure.center().getX() + 0.5D,
                    structure.center().getY() + 0.5D,
                    structure.center().getZ() + 0.5D
            );
            return;
        }

        Component failure = HerobrineFamilySummonValidator.validateStart(hero, level, structure);
        if (failure != null) {
            sendFailureMessage(hero, failure);
            return;
        }

        startRitual(hero, level, structure);
    }

    private static void tickActiveRitual(HeroEntity hero, ServerLevel level) {
        HerobrineFamilyMemberType type = HerobrineFamilyMemberType.byId(hero.getPersistentData().getString(RITUAL_TYPE_TAG));
        if (type == null || !hero.getPersistentData().contains(RITUAL_CENTER_TAG)) {
            clearRitualState(hero);
            return;
        }

        BlockPos center = NbtUtils.readBlockPos(hero.getPersistentData().getCompound(RITUAL_CENTER_TAG));
        long endTime = hero.getPersistentData().getLong(RITUAL_END_TIME_TAG);

        Component continuationFailure = HerobrineFamilySummonValidator.validateContinuation(hero);
        if (continuationFailure != null) {
            HerobrineFamilySummonEffects.playFailure(level, center, type);
            abortRitual(hero, continuationFailure);
            return;
        }

        if (hero.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D) > 196.0D) {
            HerobrineFamilySummonEffects.playFailure(level, center, type);
            abortRitual(hero, HerobrineFamilySummonFeedback.movedAway());
            return;
        }

        holdHeroAtRitual(hero, center);
        if (level.getGameTime() % 20L == 0L) {
            HerobrineFamilySummonEffects.playPulse(level, center, type);
        }

        if (level.getGameTime() < endTime) {
            return;
        }

        HerobrineFamilySummonStructure structure = HerobrineFamilyStructureDetector.detectAt(level, type, center);
        if (structure == null) {
            HerobrineFamilySummonEffects.playFailure(level, center, type);
            abortRitual(hero, HerobrineFamilySummonFeedback.structureBroken());
            return;
        }

        Component failure = HerobrineFamilySummonValidator.validateStructureUse(hero, level, structure);
        if (failure != null) {
            HerobrineFamilySummonEffects.playFailure(level, center, type);
            abortRitual(hero, failure);
            return;
        }

        if (!completeRitual(hero, level, structure)) {
            HerobrineFamilySummonEffects.playFailure(level, center, type);
            abortRitual(hero, HerobrineFamilySummonFeedback.noSpace());
            return;
        }

        clearRitualState(hero);
    }

    private static void startRitual(HeroEntity hero, ServerLevel level, HerobrineFamilySummonStructure structure) {
        hero.getPersistentData().putBoolean(RITUAL_ACTIVE_TAG, true);
        hero.getPersistentData().putString(RITUAL_TYPE_TAG, structure.memberType().id());
        hero.getPersistentData().put(RITUAL_CENTER_TAG, NbtUtils.writeBlockPos(structure.center()));
        hero.getPersistentData().putLong(RITUAL_END_TIME_TAG, level.getGameTime() + RITUAL_DURATION_TICKS);

        holdHeroAtRitual(hero, structure.center());
        HerobrineFamilySummonEffects.playStart(level, structure);
        HerobrineFamilySummonFeedback.announceStart(hero, structure);
    }

    private static boolean completeRitual(HeroEntity hero, ServerLevel level, HerobrineFamilySummonStructure structure) {
        Mob summoned = structure.memberType().entityType().create(level);
        if (summoned == null) {
            return false;
        }

        Vec3 spawnPos = structure.spawnPos();
        summoned.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, hero.getYRot(), 0.0F);
        if (!level.noCollision(summoned, summoned.getBoundingBox())) {
            return false;
        }

        HerobrineFamilySummonEffects.consumeStructureVisuals(level, structure);
        consumeStructure(level, structure);
        HerobrineFamilyMembers.assignSummonedIdentity(summoned, structure.memberType());
        if (summoned instanceof EnderDragon dragon && structure.memberType() == HerobrineFamilyMemberType.JEAN) {
            dragon.addTag("herobrine_companion_jean");
            dragon.setDragonFight(null);
            dragon.setFightOrigin(structure.center());
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        HerobrineFamilyRelations.bootstrapOwnerBond(
                summoned,
                structure.memberType(),
                HerobrineFamilySummonValidator.resolveCurrentTrust(hero, level),
                hero.getOwnerUUID()
        );
        level.addFreshEntity(summoned);

        HerobrineFamilyWorldData familyData = HerobrineFamilyWorldData.get(level);
        familyData.markSummoned(structure.memberType(), level.getGameTime());
        familyData.markTrialPassed(structure.memberType(), hero.getOwnerUUID());
        HerobrineFamilySummonEffects.playCompletion(level, structure, summoned);
        HerobrineFamilySummonFeedback.announceSuccess(hero, summoned, structure.memberType());
        return true;
    }

    private static void consumeStructure(ServerLevel level, HerobrineFamilySummonStructure structure) {
        for (BlockPos consumePos : structure.consumeBlocks()) {
            if (!level.getBlockState(consumePos).isAir()) {
                level.setBlock(consumePos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (BlockPos anchor : structure.crystalAnchors()) {
            AABB searchBox = new AABB(
                    anchor.getX() - 0.25D,
                    anchor.getY(),
                    anchor.getZ() - 0.25D,
                    anchor.getX() + 1.25D,
                    anchor.getY() + 2.75D,
                    anchor.getZ() + 1.25D
            );
            for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, searchBox, Entity::isAlive)) {
                crystal.discard();
            }
        }
    }

    private static void holdHeroAtRitual(HeroEntity hero, BlockPos center) {
        hero.getNavigation().stop();
        hero.setDeltaMovement(Vec3.ZERO);
        hero.setTarget(null);
        hero.getLookControl().setLookAt(center.getX() + 0.5D, center.getY() + 0.7D, center.getZ() + 0.5D);
    }

    private static boolean isHeroNearStand(HeroEntity hero, BlockPos standPos) {
        return hero.distanceToSqr(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D) <= 10.0D;
    }

    private static boolean isRitualActive(HeroEntity hero) {
        return hero.getPersistentData().getBoolean(RITUAL_ACTIVE_TAG);
    }

    private static void abortRitual(HeroEntity hero, Component message) {
        clearRitualState(hero);
        HerobrineFamilySummonFeedback.announceFailure(hero, message);
    }

    private static void clearRitualState(HeroEntity hero) {
        hero.getPersistentData().remove(RITUAL_ACTIVE_TAG);
        hero.getPersistentData().remove(RITUAL_TYPE_TAG);
        hero.getPersistentData().remove(RITUAL_CENTER_TAG);
        hero.getPersistentData().remove(RITUAL_END_TIME_TAG);
    }

    private static void sendFailureMessage(HeroEntity hero, Component message) {
        long now = hero.level().getGameTime();
        long nextAllowedTime = hero.getPersistentData().getLong(NEXT_FAILURE_MESSAGE_TIME_TAG);
        if (now < nextAllowedTime) {
            return;
        }

        hero.getPersistentData().putLong(NEXT_FAILURE_MESSAGE_TIME_TAG, now + FAILURE_MESSAGE_COOLDOWN_TICKS);
        HerobrineFamilySummonFeedback.announceFailure(hero, message);
    }
}
