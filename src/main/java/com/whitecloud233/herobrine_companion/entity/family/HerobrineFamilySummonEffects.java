package com.whitecloud233.herobrine_companion.entity.family;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class HerobrineFamilySummonEffects {
    private HerobrineFamilySummonEffects() {
    }

    public static void playStart(ServerLevel level, HerobrineFamilySummonStructure structure) {
        playPulse(level, structure.center(), structure.memberType());
        level.playSound(null, structure.center(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.5F, 0.65F);
        level.playSound(null, structure.center(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 2.2F, 0.85F);
        spawnTypeParticles(level, structure.center(), structure.memberType(), 0.75D);
    }

    public static void playPulse(ServerLevel level, BlockPos center, HerobrineFamilyMemberType type) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(center));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        float pitch = type == HerobrineFamilyMemberType.JEAN ? 1.1F : 0.6F;
        level.playSound(null, center, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 1.3F, pitch);
        spawnTypeParticles(level, center, type, 0.45D);
    }

    public static void playFailure(ServerLevel level, BlockPos center, HerobrineFamilyMemberType type) {
        level.playSound(null, center, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.1F, 0.65F);
        level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.75F);
        spawnTypeParticles(level, center, type, 0.25D);
    }

    public static void playCompletion(ServerLevel level, HerobrineFamilySummonStructure structure, Mob summoned) {
        playPulse(level, structure.center(), structure.memberType());
        level.playSound(null, structure.center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.5F,
                structure.memberType() == HerobrineFamilyMemberType.JEAN ? 1.1F : 0.75F);
        level.playSound(null, structure.center(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.75F);
        spawnTypeParticles(level, structure.center(), structure.memberType(), 1.2D);
        summoned.setTarget(null);
    }

    public static void consumeStructureVisuals(ServerLevel level, HerobrineFamilySummonStructure structure) {
        for (BlockPos consumePos : structure.consumeBlocks()) {
            if (!level.getBlockState(consumePos).isAir()) {
                level.levelEvent(2001, consumePos, net.minecraft.world.level.block.Block.getId(level.getBlockState(consumePos)));
            }
        }

        for (BlockPos anchor : structure.crystalAnchors()) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    anchor.getX() + 0.5D, anchor.getY() + 1.2D, anchor.getZ() + 0.5D,
                    16, 0.35D, 0.25D, 0.35D, 0.02D);
            if (level.getBlockState(anchor).is(Blocks.AIR)) {
                level.sendParticles(ParticleTypes.END_ROD,
                        anchor.getX() + 0.5D, anchor.getY() + 0.8D, anchor.getZ() + 0.5D,
                        10, 0.25D, 0.25D, 0.25D, 0.03D);
            }
        }
    }

    private static void spawnTypeParticles(ServerLevel level, BlockPos center, HerobrineFamilyMemberType type, double speedScale) {
        if (type == HerobrineFamilyMemberType.SIMMONS) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D,
                    18, 0.65D, 0.35D, 0.65D, 0.02D + speedScale * 0.02D);
            level.sendParticles(ParticleTypes.SMOKE,
                    center.getX() + 0.5D, center.getY() + 0.9D, center.getZ() + 0.5D,
                    14, 0.55D, 0.25D, 0.55D, 0.01D + speedScale * 0.01D);
            return;
        }

        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                center.getX() + 0.5D, center.getY() + 2.2D, center.getZ() + 0.5D,
                24, 0.95D, 0.45D, 0.95D, 0.03D + speedScale * 0.02D);
        level.sendParticles(ParticleTypes.PORTAL,
                center.getX() + 0.5D, center.getY() + 1.4D, center.getZ() + 0.5D,
                18, 0.85D, 0.55D, 0.85D, 0.02D + speedScale * 0.01D);
    }
}
