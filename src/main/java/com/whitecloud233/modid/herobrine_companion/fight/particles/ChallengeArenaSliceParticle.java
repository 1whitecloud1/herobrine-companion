package com.whitecloud233.modid.herobrine_companion.fight.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ChallengeArenaSliceParticle extends Particle {
    private static final ResourceLocation PORTAL_TEXTURE = new ResourceLocation(
            HerobrineCompanion.MODID,
            "textures/entity/end_ring_portal.png"
    );
    private static final float FALL_DISTANCE = 132.0F;

    private final Vec3 center;
    private final List<Vec3> capPoints = new ArrayList<>();
    private final int fallTicks;
    private final int holdTicks;

    public ChallengeArenaSliceParticle(ClientLevel level, Vec3 center, Vec3 capNormal, Vec3 cutDirection,
                                       float cutOffset, float arenaRadius, int fallTicks, int holdTicks) {
        super(level, center.x, center.y, center.z);
        Vec3 normal = new Vec3(capNormal.x, 0.0D, capNormal.z).normalize();
        Vec3 direction = new Vec3(cutDirection.x, 0.0D, cutDirection.z).normalize();
        this.center = center;
        this.fallTicks = Math.max(20, fallTicks);
        this.holdTicks = Math.max(1, holdTicks);
        this.lifetime = this.fallTicks + this.holdTicks;
        this.hasPhysics = false;

        float portalRadius = Math.min(50.0F, Math.max(8.0F, arenaRadius));
        if (cutOffset >= portalRadius - 0.01F) {
            return;
        }
        float radius = portalRadius;
        float clampedOffset = Mth.clamp(cutOffset, -radius + 1.0F, radius - 1.0F);
        double thetaStart = Math.asin(Mth.clamp(clampedOffset / radius, -1.0F, 1.0F));
        double thetaEnd = Math.PI - thetaStart;
        int segments = Math.max(24, Mth.ceil(radius * 0.8F));
        for (int i = 0; i <= segments; i++) {
            double theta = thetaStart + (thetaEnd - thetaStart) * i / (double) segments;
            Vec3 point = center
                    .add(direction.scale(Math.cos(theta) * radius))
                    .add(normal.scale(Math.sin(theta) * radius));
            this.capPoints.add(point);
        }

        this.setBoundingBox(new AABB(
                center.x - radius - 2.0D,
                center.y - FALL_DISTANCE - 2.0D,
                center.z - radius - 2.0D,
                center.x + radius + 2.0D,
                center.y + 2.0D,
                center.z + radius + 2.0D
        ));
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        float age = this.age + partialTicks;
        float progress = Mth.clamp(age / this.fallTicks, 0.0F, 1.0F);
        progress = progress * progress * (3.0F - 2.0F * progress);
        float fade = age <= this.fallTicks
                ? 1.0F
                : 1.0F - ((age - this.fallTicks) / this.holdTicks);
        fade = Mth.clamp(fade, 0.0F, 1.0F);
        if (fade <= 0.0F || this.capPoints.size() < 3) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        Matrix4f matrix = new Matrix4f().identity();
        float yOffset = -0.25F - FALL_DISTANCE * progress;
        this.drawCap(matrix, vertex, cameraPos, yOffset, 0.95F * fade);
        this.drawCap(matrix, vertex, cameraPos, yOffset - 0.08F, 0.55F * fade);
    }

    private void drawCap(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, float yOffset, float alpha) {
        Vec3 first = this.capPoints.get(0);
        for (int i = 1; i < this.capPoints.size() - 1; i++) {
            Vec3 a = first;
            Vec3 b = this.capPoints.get(i);
            Vec3 c = this.capPoints.get(i + 1);
            this.addVertex(vertex, matrix, cameraPos, a, yOffset, alpha);
            this.addVertex(vertex, matrix, cameraPos, b, yOffset, alpha);
            this.addVertex(vertex, matrix, cameraPos, c, yOffset, alpha);
        }
    }

    private void addVertex(VertexConsumer vertex, Matrix4f matrix, Vec3 cameraPos, Vec3 point, float yOffset, float alpha) {
        float radius = this.capPoints.size() > 0 ? this.radiusOf(point) : 1.0F;
        float u = 0.5F + (float) (point.x - this.center.x) / radius * 0.5F;
        float v = 0.5F + (float) (point.z - this.center.z) / radius * 0.5F;
        vertex.vertex(
                        matrix,
                        (float) (point.x - cameraPos.x),
                        (float) (point.y + yOffset - cameraPos.y),
                        (float) (point.z - cameraPos.z)
                )
                .uv(u, v)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .endVertex();
    }

    private float radiusOf(Vec3 point) {
        double dx = point.x - this.center.x;
        double dz = point.z - this.center.z;
        return (float) Math.max(8.0D, Math.sqrt(dx * dx + dz * dz));
    }

    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Nullable
        @Override
        public void begin(BufferBuilder tesselator, TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderTexture(0, PORTAL_TEXTURE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
        }

        @Override
        public String toString() {
            return "CHALLENGE_ARENA_SLICE";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }
}
