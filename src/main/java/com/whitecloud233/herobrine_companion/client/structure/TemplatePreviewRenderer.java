package com.whitecloud233.herobrine_companion.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

/**
 * 建筑预览的线框渲染（客户端）。
 *
 * <p><b>性能取舍</b>：只画 12 条棱 + 24 条角标记，共 <b>约 36 个薄面片</b>。
 * 不画方块、不做逐格高亮、不分配每帧集合——每帧分配量恒定，与建筑体积无关。
 * 143 万方块的建筑和 10 方块的小屋，渲染开销完全相同。</p>
 *
 * <p><b>为什么用 debugQuads</b>：项目已在 {@code EntitySpeechBubbleRenderer} 里用
 * {@code debugQuads} 画纯色几何，沿用它可避免引入第二套渲染约定；
 * 1.20.1 的 {@code RenderType.LINES} 还需要额外设置线宽与法线。</p>
 *
 * <p><b>单一职责</b>：只负责把 {@link TemplatePreviewBox} 画出来。</p>
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class TemplatePreviewRenderer {

    /** 线框颜色（半透明青色）。 */
    private static final int LINE_RGB = 0x35E0FF;

    /** 角标记长度（格）。 */
    private static final float CORNER_MARK = 2.0F;

    /** 线段近似厚度（格）。 */
    private static final float THICKNESS = 0.035F;

    /** 超过该距离（格）不画：远景建筑只有噪点，白耗顶点。 */
    private static final double MAX_DRAW_DISTANCE_SQR = 512.0D * 512.0D;

    private TemplatePreviewRenderer() {
    }

    /** 事件入口：在实体之后画线框；无预览时零开销返回。 */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (TemplatePreviewBox.current() == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        if (render(event.getPoseStack(), bufferSource, event.getCamera())) {
            bufferSource.endBatch();
        }
    }

    /** @return true 表示本帧确实提交了顶点（调用方负责 endBatch） */
    public static boolean render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        TemplatePreviewBox box = TemplatePreviewBox.current();
        if (box == null) {
            return false;
        }
        Vec3 cameraPos = camera.getPosition();
        AABB bounds = new AABB(
                box.min().getX(), box.min().getY(), box.min().getZ(),
                box.max().getX() + 1.0D, box.max().getY() + 1.0D, box.max().getZ() + 1.0D);
        if (bounds.getCenter().distanceToSqr(cameraPos) > MAX_DRAW_DISTANCE_SQR) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.debugQuads());

        float x0 = (float) (bounds.minX + 0.01D);
        float y0 = (float) (bounds.minY + 0.01D);
        float z0 = (float) (bounds.minZ + 0.01D);
        float x1 = (float) (bounds.maxX - 0.01D);
        float y1 = (float) (bounds.maxY - 0.01D);
        float z1 = (float) (bounds.maxZ - 0.01D);

        edge(vertices, pose, x0, y0, z0, x1, y0, z0);
        edge(vertices, pose, x0, y0, z1, x1, y0, z1);
        edge(vertices, pose, x0, y1, z0, x1, y1, z0);
        edge(vertices, pose, x0, y1, z1, x1, y1, z1);
        edge(vertices, pose, x0, y0, z0, x0, y0, z1);
        edge(vertices, pose, x1, y0, z0, x1, y0, z1);
        edge(vertices, pose, x0, y1, z0, x0, y1, z1);
        edge(vertices, pose, x1, y1, z0, x1, y1, z1);
        edge(vertices, pose, x0, y0, z0, x0, y1, z0);
        edge(vertices, pose, x1, y0, z0, x1, y1, z0);
        edge(vertices, pose, x0, y0, z1, x0, y1, z1);
        edge(vertices, pose, x1, y0, z1, x1, y1, z1);

        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                for (int sz = 0; sz < 2; sz++) {
                    float cx = sx == 0 ? x0 : x1;
                    float cy = sy == 0 ? y0 : y1;
                    float cz = sz == 0 ? z0 : z1;
                    float dx = sx == 0 ? CORNER_MARK : -CORNER_MARK;
                    float dy = sy == 0 ? CORNER_MARK : -CORNER_MARK;
                    float dz = sz == 0 ? CORNER_MARK : -CORNER_MARK;
                    edge(vertices, pose, cx, cy, cz, cx + dx, cy, cz);
                    edge(vertices, pose, cx, cy, cz, cx, cy + dy, cz);
                    edge(vertices, pose, cx, cy, cz, cx, cy, cz + dz);
                }
            }
        }
        poseStack.popPose();
        return true;
    }

    /** 画一条轴对齐的"线"（用薄面片近似，视觉上有宽度）。 */
    private static void edge(VertexConsumer vertices, Matrix4f pose,
                             float ax, float ay, float az, float bx, float by, float bz) {
        float t = THICKNESS;
        if (ay == by && az == bz) {
            quad(vertices, pose, ax, ay - t, az - t, bx, ay + t, az + t);
        } else if (ax == bx && az == bz) {
            quad(vertices, pose, ax - t, ay, az - t, ax + t, by, az + t);
        } else if (ax == bx && ay == by) {
            quad(vertices, pose, ax - t, ay - t, az, ax + t, ay + t, bz);
        } else {
            quad(vertices, pose, ax, ay - t, az - t, bx, by + t, bz + t);
        }
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float r = ((LINE_RGB >> 16) & 0xFF) / 255.0F;
        float g = ((LINE_RGB >> 8) & 0xFF) / 255.0F;
        float b = (LINE_RGB & 0xFF) / 255.0F;
        float a = 0.55F;
        vertices.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
        vertices.addVertex(pose, x0, y1, z0).setColor(r, g, b, a);
        vertices.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vertices.addVertex(pose, x1, y0, z1).setColor(r, g, b, a);
    }

    /** 清理（退出世界）。 */
    public static void clear() {
        TemplatePreviewBox.clear();
    }
}
