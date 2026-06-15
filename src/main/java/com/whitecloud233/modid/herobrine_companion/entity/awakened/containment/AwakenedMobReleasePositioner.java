package com.whitecloud233.modid.herobrine_companion.entity.awakened.containment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class AwakenedMobReleasePositioner {
    private AwakenedMobReleasePositioner() {
    }

    public static Optional<Vec3> findForBlockUse(ServerLevel level, Mob mob, BlockPos clickedPos, Direction face) {
        BlockPos base = clickedPos.relative(face);
        int upRange = mob instanceof EnderDragon ? 24 : 4;
        return search(level, mob, base, upRange, 1);
    }

    public static Optional<Vec3> findForAirUse(ServerLevel level, Mob mob, Player player) {
        double distance = mob instanceof EnderDragon ? 10.0D : 3.0D;
        Vec3 target = player.getEyePosition().add(player.getLookAngle().normalize().scale(distance));
        BlockPos base = BlockPos.containing(target);
        int upRange = mob instanceof EnderDragon ? 24 : 4;
        int downRange = mob instanceof EnderDragon ? 8 : 3;
        return search(level, mob, base, upRange, downRange);
    }

    private static Optional<Vec3> search(ServerLevel level, Mob mob, BlockPos base, int upRange, int downRange) {
        Optional<Vec3> exact = tryPosition(level, mob, base);
        if (exact.isPresent()) {
            return exact;
        }

        for (int up = 1; up <= upRange; up++) {
            Optional<Vec3> candidate = tryPosition(level, mob, base.above(up));
            if (candidate.isPresent()) {
                return candidate;
            }
        }

        for (int down = 1; down <= downRange; down++) {
            Optional<Vec3> candidate = tryPosition(level, mob, base.below(down));
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> tryPosition(ServerLevel level, Mob mob, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return Optional.empty();
        }

        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        mob.moveTo(center.x, center.y, center.z, mob.getYRot(), mob.getXRot());
        return level.noCollision(mob, mob.getBoundingBox()) ? Optional.of(center) : Optional.empty();
    }
}
