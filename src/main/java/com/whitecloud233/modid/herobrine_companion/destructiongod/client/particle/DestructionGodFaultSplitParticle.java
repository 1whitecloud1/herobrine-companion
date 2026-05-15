package com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.cinematic.ClientSpatialRendHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class DestructionGodFaultSplitParticle extends Particle {
    private static final int SPLIT_ANIMATION_TICKS = 96;
    private static final int FADE_OUT_TICKS = 28;
    private final Vec3 origin;
    private final Vec3 direction;
    private final Vec3 perpendicular;
    private final float length;
    private final float maxSplitOffset;
    private final int chargeTicks;
    private final List<Vec3> lineSamples = new ArrayList<>();
    private boolean splitShakeStarted = false;

    private record ParticleColor(float r, float g, float b, float a) {
    }

    public DestructionGodFaultSplitParticle(ClientLevel level, Vec3 origin, Vec3 direction, float length, int terrainHalfWidth, int splitDistance, int chargeTicks, int lifetime) {
        super(level, origin.x, origin.y, origin.z);
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        this.origin = origin;
        this.direction = flat.normalize();
        this.perpendicular = new Vec3(-this.direction.z, 0.0D, this.direction.x);
        this.length = Math.max(8.0F, length);
        this.maxSplitOffset = Mth.clamp(splitDistance * 0.5F, 0.55F, 0.80F);
        this.chargeTicks = Math.max(10, chargeTicks);
        this.lifetime = Math.max(this.chargeTicks + SPLIT_ANIMATION_TICKS + 8, lifetime);
        this.hasPhysics = false;

        Vec3 end = origin.add(this.direction.scale(this.length));
        this.cacheStraightLineSamples(level);
        this.updateBoundsFromSamples(end);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.splitShakeStarted && this.age >= this.chargeTicks) {
            this.splitShakeStarted = true;
            ClientSpatialRendHandler.startFaultSplitShake(1.15F, 28);
        }
    }

    @Override
    public void render(VertexConsumer vertex, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        Matrix4f matrix = new Matrix4f().identity();
        float age = this.age + partialTicks;
        float chargeProgress = Mth.clamp(age / this.chargeTicks, 0.0F, 1.0F);
        float splitProgress = age <= this.chargeTicks
                ? 0.0F
                : Mth.clamp((age - this.chargeTicks) / SPLIT_ANIMATION_TICKS, 0.0F, 1.0F);
        splitProgress = splitProgress * splitProgress * (3.0F - 2.0F * splitProgress);
        float fade = age > this.lifetime - FADE_OUT_TICKS
                ? 1.0F - ((age - (this.lifetime - FADE_OUT_TICKS)) / FADE_OUT_TICKS)
                : 1.0F;
        fade = Mth.clamp(fade, 0.0F, 1.0F);
        if (fade <= 0.0F || this.lineSamples.size() < 2) {
            return;
        }

        float intactWidth = Mth.lerp(chargeProgress, 0.12F, 0.34F);
        float intactFade = 1.0F - easedRange(splitProgress, 0.12F, 0.58F);
        float intactAlpha = (0.18F + chargeProgress * 0.82F) * intactFade * fade;
        float intactCoreAlpha = Math.min(1.0F, intactAlpha * 1.18F);
        drawRibbon(matrix, vertex, cameraPos, this.lineSamples, intactWidth * 2.4F, 0.0F, 0.010F, new ParticleColor(0.58F, 0.76F, 1.0F, intactAlpha * 0.42F));
        drawRibbon(matrix, vertex, cameraPos, this.lineSamples, intactWidth, 0.0F, 0.018F, new ParticleColor(1.0F, 1.0F, 1.0F, intactCoreAlpha));

    }

    private float easedRange(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        float t = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void cacheStraightLineSamples(ClientLevel level) {
        int steps = Math.max(24, Mth.ceil(this.length * 2.0F));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Vec3 point = this.origin.add(this.direction.scale(this.length * progress));
            int blockX = Mth.floor(point.x);
            int blockZ = Mth.floor(point.z);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
            this.lineSamples.add(new Vec3(point.x, surfaceY + 0.025D, point.z));
        }
    }

    private void updateBoundsFromSamples(Vec3 end) {
        double minY = Math.min(this.origin.y, end.y) - 4.0D;
        double maxY = Math.max(this.origin.y, end.y) + 6.0D;
        for (Vec3 sample : this.lineSamples) {
            minY = Math.min(minY, sample.y - 2.0D);
            maxY = Math.max(maxY, sample.y + 3.0D);
        }
        double inflate = this.length * 0.5D + this.maxSplitOffset + 8.0D;
        this.setBoundingBox(new AABB(this.origin.x, minY, this.origin.z, end.x, maxY, end.z).inflate(inflate));
    }

    private void drawRibbon(Matrix4f matrix, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> samples, float width, float lateralOffset, float yOffset, ParticleColor color) {
        if (width <= 0.001F || samples.size() < 2 || color.a <= 0.001F) {
            return;
        }
        Vec3 lateral = this.perpendicular.scale(lateralOffset);
        Vec3 half = this.perpendicular.scale(width * 0.5D);
        for (int i = 0; i < samples.size() - 1; i++) {
            Vec3 start = samples.get(i).add(lateral).add(0.0D, yOffset, 0.0D);
            Vec3 end = samples.get(i + 1).add(lateral).add(0.0D, yOffset, 0.0D);
            Vec3 a = start.add(half).subtract(cameraPos);
            Vec3 b = end.add(half).subtract(cameraPos);
            Vec3 c = end.subtract(half).subtract(cameraPos);
            Vec3 d = start.subtract(half).subtract(cameraPos);
            addQuad(vertex, matrix, a, b, c, d, color);
        }
    }

    private void addQuad(VertexConsumer vertex, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, ParticleColor color) {
        vertex.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(color.r, color.g, color.b, color.a).endVertex();
        vertex.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(color.r, color.g, color.b, color.a).endVertex();
        vertex.vertex(matrix, (float) c.x, (float) c.y, (float) c.z).color(color.r, color.g, color.b, color.a).endVertex();
        vertex.vertex(matrix, (float) d.x, (float) d.y, (float) d.z).color(color.r, color.g, color.b, color.a).endVertex();
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
            return "DESTRUCTION_GOD_FAULT_SPLIT";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }
}
