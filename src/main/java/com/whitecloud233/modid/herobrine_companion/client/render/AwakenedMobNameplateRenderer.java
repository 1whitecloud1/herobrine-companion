package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public final class AwakenedMobNameplateRenderer {
    private static final int NAME_COLOR = 0xFFFF0000;
    private static final double MAX_NAMEPLATE_DISTANCE_SQR = 4096.0D;

    private AwakenedMobNameplateRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, int packedLight) {
        if (!(entity instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
            return;
        }

        Component displayName = entity.getDisplayName();
        if (displayName == null || displayName.getString().isBlank()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.distanceToSqr(entity) > MAX_NAMEPLATE_DISTANCE_SQR) {
            return;
        }

        Font font = minecraft.font;
        boolean isSneaking = !entity.isDiscrete();

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.5D, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float backgroundOpacity = minecraft.options.getBackgroundOpacity(0.25F);
        int background = (int) (backgroundOpacity * 255.0F) << 24;
        float xOffset = -font.width(displayName) / 2.0F;
        Font.DisplayMode displayMode = isSneaking ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        font.drawInBatch(displayName, xOffset, 0.0F, NAME_COLOR, false, matrix, buffer,
                displayMode, background, packedLight);

        if (isSneaking) {
            font.drawInBatch(displayName, xOffset, 0.0F, -1, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }
}
