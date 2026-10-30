package com.whitecloud233.modid.herobrine_companion.fight.particles;

import com.whitecloud233.modid.herobrine_companion.client.particle.ParticleGeometry;

import com.whitecloud233.modid.herobrine_companion.client.particle.ParticleColor;

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

public class PaleLightningArcParticle extends Particle {

    private final Vec3 absoluteEndPos;
    private final List<Vec3> mainPath;
    private final float baseRadius;

    public PaleLightningArcParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos) {
        this(level, startX, startY, startZ, endPos, 0.02F, 12);
    }

    public PaleLightningArcParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos, float baseRadius, int lifetimeTicks) {
        super(level, startX, startY, startZ);
        this.absoluteEndPos = endPos;
        this.baseRadius = Math.max(0.02F, baseRadius);

        this.lifetime = Math.max(8, lifetimeTicks);
        this.hasPhysics = false;

        this.setBoundingBox(new AABB(startX, startY, startZ, endPos.x, endPos.y, endPos.z).inflate(Math.max(1.0D, this.baseRadius * 18.0D)));

        Vec3 startPos = this.getPos();
        Random r = new Random();

        int segments = Math.max(5, (int)(startPos.distanceTo(endPos) * 4));
        float wanderStep = 0.3f + this.baseRadius * 1.6F;
        this.mainPath = buildJaggedPath(startPos, endPos, segments, wanderStep, r);
    }

    private List<Vec3> buildJaggedPath(Vec3 start, Vec3 end, int segments, float stepOffset, Random r) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);
        float step = 1.0f / segments;
        Vec3 currentWander = Vec3.ZERO;

        for (float p = step; p < 1.0f; p += step) {
            Vec3 basePoint = start.lerp(end, p);
            float dx = (r.nextFloat() * 2 - 1) * stepOffset;
            float dy = (r.nextFloat() * 2 - 1) * stepOffset;
            float dz = (r.nextFloat() * 2 - 1) * stepOffset;
            currentWander = currentWander.add(dx, dy, dz);

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

        float alpha = 1.0f;
        int fadeStartTick = lifetime - 5;
        if (this.age > fadeStartTick) {
            alpha = 1.0f - ((float) (this.age - fadeStartTick) / 5.0f);
        }

        ParticleColor outerGlowColor = new ParticleColor(0.58f, 0.78f, 1.0f, 0.26f * alpha);
        ParticleColor glowColor = new ParticleColor(0.8f, 0.9f, 1.0f, 0.48f * alpha);
        ParticleColor coreColor = new ParticleColor(1.0f, 1.0f, 1.0f, 0.95f * alpha);

        int cylinderSegments = this.baseRadius >= 0.08F ? 6 : 5;

        ParticleGeometry.cylinder(mat, vertex, cameraPos, this.mainPath, cylinderSegments, baseRadius * 4.6f, outerGlowColor);
        ParticleGeometry.cylinder(mat, vertex, cameraPos, this.mainPath, cylinderSegments, baseRadius * 2.5f, glowColor);
        ParticleGeometry.cylinder(mat, vertex, cameraPos, this.mainPath, cylinderSegments, baseRadius, coreColor);
    }

    public static final ParticleRenderType RENDER_TYPE = GlowTrailRenderTypes.GLOW_LIGHTNING;

    @Override
    public ParticleRenderType getRenderType() { return RENDER_TYPE; }
}