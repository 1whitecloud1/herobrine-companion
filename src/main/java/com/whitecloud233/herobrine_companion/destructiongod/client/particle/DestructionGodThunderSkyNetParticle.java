package com.whitecloud233.herobrine_companion.destructiongod.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class DestructionGodThunderSkyNetParticle extends Particle {
    private static final int PILLAR_CHARGE_TICKS = 24;
    private static final int PILLAR_BURST_TICKS = 6;
    private static final int PILLAR_FADE_TICKS = 18;
    private static final int NET_FADE_IN_TICKS = 18;

    private final Vec3 bodyCenter;
    private final Vec3 cloudCenter;
    private final float radius;
    private final int visualSeed;
    private final List<Vec3> nodes = new ArrayList<>();
    private final List<LightningEdge> edges = new ArrayList<>();
    private final Set<Long> edgeKeys = new HashSet<>();

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

    private static class LightningEdge {
        private final List<Vec3> path;
        private final float widthScale;
        private final float pulseOffset;

        private LightningEdge(List<Vec3> path, float widthScale, float pulseOffset) {
            this.path = path;
            this.widthScale = widthScale;
            this.pulseOffset = pulseOffset;
        }
    }

    public DestructionGodThunderSkyNetParticle(ClientLevel level, Vec3 bodyCenter, double cloudY, float radius, int lifetime, int seed) {
        super(level, bodyCenter.x, bodyCenter.y, bodyCenter.z);
        this.bodyCenter = bodyCenter;
        this.cloudCenter = new Vec3(bodyCenter.x, cloudY, bodyCenter.z);
        this.radius = Math.max(48.0F, radius);
        this.visualSeed = seed;
        this.lifetime = Math.max(80, lifetime);
        this.hasPhysics = false;
        this.setBoundingBox(new AABB(
                bodyCenter.x - this.radius - 24.0D,
                Math.min(bodyCenter.y, cloudY) - 8.0D,
                bodyCenter.z - this.radius - 24.0D,
                bodyCenter.x + this.radius + 24.0D,
                Math.max(bodyCenter.y, cloudY) + 16.0D,
                bodyCenter.z + this.radius + 24.0D
        ));
        this.buildSkyNet();
    }

    private void buildSkyNet() {
        Random random = new Random(this.visualSeed);
        this.nodes.add(this.cloudCenter.add(0.0D, 3.0D, 0.0D));
        int hubIndex = 0;
        List<Integer> previousRing = null;
        int[] ringCounts = new int[]{10, 16, 22, 30};
        float[] ringScales = new float[]{0.22F, 0.46F, 0.72F, 0.98F};

        for (int ring = 0; ring < ringCounts.length; ring++) {
            int count = ringCounts[ring];
            float scale = ringScales[ring];
            double ringRadius = this.radius * scale;
            double baseAngle = random.nextDouble() * Math.PI * 2.0D;
            List<Integer> currentRing = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                double angle = baseAngle + (Math.PI * 2.0D * i / count) + (random.nextDouble() - 0.5D) * (Math.PI * 2.0D / count) * 0.26D;
                double y = this.cloudCenter.y + Math.sin(angle * 2.0D + ring * 0.85D) * (2.5D + ring) + (random.nextDouble() - 0.5D) * 2.8D;
                Vec3 node = new Vec3(
                        this.cloudCenter.x + Math.cos(angle) * ringRadius,
                        y,
                        this.cloudCenter.z + Math.sin(angle) * ringRadius
                );
                this.nodes.add(node);
                currentRing.add(this.nodes.size() - 1);
            }

            for (int i = 0; i < currentRing.size(); i++) {
                int current = currentRing.get(i);
                int next = currentRing.get((i + 1) % currentRing.size());
                this.addEdge(current, next);
                if (i % 4 == 0) {
                    int skip = currentRing.get((i + 2) % currentRing.size());
                    this.addEdge(current, skip);
                }
            }

            if (previousRing != null) {
                for (int i = 0; i < currentRing.size(); i++) {
                    int mapped = Mth.floor((i / (double) currentRing.size()) * previousRing.size()) % previousRing.size();
                    this.addEdge(currentRing.get(i), previousRing.get(mapped));
                    if ((i & 1) == 0) {
                        this.addEdge(currentRing.get(i), previousRing.get((mapped + 1) % previousRing.size()));
                    }
                }
            } else {
                for (int index : currentRing) {
                    this.addEdge(hubIndex, index);
                }
            }

            if (ring == 0) {
                for (int i = 0; i < currentRing.size(); i += 4) {
                    this.addEdge(hubIndex, currentRing.get(i));
                }
            }

            previousRing = currentRing;
        }

        for (int i = 0; i < previousRing.size(); i += 4) {
            int a = previousRing.get(i);
            int b = previousRing.get((i + previousRing.size() / 3) % previousRing.size());
            this.addEdge(a, b);
        }
    }

    private void addEdge(int a, int b) {
        if (a == b) {
            return;
        }
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        long key = (((long) min) << 32) | (max & 0xFFFFFFFFL);
        if (this.edgeKeys.add(key)) {
            Random random = new Random(this.visualSeed * 31L + key);
            Vec3 start = this.nodes.get(a);
            Vec3 end = this.nodes.get(b);
            int segments = 4 + random.nextInt(3);
            double edgeLength = start.distanceTo(end);
            double wander = Math.max(this.radius * 0.018D, edgeLength * (0.055D + random.nextDouble() * 0.03D));
            this.edges.add(new LightningEdge(
                    buildJaggedPath(start, end, segments, wander, random),
                    0.9F + random.nextFloat() * 0.35F,
                    random.nextFloat() * ((float) Math.PI * 2.0F)
            ));
        }
    }

    private List<Vec3> buildJaggedPath(Vec3 start, Vec3 end, int segments, double wander, Random random) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 1.0E-4D) {
            path.add(end);
            return path;
        }

        Vec3 direction = axis.normalize();
        Vec3 tangent = Math.abs(direction.y) < 0.98D ? direction.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize() : direction.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 vertical = direction.cross(tangent).normalize();
        for (int i = 1; i < segments; i++) {
            double progress = i / (double) segments;
            Vec3 basePoint = start.lerp(end, progress);
            double envelope = Math.sin(progress * Math.PI);
            Vec3 offset = tangent.scale((random.nextDouble() * 2.0D - 1.0D) * wander * envelope)
                    .add(vertical.scale((random.nextDouble() * 2.0D - 1.0D) * wander * 0.32D * envelope));
            path.add(basePoint.add(offset));
        }
        path.add(end);
        return path;
    }

    @Override
    public void render(@NotNull VertexConsumer vertex, @NotNull Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f matrix = new Matrix4f().identity();
        float age = this.age + partialTicks;
        float fadeOut = age > this.lifetime - 26 ? 1.0F - ((age - (this.lifetime - 26.0F)) / 26.0F) : 1.0F;
        fadeOut = Mth.clamp(fadeOut, 0.0F, 1.0F);
        if (fadeOut <= 0.0F) {
            return;
        }

        this.renderSkyPillar(matrix, vertex, cameraPos, age, fadeOut);
        this.renderSkyNet(matrix, vertex, cameraPos, age, fadeOut);
    }

    private void renderSkyPillar(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, float age, float fadeOut) {
        float pillarTime = PILLAR_CHARGE_TICKS + PILLAR_BURST_TICKS + PILLAR_FADE_TICKS;
        if (age > pillarTime) {
            return;
        }

        float beamAlpha;
        float radiusScale;
        if (age <= PILLAR_CHARGE_TICKS) {
            float charge = age / (float) PILLAR_CHARGE_TICKS;
            beamAlpha = (0.22F + charge * 0.38F) * fadeOut;
            radiusScale = Mth.lerp(charge, 0.045F, 0.12F);
        } else if (age <= PILLAR_CHARGE_TICKS + PILLAR_BURST_TICKS) {
            float burst = (age - PILLAR_CHARGE_TICKS) / (float) PILLAR_BURST_TICKS;
            beamAlpha = fadeOut;
            radiusScale = Mth.lerp(burst, 0.16F, 1.28F);
        } else {
            float fade = (age - PILLAR_CHARGE_TICKS - PILLAR_BURST_TICKS) / (float) PILLAR_FADE_TICKS;
            beamAlpha = (1.0F - fade * 0.72F) * fadeOut;
            radiusScale = Mth.lerp(Mth.clamp(fade, 0.0F, 1.0F), 1.28F, 0.36F);
        }

        List<Vec3> pillarPath = new ArrayList<>(2);
        pillarPath.add(this.bodyCenter);
        pillarPath.add(this.cloudCenter);
        ParticleColor outer = new ParticleColor(0.72F, 0.88F, 1.0F, 0.32F * beamAlpha);
        ParticleColor inner = new ParticleColor(0.90F, 0.96F, 1.0F, 0.64F * beamAlpha);
        ParticleColor core = new ParticleColor(1.0F, 1.0F, 1.0F, 0.92F * beamAlpha);
        drawCylinder(matrix, vertex, cameraPos, pillarPath, 12, this.radius * 0.022F * radiusScale, outer);
        drawCylinder(matrix, vertex, cameraPos, pillarPath, 10, this.radius * 0.011F * radiusScale, inner);
        drawCylinder(matrix, vertex, cameraPos, pillarPath, 8, this.radius * 0.004F * radiusScale, core);
    }

    private void renderSkyNet(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, float age, float fadeOut) {
        float start = PILLAR_CHARGE_TICKS * 0.65F;
        float netProgress = Mth.clamp((age - start) / NET_FADE_IN_TICKS, 0.0F, 1.0F) * fadeOut;
        if (netProgress <= 0.0F) {
            return;
        }

        for (int i = 0; i < this.edges.size(); i++) {
            LightningEdge edge = this.edges.get(i);
            float pulse = 0.72F + 0.28F * (float) Math.sin(age * 0.11F + i * 0.43F + edge.pulseOffset);
            float alpha = (0.08F + pulse * 0.12F) * netProgress;
            float coreAlpha = (0.14F + pulse * 0.18F) * netProgress;
            float width = this.radius * 0.0024F * edge.widthScale;
            drawCylinder(matrix, vertex, cameraPos, edge.path, 5, width * 1.8F, new ParticleColor(0.62F, 0.82F, 1.0F, alpha));
            drawCylinder(matrix, vertex, cameraPos, edge.path, 4, width, new ParticleColor(0.96F, 0.98F, 1.0F, coreAlpha));
        }
    }

    private void drawCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> points, int cylinderSegments, float radius, ParticleColor color) {
        if (radius <= 0.0001F) {
            return;
        }
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
        vertex.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).setColor(color.r, color.g, color.b, color.a);
    }

    private static Vector3f toVector3f(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private static Vec3 toVec3(Vector3f vec) {
        return new Vec3(vec.x, vec.y, vec.z);
    }

    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(@NotNull Tesselator tesselator, @NotNull TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLightningShader);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public String toString() {
            return "DESTRUCTION_GOD_THUNDER_SKYNET";
        }
    };

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }
}


