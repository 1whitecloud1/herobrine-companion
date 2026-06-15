package com.whitecloud233.modid.herobrine_companion.entity.awakened.containment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class AwakenedVesselFeedback {
    private AwakenedVesselFeedback() {
    }

    public static void captureSuccess(ServerPlayer player, Mob mob, String name) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.awakened_vessel.capture.success", displayName(name)));
        if (mob.level() instanceof ServerLevel level) {
            level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.85F, 1.35F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(), 32, 0.35D, 0.45D, 0.35D, 0.05D);
        }
    }

    public static void captureFailure(ServerPlayer player, AwakenedMobCaptureService.Failure failure) {
        player.sendSystemMessage(Component.translatable(captureFailureKey(failure)));
    }

    public static void releaseSuccess(ServerPlayer player, ServerLevel level, Vec3 sourcePos, AwakenedMobReleaseService.ReleaseResult result) {
        String key = result.jeanSubmission()
                ? "message.herobrine_companion.awakened_vessel.release.jean_submission"
                : "message.herobrine_companion.awakened_vessel.release.success";
        player.sendSystemMessage(Component.translatable(key, displayName(result.releasedName())));
        level.playSound(null, BlockPos.containing(sourcePos), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.95F, 0.9F);
        level.sendParticles(ParticleTypes.PORTAL, sourcePos.x, sourcePos.y + 0.8D, sourcePos.z, 36, 0.45D, 0.45D, 0.45D, 0.06D);
    }

    public static void releaseFailure(ServerPlayer player, AwakenedMobCaptureService.Failure failure) {
        player.sendSystemMessage(Component.translatable(releaseFailureKey(failure)));
    }

    private static String captureFailureKey(AwakenedMobCaptureService.Failure failure) {
        if (failure == AwakenedMobCaptureService.Failure.ALREADY_FULL) {
            return "message.herobrine_companion.awakened_vessel.capture.already_full";
        }
        if (failure == AwakenedMobCaptureService.Failure.NOT_AWAKENED) {
            return "message.herobrine_companion.awakened_vessel.capture.not_awakened";
        }
        return "message.herobrine_companion.awakened_vessel.capture.unsupported";
    }

    private static String releaseFailureKey(AwakenedMobCaptureService.Failure failure) {
        if (failure == AwakenedMobCaptureService.Failure.EMPTY) {
            return "message.herobrine_companion.awakened_vessel.release.empty";
        }
        if (failure == AwakenedMobCaptureService.Failure.NO_SPACE) {
            return "message.herobrine_companion.awakened_vessel.release.no_space";
        }
        return "message.herobrine_companion.awakened_vessel.release.invalid_data";
    }

    private static String displayName(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }
}
