package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

public class HeroVisuals {

    public static void tickClientAnimations(HeroEntity hero) {
        if (hero.scytheAnimTick > 0) hero.scytheAnimTick--;
        if (hero.debugAnimTick > 0) {
            hero.debugAnimTick--;
            if (hero.level().isClientSide && hero.debugAnimTick > 10 && hero.debugAnimTick < 90) {
                spawnDebugParticles(hero);
            }
        }
        if (hero.shockTicks > 0) hero.shockTicks--;

        if (hero.thunderTicks > 0) {
            hero.thunderTicks--;
            spawnThunderParticles(hero);

            if (!hero.level().isClientSide && hero.thunderTicks == 1) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(hero.level());
                if (bolt != null) {
                    bolt.moveTo(hero.getX(), hero.getY(), hero.getZ());
                    bolt.setVisualOnly(true);
                    hero.level().addFreshEntity(bolt);
                }
                hero.shockTicks = HeroEntity.MAX_SHOCK_TICKS;
            }
        }
    }

    public static void tickClientAmbient(HeroEntity hero) {
        if (hero.getMindState() == SimpleNeuralNetwork.MindState.JUDGE) {
            if (hero.getRandom().nextInt(3) == 0) {
                double x = hero.getX() + (hero.getRandom().nextDouble() - 0.5) * 1.5;
                double y = hero.getY() + hero.getRandom().nextDouble() * 2.0;
                double z = hero.getZ() + (hero.getRandom().nextDouble() - 0.5) * 1.5;
                hero.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
            }
        }
    }

    private static void spawnDebugParticles(HeroEntity hero) {
        if (hero.getRandom().nextInt(5) != 0) return;

        double yawRad = Math.toRadians(hero.yBodyRot);
        double offsetX = -Math.sin(yawRad) * 0.8 + Math.cos(yawRad) * 0.3;
        double offsetZ = Math.cos(yawRad) * 0.8 + Math.sin(yawRad) * 0.3;
        double offsetY = 1.4;

        double x = hero.getX() + offsetX;
        double y = hero.getY() + offsetY;
        double z = hero.getZ() + offsetZ;

        double pX = x + (hero.getRandom().nextDouble() - 0.5) * 0.5;
        double pY = y + (hero.getRandom().nextDouble() - 0.5) * 0.4;
        double pZ = z + (hero.getRandom().nextDouble() - 0.5) * 0.5;

        hero.level().addParticle(ParticleTypes.ENCHANT, pX, pY, pZ, 0, 0.01, 0);
    }

    private static void spawnThunderParticles(HeroEntity hero) {
        if (!hero.level().isClientSide) return;

        double yaw = Math.toRadians(hero.yBodyRot + 90);
        double handX = hero.getX() + Math.cos(yaw) * 0.6;
        double handY = hero.getY() + 2.8;
        double handZ = hero.getZ() + Math.sin(yaw) * 0.6;

        RandomSource rand = hero.getRandom();

        for(int i=0; i<3; i++) {
            hero.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    handX + (rand.nextDouble()-0.5)*0.5,
                    handY + (rand.nextDouble()-0.5)*0.5,
                    handZ + (rand.nextDouble()-0.5)*0.5, 0, 0, 0);
        }

        float progress = 1.0F - (float)hero.thunderTicks / HeroEntity.MAX_THUNDER_TICKS;
        if (progress > 0.3F) {
            for(int i=0; i<2; i++) {
                double height = rand.nextDouble() * 10.0 * progress;
                double angle = (hero.tickCount * 0.5 + height) * 0.5;
                double radius = 1.0 - (height * 0.05);
                if (radius < 0) radius = 0;

                double pX = hero.getX() + Math.cos(angle) * radius;
                double pZ = hero.getZ() + Math.sin(angle) * radius;

                hero.level().addParticle(ParticleTypes.FIREWORK, pX, hero.getY() + height, pZ, 0, 0.1, 0);
            }
        }
    }
}