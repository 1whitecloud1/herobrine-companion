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
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class DestructionGodOrbParticle extends Particle {
    private final Vec3 impactPos;
    private final int fallTicks;
    private final float startRadius;
    private final float maxRadius;
    private final float apexHeight;

    private record ParticleColor(float r, float g, float b, float a) {}

    public DestructionGodOrbParticle(ClientLevel level, Vec3 startPos, Vec3 impactPos, int fallTicks, float startRadius, float maxRadius, float apexHeight) {
        super(level, startPos.x, startPos.y, startPos.z);
        this.impactPos = impactPos;
        this.fallTicks = Math.max(20, fallTicks);
        this.startRadius = startRadius;
        this.maxRadius = maxRadius;
        this.apexHeight = apexHeight;
        this.lifetime = this.fallTicks + 6;
        this.hasPhysics = false;
        float inflate = Math.max(maxRadius, 4.0F) * 2.0F;
        this.setBoundingBox(new AABB(startPos.x, startPos.y, startPos.z, impactPos.x, impactPos.y, impactPos.z).inflate(inflate));
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        float progress = Mth.clamp((this.age + partialTicks) / this.fallTicks, 0.0F, 1.0F);
        Vec3 center = sampleCenter(progress).subtract(camera.getPosition());
        float radius = currentRadius(progress);
        float alpha = progress < 0.1F ? progress / 0.1F : 1.0F - Math.max(0.0F, progress - 0.92F) / 0.08F;
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);
        Matrix4f matrix = new Matrix4f().identity();

        ParticleColor outer = new ParticleColor(0.68F, 0.84F, 1.0F, 0.30F * alpha);
        ParticleColor inner = new ParticleColor(0.90F, 0.96F, 1.0F, 0.58F * alpha);
        ParticleColor core = new ParticleColor(1.0F, 1.0F, 1.0F, 0.92F * alpha);

        drawOrbShell(matrix, vertex, center, radius, outer, inner, core);
    }

    private Vec3 sampleCenter(float progress) {
        Vec3 start = new Vec3(this.x, this.y, this.z);
        Vec3 mid = start.lerp(this.impactPos, 0.5D).add(0.0D, this.apexHeight, 0.0D);
        double inv = 1.0D - progress;
        return start.scale(inv * inv).add(mid.scale(2.0D * inv * progress)).add(this.impactPos.scale(progress * progress));
    }

    private float currentRadius(float progress) {
        float eased = progress * progress;
        return Mth.lerp(eased, this.startRadius, this.maxRadius);
    }

    private void drawOrbShell(Matrix4f matrix, VertexConsumer vertex, Vec3 center, float radius, ParticleColor outer, ParticleColor inner, ParticleColor core) {
        drawBand(matrix, vertex, center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), radius, radius * 0.16F, outer);
        drawBand(matrix, vertex, center, new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), radius, radius * 0.16F, outer);
        drawBand(matrix, vertex, center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), radius, radius * 0.16F, outer);

        drawBand(matrix, vertex, center, normalize(new Vec3(1.0D, 1.0D, 0.0D)), new Vec3(0.0D, 0.0D, 1.0D), radius * 0.82F, radius * 0.11F, inner);
        drawBand(matrix, vertex, center, normalize(new Vec3(1.0D, 0.0D, 1.0D)), new Vec3(0.0D, 1.0D, 0.0D), radius * 0.78F, radius * 0.11F, inner);
        drawBand(matrix, vertex, center, normalize(new Vec3(0.0D, 1.0D, 1.0D)), new Vec3(1.0D, 0.0D, 0.0D), radius * 0.74F, radius * 0.10F, inner);

        drawBand(matrix, vertex, center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), radius * 0.40F, radius * 0.20F, core);
        drawBand(matrix, vertex, center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), radius * 0.32F, radius * 0.18F, core);
    }

    private void drawBand(Matrix4f matrix, VertexConsumer vertex, Vec3 center, Vec3 u, Vec3 v, float radius, float thickness, ParticleColor color) {
        Vec3 nu = normalize(u);
        Vec3 nv = normalize(v);
        int segments = 36;
        float inner = Math.max(0.02F, radius - thickness);
        for (int i = 0; i < segments; i++) {
            double a1 = i / (double) segments * Math.PI * 2.0D;
            double a2 = (i + 1) / (double) segments * Math.PI * 2.0D;
            Vec3 p1Outer = center.add(nu.scale(Math.cos(a1) * radius)).add(nv.scale(Math.sin(a1) * radius));
            Vec3 p1Inner = center.add(nu.scale(Math.cos(a1) * inner)).add(nv.scale(Math.sin(a1) * inner));
            Vec3 p2Inner = center.add(nu.scale(Math.cos(a2) * inner)).add(nv.scale(Math.sin(a2) * inner));
            Vec3 p2Outer = center.add(nu.scale(Math.cos(a2) * radius)).add(nv.scale(Math.sin(a2) * radius));
            addVertex(vertex, matrix, p1Outer, color);
            addVertex(vertex, matrix, p1Inner, color);
            addVertex(vertex, matrix, p2Inner, color);
            addVertex(vertex, matrix, p2Outer, color);
        }
    }

    private void addVertex(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
        vertex.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).setColor(color.r, color.g, color.b, color.a);
    }

    private Vec3 normalize(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : vector.normalize();
    }

    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLightningShader);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public String toString() {
            return "DESTRUCTION_GOD_ORB";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }
}

