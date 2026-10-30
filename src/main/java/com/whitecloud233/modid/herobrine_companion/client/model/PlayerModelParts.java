package com.whitecloud233.modid.herobrine_companion.client.model;

import net.minecraft.client.model.PlayerModel;

/** Synchronize a player's clothing with its own body parts, without resetting animation transforms. */
public final class PlayerModelParts {
    private PlayerModelParts() { }

    public static void syncOverlays(PlayerModel<?> model) {
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    public static void anchorY(PlayerModel<?> model, float offsetY) {
        model.body.y = offsetY;
        model.head.y = offsetY;
        model.hat.y = model.head.y;
        model.rightArm.y = 2.0F + offsetY;
        model.leftArm.y = 2.0F + offsetY;
        model.rightLeg.y = 12.0F + offsetY;
        model.leftLeg.y = 12.0F + offsetY;
        model.jacket.y = model.body.y;
        model.rightSleeve.y = model.rightArm.y;
        model.leftSleeve.y = model.leftArm.y;
        model.rightPants.y = model.rightLeg.y;
        model.leftPants.y = model.leftLeg.y;
    }
}
