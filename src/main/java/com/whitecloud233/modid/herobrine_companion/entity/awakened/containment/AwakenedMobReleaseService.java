package com.whitecloud233.modid.herobrine_companion.entity.awakened.containment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

public final class AwakenedMobReleaseService {
    private AwakenedMobReleaseService() {
    }

    public static ReleaseResult releaseFromBlock(ServerPlayer player, ItemStack vessel, ServerLevel level, BlockPos clickedPos, Direction face) {
        return release(player, vessel, level, AwakenedMobReleaseContext.block(clickedPos, face));
    }

    public static ReleaseResult releaseFromAir(ServerPlayer player, ItemStack vessel, ServerLevel level) {
        return release(player, vessel, level, AwakenedMobReleaseContext.air());
    }

    private static ReleaseResult release(ServerPlayer player, ItemStack vessel, ServerLevel level, AwakenedMobReleaseContext context) {
        Optional<CapturedAwakenedMob> capturedOptional = AwakenedVesselStorage.read(vessel);
        if (capturedOptional.isEmpty()) {
            return ReleaseResult.failed(AwakenedMobCaptureService.Failure.EMPTY);
        }

        CapturedAwakenedMob captured = capturedOptional.get();
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(captured.entityTypeId());
        if (entityType == null) {
            return ReleaseResult.failed(AwakenedMobCaptureService.Failure.INVALID_DATA);
        }

        Entity entity = entityType.create(level);
        if (!(entity instanceof Mob mob)) {
            return ReleaseResult.failed(AwakenedMobCaptureService.Failure.INVALID_DATA);
        }

        CompoundTag entityData = captured.copyEntityData();
        AwakenedMobTransportSanitizer.sanitizeCapturedData(entityData);
        mob.load(entityData);
        mob.setUUID(UUID.randomUUID());
        AwakenedMobTransportSanitizer.sanitizeReleasedMob(mob);

        Optional<Vec3> releasePos = context.block()
                ? AwakenedMobReleasePositioner.findForBlockUse(level, mob, context.clickedPos(), context.face())
                : AwakenedMobReleasePositioner.findForAirUse(level, mob, player);
        if (releasePos.isEmpty()) {
            return ReleaseResult.failed(AwakenedMobCaptureService.Failure.NO_SPACE);
        }

        Vec3 pos = releasePos.get();
        mob.moveTo(pos.x, pos.y, pos.z, player.getYRot(), mob.getXRot());
        mob.setPersistenceRequired();
        boolean jeanSubmission = JeanSubmissionReleaseEffect.applyIfJean(level, mob, captured, player, pos);

        if (!level.addFreshEntity(mob)) {
            return ReleaseResult.failed(AwakenedMobCaptureService.Failure.SPAWN_FAILED);
        }

        AwakenedVesselStorage.clear(vessel);
        return ReleaseResult.success(captured.capturedName(), jeanSubmission);
    }

    private record AwakenedMobReleaseContext(boolean block, BlockPos clickedPos, Direction face) {
        private static AwakenedMobReleaseContext block(BlockPos clickedPos, Direction face) {
            return new AwakenedMobReleaseContext(true, clickedPos, face);
        }

        private static AwakenedMobReleaseContext air() {
            return new AwakenedMobReleaseContext(false, BlockPos.ZERO, Direction.UP);
        }
    }

    public record ReleaseResult(boolean success, AwakenedMobCaptureService.Failure failure, String releasedName, boolean jeanSubmission) {
        public static ReleaseResult success(String releasedName, boolean jeanSubmission) {
            return new ReleaseResult(true, null, releasedName, jeanSubmission);
        }

        public static ReleaseResult failed(AwakenedMobCaptureService.Failure failure) {
            return new ReleaseResult(false, failure, "", false);
        }
    }
}
