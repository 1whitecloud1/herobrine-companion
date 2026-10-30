package com.whitecloud233.modid.herobrine_companion.fight.particles;

import com.whitecloud233.modid.herobrine_companion.client.particle.ParticleGeometry;

import com.whitecloud233.modid.herobrine_companion.client.particle.ParticleColor;

import net.minecraft.util.Mth;

import com.whitecloud233.modid.herobrine_companion.client.render.GlowTrailRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PaleLightningPillarParticle extends Particle {

    private final Vec3 absoluteEndPos;
    private final float diameter;
    private final int seed;
    private final boolean immediate;

    private final List<Vec3> mainPath;
    private final List<LightningBranch> branches = new ArrayList<>();

    public PaleLightningPillarParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos, float diameter) {
        this(level, startX, startY, startZ, endPos, diameter, false);
    }

    public PaleLightningPillarParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos, float diameter, boolean immediate) {
        super(level, startX, startY, startZ);
        this.absoluteEndPos = endPos;
        this.diameter = diameter;

        this.immediate = immediate;
        this.lifetime = immediate ? 10 : 45;
        this.hasPhysics = false;
        this.seed = new Random().nextInt(100000);

        this.setBoundingBox(new AABB(startX, startY, startZ, endPos.x, endPos.y, endPos.z).inflate(diameter * 1.5f));

        Vec3 startPos = this.getPos();
        Random r = new Random(this.seed);

        int mainSegments = 30;
        float mainWanderStep = this.diameter * 0.8f;
        this.mainPath = buildJaggedPath(startPos, this.absoluteEndPos, mainSegments, mainWanderStep, r, true);

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
            Vec3 basePoint = start.lerp(end, p);
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
        path.add(isMainBolt ? end : start.lerp(end, 1.0f).add(currentWander));
        return path;
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f mat = new Matrix4f().identity();

        float currentTime = this.age + partialTicks;

        int delayDuration = 30;
        float fallProgress = fallProgress(immediate, currentTime);

        float totalAlphaFactor = 1.0f;
        int fadeStartTick = lifetime - 10;
        if (this.age > fadeStartTick) {
            totalAlphaFactor = 1.0f - ((float) (this.age - fadeStartTick) / 10.0f);
        }

        ParticleColor coreColor = new ParticleColor(1.0f, 1.0f, 1.0f, 0.95f * totalAlphaFactor);
        ParticleColor glowColor = new ParticleColor(0.6f, 0.8f, 1.0f, 0.35f * totalAlphaFactor);

        if (!immediate && fallProgress < 1.0f) {
            float flashSpeed = 0.5f + (currentTime / delayDuration) * 3.0f;
            float warningAlpha = 0.2f + 0.8f * (float)Math.abs(Math.sin(currentTime * flashSpeed));
            ParticleColor warningColor = new ParticleColor(1.0f, 1.0f, 1.0f, warningAlpha * totalAlphaFactor);

            drawWarningRing(mat, vertex, cameraPos, this.absoluteEndPos, 3.0f, warningColor);
        }

        if (fallProgress > 0.0f) {
            float mainRadius = this.diameter * 0.15f;
            int mainCylinderSegments = 8;
            ParticleGeometry.taperedCylinder(mat, vertex, cameraPos, this.mainPath, mainCylinderSegments, mainRadius * 2.8f, mainRadius * 1.8f, glowColor, fallProgress);
            ParticleGeometry.taperedCylinder(mat, vertex, cameraPos, this.mainPath, mainCylinderSegments, mainRadius, mainRadius * 0.7f, coreColor, fallProgress);

            int branchCylinderSegments = 4;
            for (LightningBranch branch : this.branches) {
                if (fallProgress >= branch.startProgress) {
                    float branchFallProgress = (fallProgress - branch.startProgress) / (1.0f - branch.startProgress);
                    branchFallProgress = Math.min(1.0f, branchFallProgress * 2.5f);

                    float bBaseRadius = mainRadius * branch.widthScale;
                    ParticleGeometry.taperedCylinder(mat, vertex, cameraPos, branch.path, branchCylinderSegments, bBaseRadius * 2.5f, 0.0f, glowColor, branchFallProgress);
                    ParticleGeometry.taperedCylinder(mat, vertex, cameraPos, branch.path, branchCylinderSegments, bBaseRadius, 0.0f, coreColor, branchFallProgress);
                }
            }
        }

        if (!immediate && fallProgress >= 1.0f && this.age < (fadeStartTick + 5)) {
            drawGroundDissipation(mat, vertex, cameraPos, glowColor);
        }
    }

    public static float fallProgress(boolean immediate, float age) {
        return immediate ? 1.0F : Mth.clamp((age - 30.0F) / 5.0F, 0F, 1F);
    }

    private void drawWarningRing(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, Vec3 centerAbs, float radius, ParticleColor col) {
        Vec3 center = centerAbs.subtract(cameraPos).add(0, 0.1, 0);
        int segments = 24;
        float lineWidth = 0.3f;

        float rOut = radius;
        float rIn = radius - lineWidth;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * (float)Math.PI * 2;
            float angle2 = (float) (i + 1) / segments * (float)Math.PI * 2;

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            Vec3 p1Out = center.add(cos1 * rOut, 0, sin1 * rOut);
            Vec3 p1In  = center.add(cos1 * rIn, 0, sin1 * rIn);
            Vec3 p2Out = center.add(cos2 * rOut, 0, sin2 * rOut);
            Vec3 p2In  = center.add(cos2 * rIn, 0, sin2 * rIn);

            ParticleGeometry.vertex(vertex, transform, p1Out, col);
            ParticleGeometry.vertex(vertex, transform, p1In, col);
            ParticleGeometry.vertex(vertex, transform, p2In, col);
            ParticleGeometry.vertex(vertex, transform, p2Out, col);
        }
    }

    private void drawGroundDissipation(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, ParticleColor glowCol) {
        int rootsAmount = 5;
        float groundTime = (float) this.age;

        ParticleColor dissipationGlowCol = new ParticleColor(0.9f, 0.95f, 1.0f, glowCol.a() * 1.5f);
        ParticleColor coreCol = new ParticleColor(1.0f, 1.0f, 1.0f, glowCol.a() * 0.8f);

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

            ParticleGeometry.taperedCylinder(transform, vertex, cameraPos, path, dissipationCylinderSegments, bBaseRadius * 2.0f, bTipRadius, dissipationGlowCol, 1.0f);
            ParticleGeometry.taperedCylinder(transform, vertex, cameraPos, path, dissipationCylinderSegments, bBaseRadius, bTipRadius, coreCol, 1.0f);
        }
    }

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

    public static final ParticleRenderType RENDER_TYPE = GlowTrailRenderTypes.GLOW_LIGHTNING;

    @Override
    public ParticleRenderType getRenderType() { return RENDER_TYPE; }
}
