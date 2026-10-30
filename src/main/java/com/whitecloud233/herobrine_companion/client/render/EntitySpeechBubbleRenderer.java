package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
    private static final int BACKGROUND_COLOR = 0xA6000000;
    private static final int LINE_HEIGHT = 10;
    private static final int GLYPH_HEIGHT = 9;
    private static final float BACKGROUND_PADDING = 2.0F;
    private static final double DEFAULT_MAX_DISTANCE_SQR = 1024.0D;
    private static final double LARGE_BUBBLE_MAX_DISTANCE_SQR = 4096.0D;
    private static final float DEFAULT_TEXT_SCALE = 0.025F;
    private static final float LARGE_BUBBLE_TEXT_SCALE = 0.0625F;

    private EntitySpeechBubbleRenderer() {
    }

    /**
     * @param packedLight 保留仅为兼容既有调用方；气泡统一用全亮光照绘制，见下方说明
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity livingEntity, int packedLight) {
        render(poseStack, bufferSource, livingEntity, packedLight, 0.0F);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity livingEntity, int packedLight, float partialTick) {
        if (!(livingEntity instanceof SpeechBubbleAccessor accessor) || !accessor.herobrineCompanion$hasSpeechBubble()) {
            return;
        }

        boolean largeSpeechBubble = usesLargeSpeechBubble(livingEntity);
        double maxDistanceSqr = largeSpeechBubble ? LARGE_BUBBLE_MAX_DISTANCE_SQR : DEFAULT_MAX_DISTANCE_SQR;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.distanceToSqr(livingEntity) > maxDistanceSqr) {
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
        float textScale = largeSpeechBubble ? LARGE_BUBBLE_TEXT_SCALE : DEFAULT_TEXT_SCALE;
        poseStack.scale(textScale, -textScale, textScale);

        Matrix4f matrix = poseStack.last().pose();
        // 1.20.1 的 drawInBatch 会把背景四边形追加在全部字形顶点之后；SEE_THROUGH 关闭深度测试、
        // 按提交顺序叠加，传入背景色等于把黑底盖在字上（0xA6 会把字压暗到约 35%）。
        // 因此底衬自己先画，drawInBatch 传 0 背景色，保证文字永远在底衬之上。
        float totalHeight = renderedLines.size() * LINE_HEIGHT;
        float totalWidth = 0.0F;
        for (RenderedLine line : renderedLines) {
            totalWidth = Math.max(totalWidth, line.width());
        }
        // 最后一行文字底边恒为 -totalHeight + (n-1)*LINE_HEIGHT + GLYPH_HEIGHT = GLYPH_HEIGHT - LINE_HEIGHT
        drawBackground(matrix, bufferSource,
                -totalWidth / 2.0F - BACKGROUND_PADDING,
                -totalHeight - BACKGROUND_PADDING,
                totalWidth / 2.0F + BACKGROUND_PADDING,
                GLYPH_HEIGHT - LINE_HEIGHT + BACKGROUND_PADDING);

        float y = -totalHeight;
        for (RenderedLine line : renderedLines) {
            float x = -line.width() / 2.0F;
            // 全亮光照 + 阴影：光影包与夜晚会把实体自身光照压得很暗，直接导致气泡字看不清。
            font.drawInBatch(line.sequence(), x, y, line.color(), true, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            y += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    private static void drawBackground(Matrix4f matrix, MultiBufferSource bufferSource, float x0, float y0, float x1, float y1) {
        float a = (BACKGROUND_COLOR >>> 24) / 255.0F;
        float r = (BACKGROUND_COLOR >>> 16 & 0xFF) / 255.0F;
        float g = (BACKGROUND_COLOR >>> 8 & 0xFF) / 255.0F;
        float b = (BACKGROUND_COLOR & 0xFF) / 255.0F;
        // debugQuads：无纹理纯色四边形，与 SEE_THROUGH 文字同为无深度测试的覆盖层表现
        // 1.21.1 的 VertexConsumer 不再接受 Matrix4f 参数，改用 PoseStack 里的矩阵叠加顶点。
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(matrix);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.debugQuads());
        vertices.addVertex(pose, x0, y0, 0.0F).setColor(r, g, b, a);
        vertices.addVertex(pose, x0, y1, 0.0F).setColor(r, g, b, a);
        vertices.addVertex(pose, x1, y1, 0.0F).setColor(r, g, b, a);
        vertices.addVertex(pose, x1, y0, 0.0F).setColor(r, g, b, a);
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

    private static boolean usesLargeSpeechBubble(LivingEntity entity) {
        return entity instanceof HeroEntity || isAwakenedMob(entity);
    }

    private static Vec3 getNameTagAnchor(LivingEntity entity, float partialTick) {
        Vec3 anchor = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
        return anchor == null ? new Vec3(0.0D, entity.getBbHeight(), 0.0D) : anchor;
    }

    private record RenderedLine(FormattedCharSequence sequence, int width, int color) {
    }
}