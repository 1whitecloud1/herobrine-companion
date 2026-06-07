package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class AwakenedMobNameplateRenderer {
    private static final int NAME_COLOR = 0xFFFF0000;
    private static final double MAX_NAMEPLATE_DISTANCE_SQR = 4096.0D;

    private AwakenedMobNameplateRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, int packedLight) {
        render(poseStack, buffer, entity, packedLight, 0.0F);
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, int packedLight, float partialTick) {
        if (!(entity instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
            return;
        }

        renderForced(poseStack, buffer, entity, packedLight, partialTick, NAME_COLOR);
    }

    public static void renderForced(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, int packedLight, int color) {
        renderForced(poseStack, buffer, entity, packedLight, 0.0F, color);
    }

    public static void renderForced(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, int packedLight, float partialTick, int color) {
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
        Vec3 nameTagAnchor = getNameTagAnchor(entity, partialTick);

        poseStack.pushPose();
        poseStack.translate(nameTagAnchor.x, nameTagAnchor.y + 0.5D, nameTagAnchor.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float backgroundOpacity = minecraft.options.getBackgroundOpacity(0.25F);
        int background = (int) (backgroundOpacity * 255.0F) << 24;
        float xOffset = -font.width(displayName) / 2.0F;
        Font.DisplayMode displayMode = isSneaking ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        font.drawInBatch(displayName, xOffset, 0.0F, color, false, matrix, buffer,
                displayMode, background, packedLight);

        if (isSneaking) {
            font.drawInBatch(displayName, xOffset, 0.0F, -1, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }

    private static Vec3 getNameTagAnchor(LivingEntity entity, float partialTick) {
        Vec3 anchor = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
        return anchor == null ? new Vec3(0.0D, entity.getBbHeight(), 0.0D) : anchor;
    }
}
