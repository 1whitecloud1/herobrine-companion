package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class JeanDragonPassengerMixin {
    private static final String HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG = "HerobrineCompanionJeanFrontHero";
    private static final double HEROBRINE_COMPANION_JEAN_PLAYER_SEAT_Y = 5.65D;
    private static final double HEROBRINE_COMPANION_JEAN_PLAYER_SEAT = 4.2D;
    private static final double HEROBRINE_COMPANION_JEAN_PLAYER_SIDE = 0.0D;

    @Inject(method = "canAddPassenger(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$allowJeanPairPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof EnderDragon dragon) || !herobrineCompanion$isJean(dragon)) {
            return;
        }

        if (passenger instanceof Player) {
            cir.setReturnValue(dragon.getPersistentData().hasUUID(HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG)
                    && herobrineCompanion$getPlayerPassenger(self) == null);
        }
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$positionJeanPassenger(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof EnderDragon dragon) || !herobrineCompanion$isJean(dragon)) {
            return;
        }

        if (!(passenger instanceof Player)) {
            return;
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, dragon.getYRot() + 180.0F).normalize();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x).normalize();
        Vec3 seat = dragon.position()
                .add(forward.scale(HEROBRINE_COMPANION_JEAN_PLAYER_SEAT))
                .add(right.scale(HEROBRINE_COMPANION_JEAN_PLAYER_SIDE))
                .add(0.0D, HEROBRINE_COMPANION_JEAN_PLAYER_SEAT_Y, 0.0D);
        moveFunction.accept(passenger, seat.x, seat.y, seat.z);
        ci.cancel();
    }

    private boolean herobrineCompanion$isJean(EnderDragon dragon) {
        return HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN);
    }

    private Player herobrineCompanion$getPlayerPassenger(Entity self) {
        for (Entity passenger : self.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
