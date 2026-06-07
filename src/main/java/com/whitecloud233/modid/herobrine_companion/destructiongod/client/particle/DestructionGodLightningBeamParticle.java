package com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class DestructionGodLightningBeamParticle extends Particle {
    private static final int CHARGE_TICKS = 56;
    private static final int BURST_TICKS = 5;
    private static final int SUSTAIN_TICKS = 18;
    private static final int FADE_TICKS = 22;
    private final Vec3 absoluteEndPos;
    private final float diameter;
    private final List<Vec3> path;

    private static class ParticleColor {
        private final float r;
        private final float g;
        private final float b;
        private final float a;

        private ParticleColor(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    public DestructionGodLightningBeamParticle(ClientLevel level, Vec3 startPos, Vec3 endPos, float diameter) {
        super(level, startPos.x, startPos.y, startPos.z);
        this.absoluteEndPos = endPos;
        this.diameter = diameter;
        this.lifetime = CHARGE_TICKS + BURST_TICKS + SUSTAIN_TICKS + FADE_TICKS;
        this.hasPhysics = false;
        this.setBoundingBox(new AABB(startPos.x, startPos.y, startPos.z, endPos.x, endPos.y, endPos.z).inflate(diameter * 1.8F));
        this.path = buildStraightPath(startPos, endPos);
    }

    @Override
    public void tick() {
        super.tick();
        if ((this.age & 1) != 0 || this.age > CHARGE_TICKS + BURST_TICKS + SUSTAIN_TICKS) {
            return;
        }

        Vec3 center = new Vec3(this.x, this.y, this.z);
        Vec3 axis = this.absoluteEndPos.subtract(center);
        if (axis.lengthSqr() < 1.0E-4D) {
            axis = new Vec3(0.0D, 1.0D, 0.0D);
        }
        axis = axis.normalize();
        Vec3 lateral = Math.abs(axis.y) < 0.98D ? axis.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize() : axis.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 vertical = axis.cross(lateral).normalize();
        float charge = Mth.clamp(this.age / (float) CHARGE_TICKS, 0.0F, 1.0F);
        Vec3 portalCenter = center.add(axis.scale(this.diameter * 0.045D));
        double outerRadius = this.diameter * (2.45D - charge * 0.42D);
        double innerRadius = this.diameter * (0.84D - charge * 0.10D);

        emitSpatialCollapseParticles(portalCenter, axis, lateral, vertical, charge);

        for (int i = 0; i < 16; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double offsetRadius = Mth.lerp(this.random.nextDouble(), innerRadius, outerRadius);
            Vec3 ringOffset = lateral.scale(Math.cos(angle) * offsetRadius).add(vertical.scale(Math.sin(angle) * offsetRadius));
            Vec3 spawn = portalCenter.add(ringOffset).add(axis.scale((this.random.nextDouble() - 0.5D) * this.diameter * 0.22D));
            Vec3 pull = portalCenter.subtract(spawn).scale(0.22D + charge * 0.18D);
            this.level.addParticle(ParticleTypes.REVERSE_PORTAL, spawn.x, spawn.y, spawn.z, pull.x, pull.y, pull.z);
            if ((i & 1) == 0 || charge > 0.72F) {
                this.level.addParticle(ParticleTypes.END_ROD, spawn.x, spawn.y, spawn.z, pull.x * 0.46D, pull.y * 0.46D, pull.z * 0.46D);
            }
            if (i % 3 == 0) {
                this.level.addParticle(ParticleTypes.ELECTRIC_SPARK, spawn.x, spawn.y, spawn.z, pull.x * 0.25D, pull.y * 0.25D, pull.z * 0.25D);
            }
        }

        if ((this.age % 5) == 0) {
            this.level.addParticle(ParticleTypes.FLASH, portalCenter.x, portalCenter.y, portalCenter.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private void emitSpatialCollapseParticles(Vec3 portalCenter, Vec3 axis, Vec3 lateral, Vec3 vertical, float charge) {
        float collapseProgress = Mth.clamp(charge / 0.85F, 0.0F, 1.0F);
        double collapseOuter = this.diameter * (3.25D - collapseProgress * 1.05D);
        double collapseInner = this.diameter * (1.55D - collapseProgress * 0.45D);
        int rimSamples = 10 + Mth.floor(collapseProgress * 8.0F);

        for (int i = 0; i < rimSamples; i++) {
            double angle = (Math.PI * 2.0D * i / rimSamples) + this.age * 0.07D;
            double radius = Mth.lerp(this.random.nextDouble(), collapseInner, collapseOuter);
            Vec3 rim = lateral.scale(Math.cos(angle) * radius).add(vertical.scale(Math.sin(angle) * radius));
            Vec3 spawn = portalCenter.add(rim).add(axis.scale((this.random.nextDouble() - 0.5D) * this.diameter * 0.35D));
            Vec3 pull = portalCenter.subtract(spawn).scale(0.26D + charge * 0.18D);
            this.level.addParticle(ParticleTypes.REVERSE_PORTAL, spawn.x, spawn.y, spawn.z, pull.x, pull.y, pull.z);
            if ((i & 1) == 0) {
                this.level.addParticle(ParticleTypes.POOF, spawn.x, spawn.y, spawn.z, pull.x * 0.18D, pull.y * 0.18D, pull.z * 0.18D);
            }
            if (collapseProgress > 0.35F && i % 3 == 0) {
                this.level.addParticle(ParticleTypes.END_ROD, spawn.x, spawn.y, spawn.z, pull.x * 0.30D, pull.y * 0.30D, pull.z * 0.30D);
            }
        }
    }

    private List<Vec3> buildStraightPath(Vec3 start, Vec3 end) {
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        return points;
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f matrix = new Matrix4f().identity();
        float age = this.age + partialTicks;
        float chargeProgress = Mth.clamp(age / (float) CHARGE_TICKS, 0.0F, 1.0F);
        float whiteHoleAlpha = Mth.clamp(0.14F + chargeProgress * 0.96F, 0.0F, 1.0F);
        float beamProgress;
        float radiusScale;
        if (age <= CHARGE_TICKS) {
            beamProgress = Mth.clamp((chargeProgress - 0.12F) / 0.88F, 0.0F, 1.0F) * (0.22F + chargeProgress * 0.26F);
            radiusScale = Mth.lerp(chargeProgress, 0.045F, 0.14F);
        } else if (age <= CHARGE_TICKS + BURST_TICKS) {
            float burstProgress = Mth.clamp((age - CHARGE_TICKS) / (float) BURST_TICKS, 0.0F, 1.0F);
            beamProgress = 1.0F;
            radiusScale = 1.95F - burstProgress * 0.55F;
        } else if (age <= CHARGE_TICKS + BURST_TICKS + SUSTAIN_TICKS) {
            beamProgress = 1.0F;
            radiusScale = 1.4F;
        } else {
            float fadeProgress = (age - CHARGE_TICKS - BURST_TICKS - SUSTAIN_TICKS) / (float) FADE_TICKS;
            beamProgress = 1.0F - fadeProgress;
            radiusScale = Mth.lerp(Mth.clamp(fadeProgress, 0.0F, 1.0F), 1.4F, 0.58F);
        }
        beamProgress = Mth.clamp(beamProgress, 0.0F, 1.0F);
        radiusScale = Math.max(0.02F, radiusScale);

        ParticleColor pullGlow = new ParticleColor(0.70F, 0.85F, 1.0F, (0.16F + chargeProgress * 0.32F) * whiteHoleAlpha);
        renderWhiteHoleInflow(matrix, vertex, cameraPos, age, chargeProgress, pullGlow);

        if (beamProgress <= 0.0F) {
            return;
        }

        ParticleColor outerGlow = new ParticleColor(0.78F, 0.90F, 1.0F, (0.28F + radiusScale * 0.12F) * beamProgress);
        ParticleColor innerGlow = new ParticleColor(0.92F, 0.97F, 1.0F, (0.50F + radiusScale * 0.14F) * beamProgress);
        ParticleColor core = new ParticleColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, (0.72F + radiusScale * 0.16F) * beamProgress));
        drawCylinder(matrix, vertex, cameraPos, this.path, 12, this.diameter * 0.58F * radiusScale, outerGlow);
        drawCylinder(matrix, vertex, cameraPos, this.path, 10, this.diameter * 0.31F * radiusScale, innerGlow);
        drawCylinder(matrix, vertex, cameraPos, this.path, 8, this.diameter * 0.11F * radiusScale, core);
    }

    private void renderWhiteHoleInflow(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, float age, float chargeProgress, ParticleColor color) {
        Vec3 center = new Vec3(this.x, this.y, this.z);
        Vec3 axis = this.absoluteEndPos.subtract(center);
        if (axis.lengthSqr() < 1.0E-4D) {
            axis = new Vec3(0.0D, 1.0D, 0.0D);
        }
        axis = axis.normalize();
        Vec3 lateral = Math.abs(axis.y) < 0.98D ? axis.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize() : axis.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 vertical = axis.cross(lateral).normalize();
        float pulse = 0.92F + 0.08F * (float) Math.sin(age * 0.22F);
        float collapseRadius = this.diameter * (2.15F - chargeProgress * 0.62F) * pulse;
        float portalRadius = this.diameter * (1.34F - chargeProgress * 0.18F);
        float rimOuterRadius = portalRadius * 1.24F;
        float coreRadius = this.diameter * (0.30F + (1.0F - chargeProgress) * 0.08F);
        Vec3 portalCenter = center.add(axis.scale(this.diameter * 0.045D));
        drawRingPlane(transform, vertex, cameraPos, portalCenter.subtract(axis.scale(this.diameter * 0.05D)), lateral, vertical, collapseRadius * 0.76F, collapseRadius, 24,
                new ParticleColor(0.54F, 0.72F, 1.0F, color.a * (0.55F + (1.0F - chargeProgress) * 0.35F)));
        drawRingPlane(transform, vertex, cameraPos, portalCenter.subtract(axis.scale(this.diameter * 0.02D)), lateral, vertical, portalRadius * 0.40F, portalRadius * 1.06F, 22,
                new ParticleColor(0.40F, 0.58F, 0.96F, color.a * 0.48F));
        drawRingPlane(transform, vertex, cameraPos, portalCenter, axis, lateral, vertical, portalRadius * 0.62F, rimOuterRadius, 20,
                new ParticleColor(0.72F, 0.88F, 1.0F, color.a * 1.15F));
        drawRingPlane(transform, vertex, cameraPos, portalCenter.add(axis.scale(this.diameter * 0.03D)), axis, lateral, vertical, coreRadius, portalRadius * 0.82F, 18,
                new ParticleColor(0.94F, 0.98F, 1.0F, Math.min(1.0F, color.a * 1.45F)));
        drawDiscPlane(transform, vertex, cameraPos, portalCenter.add(axis.scale(this.diameter * 0.01D)), axis, lateral, vertical, coreRadius * 0.92F,
                new ParticleColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, color.a * 1.7F)));
    }

    private void drawCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> points, int cylinderSegments, float radius, ParticleColor color) {
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i).subtract(cameraPos);
            Vec3 p2 = points.get(i + 1).subtract(cameraPos);
            Vec3 axis = p2.subtract(p1);
            if (axis.lengthSqr() < 0.001D) {
                continue;
            }
            List<Vec3> ringA = generateRing(p1, axis, cylinderSegments, radius);
            List<Vec3> ringB = generateRing(p2, axis, cylinderSegments, radius);
            for (int j = 0; j < cylinderSegments; j++) {
                Vec3 a1 = ringA.get(j);
                Vec3 a2 = ringA.get((j + 1) % cylinderSegments);
                Vec3 b2 = ringB.get((j + 1) % cylinderSegments);
                Vec3 b1 = ringB.get(j);
                addVertex(vertex, transform, a1, color);
                addVertex(vertex, transform, a2, color);
                addVertex(vertex, transform, b2, color);
                addVertex(vertex, transform, b1, color);
            }
        }
    }


    private List<Vec3> generateRing(Vec3 center, Vec3 axis, int segments, float radius) {
        List<Vec3> ring = new ArrayList<>();
        Vector3f axis3f = toVector3f(axis).normalize();
        Vector3f u = Math.abs(axis3f.y) < 0.999F
                ? axis3f.cross(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f()).normalize()
                : axis3f.cross(new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f()).normalize();
        Vector3f v = axis3f.cross(u, new Vector3f()).normalize();
        for (int i = 0; i < segments; i++) {
            float angle = (float) i / segments * (float) Math.PI * 2.0F;
            Vector3f pos = toVector3f(center);
            pos.add(u.mul((float) Math.cos(angle) * radius, new Vector3f()));
            pos.add(v.mul((float) Math.sin(angle) * radius, new Vector3f()));
            ring.add(toVec3(pos));
        }
        return ring;
    }

    private void addVertex(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
        vertex.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(color.r, color.g, color.b, color.a).endVertex();
    }

    private void drawRingPlane(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, Vec3 center, Vec3 axis, Vec3 lateral, Vec3 vertical, float innerRadius, float outerRadius, int segments, ParticleColor color) {
        drawRingPlane(matrix, vertex, cameraPos, center, lateral, vertical, innerRadius, outerRadius, segments, color);
    }

    private void drawRingPlane(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, Vec3 center, Vec3 lateral, Vec3 vertical, float innerRadius, float outerRadius, int segments, ParticleColor color) {
        if (outerRadius <= innerRadius || segments < 3) {
            return;
        }
        Vec3 renderCenter = center.subtract(cameraPos);
        for (int i = 0; i < segments; i++) {
            double angleA = Math.PI * 2.0D * i / segments;
            double angleB = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 outerA = planePoint(renderCenter, lateral, vertical, outerRadius, angleA);
            Vec3 outerB = planePoint(renderCenter, lateral, vertical, outerRadius, angleB);
            Vec3 innerB = planePoint(renderCenter, lateral, vertical, innerRadius, angleB);
            Vec3 innerA = planePoint(renderCenter, lateral, vertical, innerRadius, angleA);
            addVertex(vertex, matrix, outerA, color);
            addVertex(vertex, matrix, outerB, color);
            addVertex(vertex, matrix, innerB, color);
            addVertex(vertex, matrix, innerA, color);
        }
    }

    private void drawDiscPlane(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, Vec3 center, Vec3 axis, Vec3 lateral, Vec3 vertical, float radius, ParticleColor color) {
        drawRingPlane(matrix, vertex, cameraPos, center, axis, lateral, vertical, 0.0F, radius, 18, color);
    }

    private Vec3 planePoint(Vec3 center, Vec3 lateral, Vec3 vertical, float radius, double angle) {
        return center.add(lateral.scale(Math.cos(angle) * radius)).add(vertical.scale(Math.sin(angle) * radius));
    }

    private static Vector3f toVector3f(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private static Vec3 toVec3(Vector3f vec) {
        return new Vec3(vec.x, vec.y, vec.z);
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
        public void end(Tesselator tesselator) {
            tesselator.end();
        }

        @Override
        public String toString() {
            return "DESTRUCTION_GOD_LIGHTNING_BEAM";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }
}

