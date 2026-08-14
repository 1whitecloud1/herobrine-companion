package com.whitecloud233.herobrine_companion.client.fight.skill;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HeroChallengeArenaSliceSkill {
    private static final Vec3 ARENA_CENTER = new Vec3(0.5D, 101.0D, 0.5D);
    private static final double ARENA_RADIUS = 62.0D;
    private static final double RANDOM_CENTER_RADIUS = 8.0D;
    private static final double MIN_CUT_OFFSET = 42.0D;
    private static final double CUT_BEHIND_TARGET = 2.0D;
    private static final int FIRST_CAST_PHASE = 140;
    private static final int RETRY_INTERVAL = 20;
    private static final int POST_CAST_RESET = 20;
    private static final int LINE_LIFETIME_EXTRA = 140;
    private static final int FALL_TICKS = 96;
    private static final int HOLD_TICKS = 20;

    private static final Map<UUID, SliceSkillState> STATES = new HashMap<>();

    private HeroChallengeArenaSliceSkill() {
    }

    @SubscribeEvent
    public static void onHeroTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof HeroEntity hero)) {
            return;
        }
        if (!(hero.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.isClientSide) {
            return;
        }

        SliceSkillState state = STATES.computeIfAbsent(hero.getUUID(), ignored -> new SliceSkillState());
        boolean challengeActive = hero.getPersistentData().getBoolean("IsChallengeActive")
                && !hero.getPersistentData().getBoolean("IsFakeOutPhase");

        if (!challengeActive || hero.isRemoved()) {
            STATES.remove(hero.getUUID());
            return;
        }

        tick(hero, level, state);
    }

    private static void tick(HeroEntity hero, ServerLevel level, SliceSkillState state) {
        if (state.cooldown > 0) {
            state.cooldown--;
        }

        if (state.castTicks >= 0) {
            tickCast(hero, level, state);
            return;
        }

        int phaseTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
        if (phaseTicks < FIRST_CAST_PHASE || phaseTicks < state.nextCastPhase || state.cooldown > 0) {
            return;
        }

        ServerPlayer target = findChallengePlayer(level);
        if (target == null) {
            state.nextCastPhase = phaseTicks + RETRY_INTERVAL;
            return;
        }

        Vec3 toTarget = flatten(target.position().subtract(ARENA_CENTER));
        double targetRadial = toTarget.length();
        Vec3 capNormal;
        if (targetRadial < RANDOM_CENTER_RADIUS) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            capNormal = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            targetRadial = RANDOM_CENTER_RADIUS;
        } else {
            capNormal = toTarget.normalize();
        }

        beginCast(hero, level, capNormal, targetRadial, state);
    }

    private static void beginCast(HeroEntity hero, ServerLevel level, Vec3 capNormal, double targetRadial, SliceSkillState state) {
        int telegraphTicks = difficultyTelegraph(hero);
        int cooldown = difficultyCooldown(hero);
        Vec3 cutDirection = new Vec3(-capNormal.z, 0.0D, capNormal.x);
        double cutOffset = Mth.clamp(targetRadial - CUT_BEHIND_TARGET, MIN_CUT_OFFSET, ARENA_RADIUS - 1.0D);
        double halfChord = Math.sqrt(Math.max(1.0D, ARENA_RADIUS * ARENA_RADIUS - cutOffset * cutOffset));

        Vec3 chordCenter = ARENA_CENTER.add(capNormal.scale(cutOffset));
        Vec3 start = chordCenter.subtract(cutDirection.scale(halfChord));
        Vec3 end = chordCenter.add(cutDirection.scale(halfChord));

        state.castData = new CakeSliceCastData(capNormal, cutDirection, cutOffset, halfChord, start, end, telegraphTicks, cooldown);
        state.castTicks = 0;

        PacketHandler.sendToTracking(new DestructionGodFaultSplitPacket(
                new Vec3(start.x, ARENA_CENTER.y, start.z),
                cutDirection,
                (float) (halfChord * 2.0D),
                1,
                2,
                telegraphTicks,
                telegraphTicks + LINE_LIFETIME_EXTRA
        ), hero);

        ChallengeArenaSliceManager.start(level, hero.getUUID(), ARENA_CENTER, capNormal, cutDirection, cutOffset, ARENA_RADIUS, telegraphTicks);

        level.playSound(null, start.x, ARENA_CENTER.y, start.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.2F, 0.42F);
        level.playSound(null, start.x, ARENA_CENTER.y, start.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.58F);
        spawnTelegraphParticles(level, start, cutDirection, halfChord * 2.0D);
    }

    private static void tickCast(HeroEntity hero, ServerLevel level, SliceSkillState state) {
        CakeSliceCastData cast = state.castData;
        if (cast == null) {
            state.castTicks = -1;
            return;
        }

        ServerPlayer target = findChallengePlayer(level);
        if (target != null) {
            hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        hero.getNavigation().stop();

        state.castTicks++;
        if (state.castTicks == cast.telegraphTicks()) {
            releaseCast(hero, level, cast);
        }

        if (state.castTicks >= cast.telegraphTicks() + POST_CAST_RESET) {
            state.cooldown = cast.cooldown();
            state.castTicks = -1;
            state.castData = null;
            state.nextCastPhase = hero.getPersistentData().getInt("ChallengePhaseTicks") + RETRY_INTERVAL;
        }
    }

    private static void releaseCast(HeroEntity hero, ServerLevel level, CakeSliceCastData cast) {
        Vec3 lineCenter = cast.start().lerp(cast.end(), 0.5D);
        level.playSound(null, lineCenter.x, lineCenter.y, lineCenter.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 5.6F, 0.45F);
        level.playSound(null, lineCenter.x, lineCenter.y, lineCenter.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.8F, 0.62F);

        PacketHandler.sendToTracking(new SPacketChallengeArenaSlice(
                ARENA_CENTER,
                cast.capNormal(),
                cast.cutDirection(),
                (float) cast.cutOffset(),
                (float) ARENA_RADIUS,
                FALL_TICKS,
                HOLD_TICKS,
                true
        ), hero);
    }

    private static void spawnTelegraphParticles(ServerLevel level, Vec3 start, Vec3 direction, double length) {
        int samples = Math.max(8, Mth.ceil(length / 3.0D));
        for (int i = 0; i <= samples; i++) {
            Vec3 point = start.add(direction.scale(length * i / (double) samples));
            level.sendParticles(ParticleTypes.END_ROD, point.x, ARENA_CENTER.y + 0.1D, point.z, 2, 0.02D, 0.04D, 0.02D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, ARENA_CENTER.y + 0.12D, point.z, 2, 0.03D, 0.05D, 0.03D, 0.01D);
        }
    }

    private static ServerPlayer findChallengePlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                return player;
            }
        }
        return null;
    }

    private static int difficultyTelegraph(HeroEntity hero) {
        float multiplier = challengeDamageMultiplier(hero);
        if (multiplier <= 0.6F) {
            return 70;
        }
        return multiplier >= 1.8F ? 42 : 56;
    }

    private static int difficultyCooldown(HeroEntity hero) {
        float multiplier = challengeDamageMultiplier(hero);
        if (multiplier <= 0.6F) {
            return 240;
        }
        return multiplier >= 1.8F ? 140 : 180;
    }

    private static float challengeDamageMultiplier(HeroEntity hero) {
        return hero.getPersistentData().contains("ChallengeDamageMultiplier")
                ? hero.getPersistentData().getFloat("ChallengeDamageMultiplier")
                : 1.0F;
    }

    private static Vec3 flatten(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0D, vector.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return flat.normalize();
    }

    private static final class SliceSkillState {
        private int cooldown;
        private int castTicks = -1;
        private int nextCastPhase = FIRST_CAST_PHASE;
        private CakeSliceCastData castData;
    }

    private record CakeSliceCastData(Vec3 capNormal, Vec3 cutDirection, double cutOffset, double halfChord,
                                     Vec3 start, Vec3 end, int telegraphTicks, int cooldown) {
    }
}
