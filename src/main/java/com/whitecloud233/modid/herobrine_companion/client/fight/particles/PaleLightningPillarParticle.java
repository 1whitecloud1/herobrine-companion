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

public class PaleLightningPillarParticle extends Particle {

    private final Vec3 absoluteEndPos;
    private final float diameter;
    private final int seed;

    private final List<Vec3> mainPath;
    private final List<LightningBranch> branches = new ArrayList<>();

    public PaleLightningPillarParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos, float diameter) {
        super(level, startX, startY, startZ);
        this.absoluteEndPos = endPos;
        this.diameter = diameter;

        // 【修改】：总寿命加长到 45 tick
        // 25 (警告蓄力) + 5 (极速落下) + 15 (落地余波逸散)
        this.lifetime = 45;
        this.hasPhysics = false;
        this.seed = new Random().nextInt(100000);

        this.setBoundingBox(new AABB(startX, startY, startZ, endPos.x, endPos.y, endPos.z).inflate(diameter * 1.5f));

        Vec3 startPos = new Vec3(this.x, this.y, this.z);
        Random r = new Random(this.seed);

        // 1. 生成主干
        int mainSegments = 30;
        float mainWanderStep = this.diameter * 0.8f;
        this.mainPath = buildJaggedPath(startPos, this.absoluteEndPos, mainSegments, mainWanderStep, r, true);

        // 2. 生成分叉
        Vec3 mainDirection = this.absoluteEndPos.subtract(startPos).normalize();
        double totalDistance = startPos.distanceTo(this.absoluteEndPos);

        for (int i = 1; i < mainSegments - 2; i++) {
            if (r.nextFloat() < 0.40f) {
                Vec3 branchStart = this.mainPath.get(i);
                Vec3 randomSide = new Vec3(r.nextFloat() * 2 - 1, (r.nextFloat() * 2 - 1) * 0.3, r.nextFloat() * 2 - 1).normalize();
                Vec3 branchDir = mainDirection.add(randomSide.scale(1.5)).normalize();

                float branchLength = (float)(totalDistance * (0.15f + r.nextFloat() * 0.4f));
                Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));
                int branchSegments = Math.max(4, (int)(mainSegments * (branchLength / totalDistance)));

                List<Vec3> bPath = buildJaggedPath(branchStart, branchEnd, branchSegments, mainWanderStep * 0.6f, r, false);
                float startProgress = (float) i / mainSegments;
                float widthScale = 0.2f + r.nextFloat() * 0.25f;

                this.branches.add(new LightningBranch(bPath, widthScale, startProgress));
            }
        }
    }

    private List<Vec3> buildJaggedPath(Vec3 start, Vec3 end, int segments, float stepOffset, Random r, boolean isMainBolt) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);
        float step = 1.0f / segments;
        Vec3 currentWander = Vec3.ZERO;

        for (float p = step; p < 1.0f; p += step) {
            Vec3 basePoint = FDMathUtil.interpolateVectors(start, end, p);
            float dx = (r.nextFloat() * 2 - 1) * stepOffset;
            float dy = (r.nextFloat() * 2 - 1) * stepOffset * 0.2f;
            float dz = (r.nextFloat() * 2 - 1) * stepOffset;
            currentWander = currentWander.add(dx, dy, dz);

            if (isMainBolt) {
                float envelope = (float) Math.sin(p * Math.PI);
                path.add(basePoint.add(currentWander.scale(envelope)));
            } else {
                path.add(basePoint.add(currentWander));
            }
        }
        path.add(isMainBolt ? end : FDMathUtil.interpolateVectors(start, end, 1.0f).add(currentWander));
        return path;
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f mat = new Matrix4f().identity();

        float currentTime = this.age + partialTicks;

        // 【核心动画逻辑重构】：蓄力 -> 落下 -> 余波
        int delayDuration = 30; // 警告圈蓄力时间
        int fallDuration = 5;   // 极速下坠时间

        float fallProgress;
        if (currentTime < delayDuration) {
            fallProgress = 0.0f; // 还没开始落，悬停在天上
        } else if (currentTime < delayDuration + fallDuration) {
            fallProgress = (currentTime - delayDuration) / fallDuration; // 0.0 ~ 1.0 快速劈下
        } else {
            fallProgress = 1.0f; // 已经劈完
        }

        float totalAlphaFactor = 1.0f;
        int fadeStartTick = lifetime - 10;
        if (this.age > fadeStartTick) {
            totalAlphaFactor = 1.0f - ((float) (this.age - fadeStartTick) / 10.0f);
        }

        FDColor coreColor = new FDColor(1.0f, 1.0f, 1.0f, 0.95f * totalAlphaFactor);
        FDColor glowColor = new FDColor(0.6f, 0.8f, 1.0f, 0.35f * totalAlphaFactor);

        // ==========================================
        // 0. 渲染落地前的伤害警告圈
        // ==========================================
        if (fallProgress < 1.0f) {
            // 充能呼吸闪烁效果：随着接近落下时间，闪烁频率越来越快！
            float flashSpeed = 0.5f + (currentTime / delayDuration) * 3.0f;
            float warningAlpha = 0.2f + 0.8f * (float)Math.abs(Math.sin(currentTime * flashSpeed));
            FDColor warningColor = new FDColor(1.0f, 1.0f, 1.0f, warningAlpha * totalAlphaFactor);

            // 精确 3.0f 半径警告圈
            drawWarningRing(mat, vertex, cameraPos, this.absoluteEndPos, 3.0f, warningColor);
        }

        // ==========================================
        // 1 & 2. 渲染主雷电与分叉 (只有 fallProgress > 0 时才画出)
        // ==========================================
        if (fallProgress > 0.0f) {
            float mainRadius = this.diameter * 0.15f;
            int mainCylinderSegments = 8;
            drawVolumetricCylinder(mat, vertex, cameraPos, this.mainPath, mainCylinderSegments, mainRadius * 2.8f, mainRadius * 1.8f, glowColor, fallProgress);
            drawVolumetricCylinder(mat, vertex, cameraPos, this.mainPath, mainCylinderSegments, mainRadius, mainRadius * 0.7f, coreColor, fallProgress);

            int branchCylinderSegments = 4;
            for (LightningBranch branch : this.branches) {
                if (fallProgress >= branch.startProgress) {
                    float branchFallProgress = (fallProgress - branch.startProgress) / (1.0f - branch.startProgress);
                    branchFallProgress = Math.min(1.0f, branchFallProgress * 2.5f);

                    float bBaseRadius = mainRadius * branch.widthScale;
                    drawVolumetricCylinder(mat, vertex, cameraPos, branch.path, branchCylinderSegments, bBaseRadius * 2.5f, 0.0f, glowColor, branchFallProgress);
                    drawVolumetricCylinder(mat, vertex, cameraPos, branch.path, branchCylinderSegments, bBaseRadius, 0.0f, coreColor, branchFallProgress);
                }
            }
        }

        // ==========================================
        // 3. 落地逸散特效 (保留)
        // ==========================================
        if (fallProgress >= 1.0f && this.age < (fadeStartTick + 5)) {
            drawGroundDissipation(mat, vertex, cameraPos, glowColor);
        }
    }

    // =======================================================
    // 伤害警告圈 (Warning Ring) 渲染逻辑
    // =======================================================
    private void drawWarningRing(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, Vec3 centerAbs, float radius, FDColor col) {
        // 【修复防遮挡】：抬高 0.1 格防止被地面方块彻底吞没
        Vec3 center = centerAbs.subtract(cameraPos).add(0, 0.1, 0);
        int segments = 24;
        float lineWidth = 0.3f;

        float rOut = radius;
        float rIn = radius - lineWidth;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * FDMathUtil.FPI * 2;
            float angle2 = (float) (i + 1) / segments * FDMathUtil.FPI * 2;

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            Vec3 p1Out = center.add(cos1 * rOut, 0, sin1 * rOut);
            Vec3 p1In  = center.add(cos1 * rIn, 0, sin1 * rIn);
            Vec3 p2Out = center.add(cos2 * rOut, 0, sin2 * rOut);
            Vec3 p2In  = center.add(cos2 * rIn, 0, sin2 * rIn);

            addVertex(vertex, transform, p1Out, col);
            addVertex(vertex, transform, p1In, col);
            addVertex(vertex, transform, p2In, col);
            addVertex(vertex, transform, p2Out, col);
        }
    }

    // =======================================================
    // 体积圆柱体渲染器
    // =======================================================
    private void drawVolumetricCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> path, int cylinderSegments, float baseRadius, float tipRadius, FDColor col, float fallProgress) {
        int totalSegments = path.size() - 1;
        if (totalSegments <= 0) return;

        int maxSegmentsToDraw = (int)Math.max(1, totalSegments * fallProgress);
        maxSegmentsToDraw = Math.min(maxSegmentsToDraw, totalSegments);

        for (int i = 0; i < maxSegmentsToDraw; i++) {
            Vec3 p1Abs = path.get(i);
            Vec3 p2Abs = path.get(i + 1);

            Vec3 p1 = p1Abs.subtract(cameraPos);
            Vec3 p2 = p2Abs.subtract(cameraPos);

            Vec3 dir = p2.subtract(p1);
            if (dir.lengthSqr() < 0.001) continue;

            float r1 = baseRadius + (tipRadius - baseRadius) * ((float) i / totalSegments);
            float r2 = baseRadius + (tipRadius - baseRadius) * ((float) (i + 1) / totalSegments);

            List<Vec3> prevRing = generateRing(p1, dir, cylinderSegments, r1);
            List<Vec3> nextRing = generateRing(p2, dir, cylinderSegments, r2);

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

        if (radius <= 0.001f) {
            for (int i=0; i<segments; i++) ring.add(center);
            return ring;
        }

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

    // ================== 底部逸散特效 ==================
    private void drawGroundDissipation(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, FDColor glowCol) {
        int rootsAmount = 5;
        float groundTime = (float) this.age;

        FDColor dissipationGlowCol = new FDColor(0.9f, 0.95f, 1.0f, glowCol.a * 1.5f);
        FDColor coreCol = new FDColor(1.0f, 1.0f, 1.0f, glowCol.a * 0.8f);

        for (int i = 0; i < rootsAmount; i++) {
            long rootSeed = this.seed + i * 12345L + (long)(groundTime * 1.5f);
            Random r = new Random(rootSeed);

            float rootHeight = r.nextFloat() * 1.5f;
            float dissipationSpread = diameter * 0.8f;

            double offsetX = (r.nextDouble() * 2 - 1) * dissipationSpread;
            double offsetZ = (r.nextDouble() * 2 - 1) * dissipationSpread;

            Vec3 dissipationEndAbsPos = this.absoluteEndPos.add(offsetX, rootHeight, offsetZ);

            int segments = 4;
            float randomSpread = 0.5f;
            List<Vec3> path = buildJaggedPath(this.absoluteEndPos, dissipationEndAbsPos, segments, randomSpread, r, false);

            int dissipationCylinderSegments = 4;
            float bBaseRadius = diameter * 0.08f;
            float bTipRadius = 0.0f;

            drawVolumetricCylinder(transform, vertex, cameraPos, path, dissipationCylinderSegments, bBaseRadius * 2.0f, bTipRadius, dissipationGlowCol, 1.0f);
            drawVolumetricCylinder(transform, vertex, cameraPos, path, dissipationCylinderSegments, bBaseRadius, bTipRadius, coreCol, 1.0f);
        }
    }

    // ================== 分支数据结构 ==================
    private static class LightningBranch {
        final List<Vec3> path;
        final float widthScale;
        final float startProgress;

        LightningBranch(List<Vec3> path, float widthScale, float startProgress) {
            this.path = path;
            this.widthScale = widthScale;
            this.startProgress = startProgress;
        }
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
        public String toString() { return "PALE_PILLAR_WARNING"; }
    };

    @Override
    public ParticleRenderType getRenderType() { return RENDER_TYPE; }
}