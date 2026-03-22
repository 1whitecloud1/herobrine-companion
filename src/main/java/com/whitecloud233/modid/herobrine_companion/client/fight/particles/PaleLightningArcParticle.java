package com.whitecloud233.modid.herobrine_companion.client.fight.particles;

import com.finderfeed.fdlib.util.FDColor;
import com.finderfeed.fdlib.util.math.FDMathUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PaleLightningArcParticle extends Particle {

    private final Vec3 absoluteEndPos;
    private final List<Vec3> mainPath;
    private final float baseRadius = 0.02f; // 雷电网相对较细

    public PaleLightningArcParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos) {
        super(level, startX, startY, startZ);
        this.absoluteEndPos = endPos;

        // 雷电网寿命较短，瞬间产生并快速消散
        this.lifetime = 12;
        this.hasPhysics = false;

        this.setBoundingBox(new AABB(startX, startY, startZ, endPos.x, endPos.y, endPos.z).inflate(1.0));

        Vec3 startPos = new Vec3(this.x, this.y, this.z);
        Random r = new Random();

        // 生成曲折的闪电路径
        int segments = Math.max(5, (int)(startPos.distanceTo(endPos) * 4));
        float wanderStep = 0.3f;
        this.mainPath = buildJaggedPath(startPos, endPos, segments, wanderStep, r);
    }

    private List<Vec3> buildJaggedPath(Vec3 start, Vec3 end, int segments, float stepOffset, Random r) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);
        float step = 1.0f / segments;
        Vec3 currentWander = Vec3.ZERO;

        for (float p = step; p < 1.0f; p += step) {
            Vec3 basePoint = FDMathUtil.interpolateVectors(start, end, p);
            float dx = (r.nextFloat() * 2 - 1) * stepOffset;
            float dy = (r.nextFloat() * 2 - 1) * stepOffset;
            float dz = (r.nextFloat() * 2 - 1) * stepOffset;
            currentWander = currentWander.add(dx, dy, dz);

            // 添加包络线让中段偏离更大，两端收束
            float envelope = (float) Math.sin(p * Math.PI);
            path.add(basePoint.add(currentWander.scale(envelope)));
        }
        path.add(end);
        return path;
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f mat = new Matrix4f().identity();

        // 计算消散透明度 (最后 5 tick 开始变淡)
        float alpha = 1.0f;
        int fadeStartTick = lifetime - 5;
        if (this.age > fadeStartTick) {
            alpha = 1.0f - ((float) (this.age - fadeStartTick) / 5.0f);
        }

        // 保持纯白雷电的设定
        FDColor coreColor = new FDColor(1.0f, 1.0f, 1.0f, 0.95f * alpha);
        FDColor glowColor = new FDColor(0.8f, 0.9f, 1.0f, 0.4f * alpha);

        int cylinderSegments = 5; // 性能考虑，雷电网截面段数可以稍微低一点

        // 渲染外发光层
        drawVolumetricCylinder(mat, vertex, cameraPos, this.mainPath, cylinderSegments, baseRadius * 2.5f, glowColor);
        // 渲染核心高光层
        drawVolumetricCylinder(mat, vertex, cameraPos, this.mainPath, cylinderSegments, baseRadius, coreColor);
    }

    private void drawVolumetricCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> path, int cylinderSegments, float radius, FDColor col) {
        int totalSegments = path.size() - 1;
        if (totalSegments <= 0) return;

        for (int i = 0; i < totalSegments; i++) {
            Vec3 p1Abs = path.get(i);
            Vec3 p2Abs = path.get(i + 1);

            Vec3 p1 = p1Abs.subtract(cameraPos);
            Vec3 p2 = p2Abs.subtract(cameraPos);

            Vec3 dir = p2.subtract(p1);
            if (dir.lengthSqr() < 0.001) continue;

            List<Vec3> prevRing = generateRing(p1, dir, cylinderSegments, radius);
            List<Vec3> nextRing = generateRing(p2, dir, cylinderSegments, radius);

            for (int j = 0; j < cylinderSegments; j++) {
                Vec3 p1a = prevRing.get(j);
                Vec3 p1b = prevRing.get((j + 1) % cylinderSegments);
                Vec3 p2a = nextRing.get(j);
                Vec3 p2b = nextRing.get((j + 1) % cylinderSegments);

                addVertex(vertex, transform, p1a, col);
                addVertex(vertex, transform, p1b, col);
                addVertex(vertex, transform, p2b, col);
                addVertex(vertex, transform, p2a, col);
            }
        }
    }

    private List<Vec3> generateRing(Vec3 center, Vec3 axis, int segments, float radius) {
        List<Vec3> ring = new ArrayList<>();
        Vector3f axis3f = FDMathUtil.vec3ToVector3f(axis).normalize();
        Vector3f u;
        if (Math.abs(axis3f.y) < 0.999f) {
            u = axis3f.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f()).normalize();
        } else {
            u = axis3f.cross(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f()).normalize();
        }
        Vector3f v = axis3f.cross(u, new Vector3f()).normalize();

        for (int i = 0; i < segments; i++) {
            float angle = (float)i / segments * FDMathUtil.FPI * 2;
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);

            Vector3f vertexPos = FDMathUtil.vec3ToVector3f(center);
            vertexPos.add(u.mul(cos * radius, new Vector3f()));
            vertexPos.add(v.mul(sin * radius, new Vector3f()));
            ring.add(FDMathUtil.vector3fToVec3(vertexPos));
        }
        return ring;
    }

    private void addVertex(VertexConsumer vertex, Matrix4f mat, Vec3 pos, FDColor col) {
        vertex.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z).color(col.r, col.g, col.b, col.a).endVertex();
    }

    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Nullable
        @Override
        public void begin(BufferBuilder tesselator, TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLightningShader);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        @Override
        public void end(Tesselator tesselator) { tesselator.end(); }
        @Override
        public String toString() { return "PALE_ARC"; }
    };

    @Override
    public ParticleRenderType getRenderType() { return RENDER_TYPE; }
}