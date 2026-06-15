package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobRelationState;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedPlayerMemory;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyWorldData;
import com.whitecloud233.herobrine_companion.entity.family.JeanCombatResponseService;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HerobrineFamilyMountEvents {
    private static final String JEAN_MOUNT_NO_AI_TAG = "HerobrineCompanionJeanMountNoAi";
    private static final String JEAN_FRONT_HERO_TAG = "HerobrineCompanionJeanFrontHero";
    private static final String JEAN_FRONT_HERO_TIME_TAG = "HerobrineCompanionJeanFrontHeroTime";
    private static final String JEAN_PLAYER_RIDDEN_TAG = "HerobrineCompanionJeanPlayerRidden";
    private static final String HERO_FRONT_SEAT_TAG = "HerobrineCompanionJeanFrontSeat";
    private static final String HERO_FRONT_SEAT_NO_AI_TAG = "HerobrineCompanionJeanFrontSeatNoAi";
    private static final double FLIGHT_SPEED = 1.15D;
    private static final double VERTICAL_SPEED = 0.42D;
    private static final double HERO_SEAT_Y_OFFSET = 4.75D;
    private static final double PLAYER_SEAT_Y_OFFSET = 5.65D;
    private static final double HERO_BOARDING_FRONT_OFFSET = 7.2D;
    private static final double HERO_MOUNTED_BACK_OFFSET = -5.8D;
    private static final double PLAYER_FRONT_OFFSET = 4.2D;
    private static final double HERO_BOARDING_SIDE_OFFSET = -3.2D;
    private static final double HERO_MOUNTED_SIDE_OFFSET = 0.0D;
    private static final double PLAYER_SIDE_OFFSET = 0.0D;
    private static final int INPUT_TIMEOUT_TICKS = 12;
    private static final Map<UUID, MountInput> INPUTS = new HashMap<>();

    private HerobrineFamilyMountEvents() {
    }

    public static void updateInput(ServerPlayer player, float strafe, float forward, boolean jump, boolean dismount, float yaw, float pitch) {
        if (player == null
                || !(player.getVehicle() instanceof EnderDragon dragon)
                || !isJean(dragon)) {
            return;
        }

        long now = player.level().getGameTime();
        INPUTS.put(player.getUUID(), new MountInput(
                Mth.clamp(strafe, -1.0F, 1.0F),
                Mth.clamp(forward, -1.0F, 1.0F),
                jump,
                dismount,
                yaw,
                Mth.clamp(pitch, -90.0F, 90.0F),
                now
        ));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleMountInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleMountInteraction(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && isJean(dragon)) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof EnderDragon dragon) || !isJean(dragon)) {
            INPUTS.remove(player.getUUID());
            return;
        }

        MountInput input = INPUTS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (input == null || now - input.gameTime > INPUT_TIMEOUT_TICKS) {
            input = MountInput.idle(now, player.getYRot(), player.getXRot());
        }

        if (input.dismount) {
            player.stopRiding();
            INPUTS.remove(player.getUUID());
            clearMountedJean(dragon);
            return;
        }

        driveJean(dragon, player, input);
        positionMountedPair(dragon, player);
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof EnderDragon dragon) || !isJean(dragon)) {
            return;
        }

        if (JeanCombatResponseService.hasActivePlayerDamageResponse(dragon)) {
            clearMountedJean(dragon);
            return;
        }

        detachLegacyHeroPassenger(dragon);

        HeroEntity frontHero = getFrontHero(dragon);
        Player rider = getPlayerRider(dragon);

        if (frontHero == null) {
            if (rider instanceof ServerPlayer serverPlayer && canPlayerBoardJean(dragon, serverPlayer)) {
                frontHero = findEligibleHero(dragon, serverPlayer);
                if (frontHero != null) {
                    mountHeroFront(dragon, frontHero);
                }
            }

            if (frontHero == null) {
                if (rider != null) {
                    rider.stopRiding();
                }
                dragon.getPersistentData().remove(JEAN_PLAYER_RIDDEN_TAG);
                if (dragon.getPersistentData().getBoolean(JEAN_MOUNT_NO_AI_TAG)) {
                    releaseMountedJean(dragon);
                }
                return;
            }
        }

        if (dragon.getPersistentData().getBoolean(JEAN_PLAYER_RIDDEN_TAG) && rider == null) {
            clearMountedJean(dragon);
            return;
        }

        if (rider == null) {
            prepareBoardingJean(dragon);
        } else {
            prepareMountedJean(dragon);
        }
        if (rider instanceof ServerPlayer serverPlayer) {
            positionMountedPair(dragon, serverPlayer);
        } else {
            positionBoardingHero(dragon, frontHero);
        }
    }

    private static void handleMountInteraction(PlayerInteractEvent event, Entity rawTarget) {
        if (event.getEntity().level().isClientSide
                || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().isEmpty()
                || event.getEntity().isShiftKeyDown()) {
            return;
        }

        EnderDragon dragon = resolveDragon(rawTarget);
        if (dragon == null || !isJean(dragon) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        detachLegacyHeroPassenger(dragon);

        if (!canPlayerBoardJean(dragon, player)) {
            return;
        }

        HeroEntity frontHero = getFrontHero(dragon);
        if (frontHero == null) {
            HeroEntity hero = findEligibleHero(dragon, player);
            if (hero == null) {
                cancelInteraction(event);
                return;
            }
            mountHeroFront(dragon, hero);
            cancelInteraction(event);
            return;
        }

        if (getPlayerRider(dragon) != null || player.getVehicle() == dragon) {
            cancelInteraction(event);
            return;
        }

        if (player.level().getGameTime() <= dragon.getPersistentData().getLong(JEAN_FRONT_HERO_TIME_TAG)) {
            cancelInteraction(event);
            return;
        }

        if (player.startRiding(dragon, true)) {
            prepareMountedJean(dragon);
            dragon.getPersistentData().putBoolean(JEAN_PLAYER_RIDDEN_TAG, true);
            INPUTS.put(player.getUUID(), MountInput.idle(player.level().getGameTime(), player.getYRot(), player.getXRot()));
            positionMountedPair(dragon, player);
            cancelInteraction(event);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.EntityInteract entityInteract) {
            entityInteract.setCanceled(true);
            entityInteract.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific specificInteract) {
            specificInteract.setCanceled(true);
            specificInteract.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void driveJean(EnderDragon dragon, ServerPlayer rider, MountInput input) {
        if (getFrontHero(dragon) == null) {
            HeroEntity hero = findEligibleHero(dragon, rider);
            if (hero == null) {
                rider.stopRiding();
                clearMountedJean(dragon);
                return;
            }
            mountHeroFront(dragon, hero);
        }

        prepareMountedJean(dragon);

        float yaw = input.yaw;
        float dragonYaw = yaw + 180.0F;
        dragon.setYRot(dragonYaw);
        dragon.setXRot(input.pitch * 0.35F);
        dragon.yBodyRot = dragonYaw;
        dragon.yHeadRot = dragonYaw;
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);

        Vec3 look = Vec3.directionFromRotation(input.pitch, yaw).normalize();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() <= 1.0E-4D) {
            forward = Vec3.directionFromRotation(0.0F, yaw).normalize();
        } else {
            forward = forward.normalize();
        }
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        Vec3 horizontal = forward.scale(input.forward).add(right.scale(input.strafe));
        if (horizontal.lengthSqr() > 1.0E-4D) {
            horizontal = horizontal.normalize().scale(FLIGHT_SPEED);
        }

        double vertical = 0.0D;
        if (input.jump) {
            vertical += VERTICAL_SPEED;
        }
        if (Math.abs(input.forward) > 0.05F) {
            vertical += look.y * VERTICAL_SPEED * 1.35D;
        }
        if (horizontal.lengthSqr() <= 1.0E-4D && !input.jump) {
            vertical -= 0.02D;
        }

        Vec3 motion = new Vec3(horizontal.x, vertical, horizontal.z);
        dragon.setDeltaMovement(motion);
        dragon.setPos(dragon.getX() + motion.x, dragon.getY() + motion.y, dragon.getZ() + motion.z);
        dragon.setFightOrigin(BlockPos.containing(dragon.position()));
        dragon.hasImpulse = true;
        dragon.fallDistance = 0.0F;
        rider.fallDistance = 0.0F;
    }

    private static void mountHeroFront(EnderDragon dragon, HeroEntity hero) {
        prepareBoardingJean(dragon);
        hero.stopRiding();
        hero.getNavigation().stop();
        hero.setTarget(null);
        hero.setPose(Pose.SITTING);
        if (!hero.isNoAi()) {
            hero.getPersistentData().putBoolean(HERO_FRONT_SEAT_NO_AI_TAG, true);
            hero.setNoAi(true);
        }
        hero.getPersistentData().putBoolean(HERO_FRONT_SEAT_TAG, true);
        dragon.getPersistentData().putUUID(JEAN_FRONT_HERO_TAG, hero.getUUID());
        dragon.getPersistentData().putLong(JEAN_FRONT_HERO_TIME_TAG, dragon.level().getGameTime());
        positionBoardingHero(dragon, hero);
    }

    private static void positionMountedPair(EnderDragon dragon, ServerPlayer rider) {
        HeroEntity hero = getFrontHero(dragon);
        if (hero != null) {
            positionMountedHero(dragon, hero);
        }
        positionEntityOnSeat(dragon, rider, PLAYER_FRONT_OFFSET, PLAYER_SIDE_OFFSET, PLAYER_SEAT_Y_OFFSET, false);
        rider.fallDistance = 0.0F;
    }

    private static void positionBoardingHero(EnderDragon dragon, HeroEntity hero) {
        positionHeroOnSeat(dragon, hero, HERO_BOARDING_FRONT_OFFSET, HERO_BOARDING_SIDE_OFFSET);
    }

    private static void positionMountedHero(EnderDragon dragon, HeroEntity hero) {
        positionHeroOnSeat(dragon, hero, HERO_MOUNTED_BACK_OFFSET, HERO_MOUNTED_SIDE_OFFSET);
    }

    private static void positionHeroOnSeat(EnderDragon dragon, HeroEntity hero, double forwardOffset, double sideOffset) {
        hero.getNavigation().stop();
        hero.setDeltaMovement(Vec3.ZERO);
        hero.setTarget(null);
        hero.setPose(Pose.SITTING);
        positionEntityOnSeat(dragon, hero, forwardOffset, sideOffset, HERO_SEAT_Y_OFFSET, true);
        hero.fallDistance = 0.0F;
    }

    private static void positionEntityOnSeat(EnderDragon dragon, Entity entity, double forwardOffset, double sideOffset, double yOffset, boolean alignYaw) {
        Vec3 forward = jeanModelForward(dragon);
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x).normalize();
        Vec3 seat = dragon.position()
                .add(forward.scale(forwardOffset))
                .add(right.scale(sideOffset))
                .add(0.0D, yOffset, 0.0D);
        entity.setPos(seat.x, seat.y, seat.z);
        if (alignYaw) {
            float riderYaw = jeanRiderYaw(dragon);
            entity.setYRot(riderYaw);
            entity.setYHeadRot(riderYaw);
            if (entity instanceof HeroEntity hero) {
                hero.setYBodyRot(riderYaw);
            }
        }
    }

    private static Vec3 jeanModelForward(EnderDragon dragon) {
        return Vec3.directionFromRotation(0.0F, dragon.getYRot() + 180.0F).normalize();
    }

    private static float jeanRiderYaw(EnderDragon dragon) {
        return dragon.getYRot() + 180.0F;
    }

    private static void prepareBoardingJean(EnderDragon dragon) {
        prepareJeanDragonBase(dragon);
        if (dragon.getPersistentData().getBoolean(JEAN_MOUNT_NO_AI_TAG)) {
            dragon.getPersistentData().remove(JEAN_MOUNT_NO_AI_TAG);
            dragon.setNoAi(false);
        }
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        dragon.getPersistentData().putBoolean("HeroSubmission", true);
        dragon.setSilent(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setTarget(null);
        dragon.hasImpulse = true;
    }

    private static void prepareMountedJean(EnderDragon dragon) {
        prepareJeanDragonBase(dragon);
        if (dragon.getPersistentData().getBoolean(JEAN_MOUNT_NO_AI_TAG)) {
            dragon.getPersistentData().remove(JEAN_MOUNT_NO_AI_TAG);
            dragon.setNoAi(false);
        }
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        dragon.setSilent(false);
        dragon.getPersistentData().putBoolean("HeroSubmission", false);
        dragon.setTarget(null);
    }

    private static void prepareJeanDragonBase(EnderDragon dragon) {
        dragon.addTag("herobrine_companion_jean");
        dragon.setDragonFight(null);
        dragon.setFightOrigin(BlockPos.containing(dragon.position()));
    }

    private static void releaseMountedJean(EnderDragon dragon) {
        if (dragon.getPersistentData().getBoolean(JEAN_MOUNT_NO_AI_TAG)) {
            dragon.getPersistentData().remove(JEAN_MOUNT_NO_AI_TAG);
            dragon.setNoAi(false);
            dragon.setSilent(false);
            dragon.setDeltaMovement(Vec3.ZERO);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        }
    }

    private static void clearMountedJean(EnderDragon dragon) {
        HeroEntity hero = getFrontHero(dragon);
        if (hero != null) {
            hero.getPersistentData().remove(HERO_FRONT_SEAT_TAG);
            hero.stopRiding();
            if (hero.getPersistentData().getBoolean(HERO_FRONT_SEAT_NO_AI_TAG)) {
                hero.getPersistentData().remove(HERO_FRONT_SEAT_NO_AI_TAG);
                hero.setNoAi(false);
            }
            hero.setPose(Pose.STANDING);
            hero.setDeltaMovement(Vec3.ZERO);
        }
        for (Entity passenger : dragon.getPassengers()) {
            passenger.stopRiding();
        }
        dragon.getPersistentData().remove(JEAN_FRONT_HERO_TAG);
        dragon.getPersistentData().remove(JEAN_FRONT_HERO_TIME_TAG);
        dragon.getPersistentData().remove(JEAN_PLAYER_RIDDEN_TAG);
        dragon.getPersistentData().putBoolean("HeroSubmission", false);
        releaseMountedJean(dragon);
    }

    private static boolean canPlayerBoardJean(EnderDragon dragon, ServerPlayer player) {
        if (HerobrineFamilyWorldData.get(player.serverLevel()).hasTrialPassed(HerobrineFamilyMemberType.JEAN, player.getUUID())) {
            return true;
        }

        if (!(dragon instanceof AwakenedMobAccessor accessor)) {
            return false;
        }

        AwakenedPlayerMemory memory = accessor.herobrineCompanion$getOrCreatePlayerMemory(player.getUUID());
        AwakenedMobRelationState relation = memory.relationState();
        return relation == AwakenedMobRelationState.FAMILIAR || relation == AwakenedMobRelationState.SUBMISSIVE;
    }

    private static HeroEntity findEligibleHero(EnderDragon dragon, ServerPlayer player) {
        if (dragon.level() instanceof ServerLevel dragonLevel) {
            UUID activeHeroId = HeroWorldData.get(dragonLevel).getActiveHeroUUID(player.getUUID());
            if (activeHeroId != null) {
                for (ServerLevel level : dragonLevel.getServer().getAllLevels()) {
                    Entity activeHero = level.getEntity(activeHeroId);
                    if (activeHero instanceof HeroEntity hero && isEligibleFrontHero(hero, player)) {
                        return moveHeroToDragonLevel(hero, dragonLevel, dragon);
                    }
                }
            }
        }

        return dragon.level().getEntitiesOfClass(HeroEntity.class, dragon.getBoundingBox().inflate(96.0D),
                        hero -> isEligibleFrontHero(hero, player))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(dragon), right.distanceToSqr(dragon)))
                .orElse(null);
    }

    private static HeroEntity moveHeroToDragonLevel(HeroEntity hero, ServerLevel dragonLevel, EnderDragon dragon) {
        if (hero.level() == dragonLevel) {
            return hero;
        }

        Vec3 pos = dragon.position().add(0.0D, HERO_SEAT_Y_OFFSET, 0.0D);
        DimensionTransition transition = new DimensionTransition(
                dragonLevel,
                pos,
                Vec3.ZERO,
                hero.getYRot(),
                hero.getXRot(),
                DimensionTransition.DO_NOTHING
        );
        Entity moved = hero.changeDimension(transition);

        return moved instanceof HeroEntity movedHero && isEligibleFrontHero(movedHero, null) ? movedHero : null;
    }

    private static boolean isEligibleFrontHero(HeroEntity hero, ServerPlayer player) {
        return hero.isAlive()
                && !hero.isRemoved()
                && (player == null || hero.getOwnerUUID() == null || hero.getOwnerUUID().equals(player.getUUID()));
    }

    private static HeroEntity getFrontHero(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel serverLevel) || !dragon.getPersistentData().hasUUID(JEAN_FRONT_HERO_TAG)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(dragon.getPersistentData().getUUID(JEAN_FRONT_HERO_TAG));
        if (entity instanceof HeroEntity hero && hero.isAlive() && !hero.isRemoved()) {
            return hero;
        }
        dragon.getPersistentData().remove(JEAN_FRONT_HERO_TAG);
        return null;
    }

    private static void detachLegacyHeroPassenger(EnderDragon dragon) {
        HeroEntity firstHero = null;
        for (Entity passenger : dragon.getPassengers()) {
            if (passenger instanceof HeroEntity hero) {
                if (firstHero == null && hero.isAlive() && !hero.isRemoved()) {
                    firstHero = hero;
                }
                hero.stopRiding();
            }
        }

        if (firstHero != null && !dragon.getPersistentData().hasUUID(JEAN_FRONT_HERO_TAG)) {
            dragon.getPersistentData().putUUID(JEAN_FRONT_HERO_TAG, firstHero.getUUID());
            dragon.getPersistentData().putLong(JEAN_FRONT_HERO_TIME_TAG, dragon.level().getGameTime());
            positionBoardingHero(dragon, firstHero);
        }
    }

    private static Player getPlayerRider(EnderDragon dragon) {
        for (Entity passenger : dragon.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static EnderDragon resolveDragon(Entity target) {
        if (target instanceof EnderDragon dragon) {
            return dragon;
        }
        if (target instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return null;
    }

    private static boolean isJean(EnderDragon dragon) {
        return HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN);
    }

    private record MountInput(float strafe, float forward, boolean jump, boolean dismount, float yaw, float pitch, long gameTime) {
        private static MountInput idle(long gameTime, float yaw, float pitch) {
            return new MountInput(0.0F, 0.0F, false, false, yaw, pitch, gameTime);
        }
    }
}
