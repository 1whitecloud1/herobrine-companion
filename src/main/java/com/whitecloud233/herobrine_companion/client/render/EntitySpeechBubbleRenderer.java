package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class EntitySpeechBubbleRenderer {
    private static final int MAX_LINE_WIDTH = 140;
    private static final int NAME_COLOR = 0xFFE0C88E;
    private static final int TEXT_COLOR = 0xFFF4F4F4;
    private static final int LINE_HEIGHT = 10;

    private EntitySpeechBubbleRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity livingEntity, int packedLight) {
        render(poseStack, bufferSource, livingEntity, packedLight, 0.0F);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity livingEntity, int packedLight, float partialTick) {
        if (!(livingEntity instanceof SpeechBubbleAccessor accessor) || !accessor.herobrineCompanion$hasSpeechBubble()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.distanceToSqr(livingEntity) > 1024.0D) {
            return;
        }

        Component speech = accessor.herobrineCompanion$getSpeechBubble();
        if (speech == null) {
            return;
        }

        Font font = minecraft.font;
        List<RenderedLine> renderedLines = buildRenderedLines(livingEntity, speech, font);
        if (renderedLines.isEmpty()) {
            return;
        }

        Vec3 nameTagAnchor = getNameTagAnchor(livingEntity, partialTick);
        poseStack.pushPose();
        poseStack.translate(nameTagAnchor.x, nameTagAnchor.y + 0.85F, nameTagAnchor.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        int background = ((int) (minecraft.options.getBackgroundOpacity(0.35F) * 255.0F) << 24);
        float totalHeight = renderedLines.size() * LINE_HEIGHT;
        float y = -totalHeight;

        for (RenderedLine line : renderedLines) {
            float x = -line.width() / 2.0F;
            font.drawInBatch(line.sequence(), x, y, line.color(), false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, background, packedLight);
            y += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    private static List<RenderedLine> buildRenderedLines(LivingEntity entity, Component speech, Font font) {
        List<RenderedLine> lines = new ArrayList<>();
        if (entity.hasCustomName() && !isAwakenedMob(entity)) {
            FormattedCharSequence nameLine = entity.getDisplayName().getVisualOrderText();
            lines.add(new RenderedLine(nameLine, font.width(nameLine), NAME_COLOR));
        }

        for (FormattedCharSequence sequence : font.split(speech, MAX_LINE_WIDTH)) {
            lines.add(new RenderedLine(sequence, font.width(sequence), TEXT_COLOR));
        }
        return lines;
    }

    private static boolean isAwakenedMob(LivingEntity entity) {
        return entity instanceof AwakenedMobAccessor accessor && accessor.herobrineCompanion$isAwakenedMob();
    }

    private static Vec3 getNameTagAnchor(LivingEntity entity, float partialTick) {
        Vec3 anchor = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
        return anchor == null ? new Vec3(0.0D, entity.getBbHeight(), 0.0D) : anchor;
    }

    private record RenderedLine(FormattedCharSequence sequence, int width, int color) {
    }
}
