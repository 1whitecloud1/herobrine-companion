package com.whitecloud233.modid.herobrine_companion.destructiongod.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.SPacketWorldRendCinematic;
import com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager;
import com.whitecloud233.modid.herobrine_companion.destructiongod.world.FaultSplitTerrainSkill;
import com.whitecloud233.modid.herobrine_companion.destructiongod.world.LightningTerrainSkill;
import com.whitecloud233.modid.herobrine_companion.destructiongod.world.OrbTerrainSkill;
import com.whitecloud233.modid.herobrine_companion.destructiongod.world.RendTerrainSkill;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.CleaveBladeEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class DestructionGodCombatGoal extends Goal {
    private static final double PRESSURE_INNER_RADIUS = 12.0D;
    private static final double PRESSURE_OUTER_RADIUS = 20.0D;
    private static final int FAULT_SPLIT_TELEGRAPH_TICKS = 120;
    private static final int FAULT_SPLIT_DISPLAY_MOVE_TICKS = 96;
    private static final int FAULT_SPLIT_DISPLAY_HOLD_TICKS = 28;
    private final DestructionGodHerobrineEntity boss;
    private int meleeCooldown;
    @Nullable
    private FaultSplitCastData faultSplitCastData;

    public DestructionGodCombatGoal(DestructionGodHerobrineEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || this.boss.isCastingSkill();
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if ((target == null || !target.isAlive()) && !this.boss.level().isClientSide) {
            target = this.boss.level().getNearestPlayer(this.boss, 96.0D);
            if (target != null) {
                this.boss.setTarget(target);
            }
        }
        if (target == null) {
            return;
        }

        this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
        }

        if (!this.boss.isCastingSkill() || this.boss.getSkillState() != DestructionGodHerobrineEntity.SKILL_SCYTHE_FAULT_SPLIT) {
            this.faultSplitCastData = null;
        }

        if (this.boss.isCastingSkill()) {
            this.handleCasting(target);
            return;
        }

        double distanceSq = this.boss.distanceToSqr(target);
        double outerRadiusSq = PRESSURE_OUTER_RADIUS * PRESSURE_OUTER_RADIUS;
        if (distanceSq > outerRadiusSq) {
            this.boss.getNavigation().moveTo(target, 0.95D);
        } else {
            this.boss.getNavigation().stop();
        }
        this.applyAerialChase(target, distanceSq);

        if (!this.boss.level().isClientSide && this.boss.level() instanceof ServerLevel serverLevel) {
            maybeBeginSkill(serverLevel, target, distanceSq);
        }

        if (!this.boss.isCastingSkill() && distanceSq < 9.0D && this.meleeCooldown <= 0 && this.boss.hasLineOfSight(target)) {
            this.boss.doHurtTarget(target);
            this.boss.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.7F, 0.7F);
            this.meleeCooldown = 18;
        }
    }

    private void handleCasting(LivingEntity target) {
        this.boss.getNavigation().stop();
        if (this.boss.getSkillState() == DestructionGodHerobrineEntity.SKILL_THUNDER_SKYNET) {
            this.maintainThunderSkyNetControl(target);
        }
        if (!this.boss.level().isClientSide && this.boss.level() instanceof ServerLevel serverLevel) {
            Vec3 castDirection = flatten(target.position().subtract(this.boss.position()));
            Vec3 origin = this.boss.position().add(0.0D, 0.5D, 0.0D).add(castDirection.scale(2.0D));
            int phase = this.boss.getBossPhase();
            switch (this.boss.getSkillState()) {
                case DestructionGodHerobrineEntity.SKILL_WORLD_REND -> {
                    if (this.boss.consumeSkillStageTrigger(0, 12)) {
                        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.boss.getX(), this.boss.getY() + 1.4D, this.boss.getZ(), 24, 0.7D, 1.0D, 0.7D, 0.03D);
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, this.boss.getY() + 1.5D, origin.z, 20, 0.35D, 0.8D, 0.35D, 0.08D);
                        serverLevel.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.8F, 0.45F);
                        serverLevel.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.2F, 0.7F);
                    }
                    if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
                        Vec3 rendOrigin = worldRendOrigin(serverLevel, target, origin);
                        double slashLength = 108.0D + phase * 14.0D;
                        int halfWidth = Math.max(3, 4 + phase / 2);
                        int slashDepth = 20 + phase * 4;
                        double bladeSpeed = 5.2D;
                        float slashRoll = randomSlashRoll(serverLevel);
                        CleaveBladeEntity blade = new CleaveBladeEntity(ModEvents.CLEAVE_BLADE.get(), serverLevel, rendOrigin.x, rendOrigin.y + 0.9D, rendOrigin.z, castDirection.x, castDirection.z, Math.max(14, Mth.ceil(slashLength / bladeSpeed) + 3), slashRoll);
                        blade.setDeltaMovement(castDirection.x * bladeSpeed, 0.0D, castDirection.z * bladeSpeed);
                        serverLevel.addFreshEntity(blade);
                        RendTerrainSkill.startBladeLineRend(serverLevel, this.boss, rendOrigin, castDirection, slashLength, halfWidth, slashDepth, 0, slashRoll);
                        sendWorldRendCinematic(serverLevel, rendOrigin);
                        serverLevel.playSound(null, rendOrigin.x, rendOrigin.y + 1.0D, rendOrigin.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.5F, 0.8F);
                    }
                }
                case DestructionGodHerobrineEntity.SKILL_APOCALYPSE_CRACK -> handleApocalypseCrack(serverLevel, target, castDirection);
                case DestructionGodHerobrineEntity.SKILL_WORLD_PEEL -> handleWorldPeel(serverLevel, target, castDirection);
                case DestructionGodHerobrineEntity.SKILL_WORLD_COLLAPSE -> handleWorldCollapse(serverLevel, target);
                case DestructionGodHerobrineEntity.SKILL_SCYTHE_ICHIMONJI -> handleIchimonji(serverLevel, target);
                case DestructionGodHerobrineEntity.SKILL_SCYTHE_REVERSE_MOON -> handleReverseMoon(serverLevel, target);
                case DestructionGodHerobrineEntity.SKILL_SCYTHE_EXECUTION -> handleExecution(serverLevel, target);
                case DestructionGodHerobrineEntity.SKILL_SCYTHE_FAULT_SPLIT -> handleFaultSplit(serverLevel, phase);
                case DestructionGodHerobrineEntity.SKILL_DESTRUCTION_LIGHTNING -> handleDestructionLightning(serverLevel, target);
                case DestructionGodHerobrineEntity.SKILL_DESTRUCTION_GOD_ORB -> handleDestructionGodOrb(serverLevel, target, castDirection);
                case DestructionGodHerobrineEntity.SKILL_THUNDER_SKYNET -> handleThunderSkyNet(serverLevel, target);
                default -> {
                }
            }
        }
    }

    private void handleDestructionGodOrb(ServerLevel level, LivingEntity target, Vec3 castDirection) {
        Vec3 handPos = this.boss.position().add(0.0D, 2.25D, 0.0D).add(new Vec3(-castDirection.z, 0.0D, castDirection.x).scale(0.65D)).add(castDirection.scale(0.55D));
        if (this.boss.consumeSkillStageTrigger(0, 8)) {
            level.sendParticles(ParticleTypes.END_ROD, handPos.x, handPos.y, handPos.z, 42, 0.45D, 0.45D, 0.45D, 0.06D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, handPos.x, handPos.y, handPos.z, 64, 0.55D, 0.55D, 0.55D, 0.18D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, handPos.x, handPos.y, handPos.z, 36, 0.45D, 0.45D, 0.45D, 0.05D);
            level.playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.2F, 0.38F);
        }

        if (this.boss.consumeSkillStageTrigger(1, 20)) {
            Vec3 raisedPos = this.boss.position().add(0.0D, 5.6D, 0.0D).add(castDirection.scale(0.5D));
            level.sendParticles(ParticleTypes.END_ROD, raisedPos.x, raisedPos.y, raisedPos.z, 80, 0.9D, 0.9D, 0.9D, 0.08D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, raisedPos.x, raisedPos.y, raisedPos.z, 110, 1.0D, 1.0D, 1.0D, 0.22D);
            level.playSound(null, raisedPos.x, raisedPos.y, raisedPos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.4F, 0.45F);
        }

        if (this.boss.consumeSkillStageTrigger(2, this.boss.getSkillTriggerTick())) {
            Vec3 impact = selectOrbImpact(level, target, castDirection);
            Vec3 start = handPos.add(0.0D, 0.4D, 0.0D);
            int phase = this.boss.getBossPhase();
            double craterRadius = 38.0D + phase * 5.0D;
            double orbMaxRadius = craterRadius * 0.5D;
            int craterDepth = 48 + phase * 8;
            int fallTicks = 150;
            PacketHandler.sendToTracking(new DestructionGodOrbPacket(start, impact, fallTicks, 1.8F, (float) orbMaxRadius, 24.0F), this.boss);
            OrbTerrainSkill.start(level, this.boss, start, impact, fallTicks, 1.8D, orbMaxRadius, craterRadius, craterDepth, 0);
            level.sendParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 6, 1.0D, 1.0D, 1.0D, 0.0D);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.5F, 0.55F);
        }
    }

    private void handleApocalypseCrack(ServerLevel level, LivingEntity target, Vec3 castDirection) {
        int phase = this.boss.getBossPhase();
        Vec3 crackOrigin = faultSplitOrigin(level, this.boss.position().add(0.0D, 0.4D, 0.0D).add(castDirection.scale(4.5D)));
        double sideOffset = 4.8D + phase * 0.9D;

        if (this.boss.consumeSkillStageTrigger(0, 8)) {
            level.sendParticles(ParticleTypes.SMOKE, crackOrigin.x, crackOrigin.y + 0.25D, crackOrigin.z, 26, 0.9D, 0.2D, 0.9D, 0.02D);
            level.sendParticles(ParticleTypes.ASH, crackOrigin.x, crackOrigin.y + 0.2D, crackOrigin.z, 18, 0.8D, 0.15D, 0.8D, 0.01D);
            level.playSound(null, crackOrigin.x, crackOrigin.y, crackOrigin.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.8F, 0.42F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            double lineLength = 84.0D + phase * 10.0D;
            int crackHalfWidth = Math.max(1, phase / 2);
            int depth = 9 + phase * 2;
            DestructionTerrainManager.startApocalypseCrack(level, this.boss, crackOrigin, castDirection, lineLength, sideOffset, crackHalfWidth, depth, 0);
            level.playSound(null, crackOrigin.x, crackOrigin.y + 0.6D, crackOrigin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 4.2F, 1.2F);
            level.playSound(null, crackOrigin.x, crackOrigin.y + 0.6D, crackOrigin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 3.6F, 0.75F);
        }
    }

    private void handleWorldPeel(ServerLevel level, LivingEntity target, Vec3 castDirection) {
        int phase = this.boss.getBossPhase();
        Vec3 peelOrigin = faultSplitOrigin(level, this.boss.position().add(0.0D, 0.5D, 0.0D).add(castDirection.scale(6.0D)));
        double lineLength = Mth.clamp(Math.sqrt(this.boss.distanceToSqr(target)) + 22.0D + phase * 4.0D, 42.0D, 96.0D);
        int radius = 6 + phase;

        if (this.boss.consumeSkillStageTrigger(0, 10)) {
            level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0D, target.getZ(), 32, 1.1D, 1.3D, 1.1D, 0.08D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, peelOrigin.x, peelOrigin.y + 0.3D, peelOrigin.z, 22, 0.8D, 0.3D, 0.8D, 0.04D);
            level.playSound(null, peelOrigin.x, peelOrigin.y, peelOrigin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.3F, 0.45F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            DestructionTerrainManager.startWorldPeel(level, this.boss, peelOrigin, castDirection, lineLength, radius, 0);
            level.playSound(null, peelOrigin.x, peelOrigin.y + 0.5D, peelOrigin.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.WEATHER, 3.6F, 0.8F);
        }
    }

    private void handleWorldCollapse(ServerLevel level, LivingEntity target) {
        int phase = this.boss.getBossPhase();
        Vec3 collapseCenter = groundCenter(level, target.position());
        double startRadius = 24.0D + phase * 7.0D;
        double endRadius = 4.5D;
        int bandWidth = 4 + phase;

        if (this.boss.consumeSkillStageTrigger(0, 14)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, collapseCenter.x, collapseCenter.y + 1.0D, collapseCenter.z, 42, startRadius * 0.18D, 1.6D, startRadius * 0.18D, 0.015D);
            level.sendParticles(ParticleTypes.SMOKE, collapseCenter.x, collapseCenter.y + 0.5D, collapseCenter.z, 20, 1.2D, 0.4D, 1.2D, 0.02D);
            level.playSound(null, collapseCenter.x, collapseCenter.y, collapseCenter.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.8F, 0.55F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            DestructionTerrainManager.startWorldCollapse(level, this.boss, collapseCenter, startRadius, endRadius, bandWidth, 0);
            level.playSound(null, collapseCenter.x, collapseCenter.y, collapseCenter.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.2F, 0.55F);
        }
    }

    private void handleDestructionLightning(ServerLevel level, LivingEntity target) {
        if (this.boss.consumeSkillStageTrigger(0, 8)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.boss.getX(), this.boss.getY() + 2.0D, this.boss.getZ(), 36, 0.8D, 1.2D, 0.8D, 0.12D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.boss.getX(), this.boss.getY() + 1.6D, this.boss.getZ(), 24, 0.7D, 0.9D, 0.7D, 0.035D);
            level.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.5F, 0.55F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            Vec3 bossCenter = this.boss.position().add(0.0D, 2.1D, 0.0D);
            Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
            int phase = this.boss.getBossPhase();
            int beams = 4 + phase;
            double holeOrbitRadius = 22.0D + phase * 6.0D;
            double beamLength = 124.0D + phase * 24.0D;
            int chargeDelay = 56;
            List<Vec3> holePositions = createWhiteHolePositions(level, bossCenter, beams, holeOrbitRadius);

            PacketHandler.sendToTracking(new DestructionGodLightningArcPacket(bossCenter, targetCenter, 0.08F, 16), this.boss);
            LightningTerrainSkill.startArc(level, this.boss, bossCenter, targetCenter, 1.45D + phase * 0.18D, 2);

            for (int i = 0; i < holePositions.size(); i++) {
                Vec3 holePos = holePositions.get(i);
                Vec3 beamDirection = randomLightningBeamDirection(level, targetCenter.subtract(holePos));
                Vec3 beamEnd = holePos.add(beamDirection.scale(beamLength));
                float width = 6.4F + phase * 1.05F;
                int delay = chargeDelay + i * 2;

                PacketHandler.sendToTracking(new DestructionGodLightningPacket(holePos, beamEnd, width), this.boss);
                LightningTerrainSkill.startBeam(level, this.boss, holePos, beamEnd, width * 0.52D, delay + 2);
            }

            level.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.2F, 0.68F);
        }
    }

    private void handleThunderSkyNet(ServerLevel level, LivingEntity target) {
        Vec3 bodyCenter = this.boss.position().add(0.0D, 1.8D, 0.0D);
        int phase = this.boss.getBossPhase();
        int netDuration = 162 + phase * 14;
        int pillarLeadTicks = 22;

        if (this.boss.consumeSkillStageTrigger(0, 8)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bodyCenter.x, bodyCenter.y, bodyCenter.z, 64, 1.4D, 1.8D, 1.4D, 0.15D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, bodyCenter.x, bodyCenter.y + 0.2D, bodyCenter.z, 42, 0.9D, 1.2D, 0.9D, 0.04D);
            level.sendParticles(ParticleTypes.FLASH, bodyCenter.x, bodyCenter.y + 0.35D, bodyCenter.z, 4, 0.3D, 1.2D, 0.3D, 0.0D);
            level.playSound(null, bodyCenter.x, bodyCenter.y, bodyCenter.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.6F, 0.45F);
            level.playSound(null, bodyCenter.x, bodyCenter.y, bodyCenter.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.55F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            double skyRadius = 128.0D + phase * 18.0D;
            double cloudY = Math.max(bodyCenter.y + 54.0D, target.getY() + 44.0D);
            int strikesPerPulse = 5 + phase / 2;
            int pulseInterval = 4;
            int visualLifetime = pillarLeadTicks + netDuration + 28;
            int seed = level.random.nextInt();

            PacketHandler.sendToTracking(new DestructionGodThunderSkyNetPacket(bodyCenter, cloudY, (float) skyRadius, visualLifetime, seed), this.boss);
            LightningTerrainSkill.startThunderSkyNet(level, this.boss, bodyCenter, cloudY, skyRadius, netDuration, strikesPerPulse, pulseInterval, pillarLeadTicks);

            level.playSound(null, bodyCenter.x, bodyCenter.y, bodyCenter.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.5F, 0.55F);
            level.playSound(null, bodyCenter.x, bodyCenter.y, bodyCenter.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.7F);
        }
    }

    private void handleIchimonji(ServerLevel level, LivingEntity target) {
        Vec3 forward = flatten(target.position().subtract(this.boss.position()));
        if (this.boss.consumeSkillStageTrigger(0, 11)) {
            performArcSlash(level, forward, 4.8D, 92.0D, (float) (this.boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.35D), 1.05D, true, true);
        }
    }

    private void handleReverseMoon(ServerLevel level, LivingEntity target) {
        Vec3 forward = flatten(target.position().subtract(this.boss.position()));
        if (this.boss.consumeSkillStageTrigger(0, 8)) {
            Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).scale(1.8D);
            Vec3 blinkPos = target.position().subtract(forward.scale(1.2D)).add(side);
            this.boss.teleportTo(blinkPos.x, Math.max(target.getY(), this.boss.getY()), blinkPos.z);
            this.boss.level().playSound(null, blinkPos.x, blinkPos.y, blinkPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.7F);
        }
        if (this.boss.consumeSkillStageTrigger(1, 14)) {
            performArcSlash(level, forward, 3.9D, 110.0D, (float) this.boss.getAttributeValue(Attributes.ATTACK_DAMAGE), 0.6D, false, false);
        }
        if (this.boss.consumeSkillStageTrigger(2, 22)) {
            List<LivingEntity> victims = collectSlashTargets(4.5D);
            for (LivingEntity victim : victims) {
                if (!isWithinArc(victim, forward, 130.0D)) {
                    continue;
                }
                victim.hurt(this.boss.damageSources().mobAttack(this.boss), (float) (this.boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.15D));
                Vec3 pullTarget = this.boss.position().add(forward.scale(1.4D));
                Vec3 pull = pullTarget.subtract(victim.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    pull = pull.normalize();
                    victim.setDeltaMovement(victim.getDeltaMovement().scale(0.25D).add(pull.x * 1.05D, 0.28D, pull.z * 1.05D));
                }
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.boss.getX(), this.boss.getY() + 1.2D, this.boss.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void handleExecution(ServerLevel level, LivingEntity fallbackTarget) {
        LivingEntity lockedTarget = this.boss.getExecutionTarget();
        LivingEntity target = lockedTarget != null && lockedTarget.isAlive() ? lockedTarget : fallbackTarget;
        Vec3 forward = flatten(target.position().subtract(this.boss.position()));

        if (this.boss.consumeSkillStageTrigger(0, 10)) {
            this.boss.setExecutionTarget(target);
            Vec3 hoverPos = target.position().subtract(forward.scale(0.8D)).add(0.0D, 4.0D, 0.0D);
            this.boss.teleportTo(hoverPos.x, hoverPos.y, hoverPos.z);
            this.boss.setDeltaMovement(0.0D, -0.05D, 0.0D);
            level.playSound(null, hoverPos.x, hoverPos.y, hoverPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.3F, 0.45F);
        }

        if (this.boss.consumeSkillStageTrigger(1, 22)) {
            Vec3 dive = target.position().subtract(this.boss.position());
            if (dive.lengthSqr() > 1.0E-4D) {
                dive = dive.normalize();
            }
            this.boss.setDeltaMovement(dive.x * 1.5D, -1.1D, dive.z * 1.5D);
        }

        if (this.boss.consumeSkillStageTrigger(2, 28)) {
            AABB strikeBox = this.boss.getBoundingBox().inflate(2.25D, 1.5D, 2.25D);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, strikeBox, entity -> entity.isAlive() && entity != this.boss);
            boolean hit = false;
            for (LivingEntity victim : victims) {
                float damage = (float) (this.boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * (victim == target ? 3.8D : 2.2D));
                victim.hurt(this.boss.damageSources().mobAttack(this.boss), damage);
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.15D, 0.0D, 0.15D).add(0.0D, -0.2D, 0.0D));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
                hit = true;
            }

            Vec3 impactCenter = target.position();
            if (!hit) {
                level.sendParticles(ParticleTypes.FLASH, impactCenter.x, impactCenter.y + 0.7D, impactCenter.z, 2, 0.3D, 0.3D, 0.3D, 0.0D);
            }

            level.sendParticles(ParticleTypes.EXPLOSION, this.boss.getX(), this.boss.getY() + 0.4D, this.boss.getZ(), 6, 0.6D, 0.3D, 0.6D, 0.01D);
            level.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.8F, 0.6F);
        }
    }

    private void handleFaultSplit(ServerLevel level, int phase) {
        FaultSplitCastData castData = this.faultSplitCastData(level, phase);

        if (this.boss.consumeSkillStageTrigger(0, 1)) {
            PacketHandler.sendToTracking(new DestructionGodFaultSplitPacket(castData.origin(), castData.direction(), (float) castData.lineLength(), castData.terrainHalfWidth(), castData.shiftDistance(), castData.telegraphTicks(), castData.visualLifetime()), this.boss);
            level.playSound(null, castData.origin().x, castData.origin().y, castData.origin().z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.2F, 0.42F);
            level.playSound(null, castData.origin().x, castData.origin().y, castData.origin().z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.58F);
        }

        if (this.boss.consumeSkillStageTrigger(1, this.boss.getSkillTriggerTick())) {
            FaultSplitTerrainSkill.start(level, this.boss, castData.origin(), castData.direction(), castData.lineLength(), castData.terrainHalfWidth(), castData.lineHalfWidth(), castData.shiftDistance(), 0);
            level.playSound(null, castData.origin().x, castData.origin().y + 0.8D, castData.origin().z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 5.0F, 0.45F);
            level.playSound(null, castData.origin().x, castData.origin().y + 0.8D, castData.origin().z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.2F, 0.65F);
            level.playSound(null, castData.origin().x, castData.origin().y + 0.8D, castData.origin().z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.6F, 0.45F);
        }
    }

    private FaultSplitCastData faultSplitCastData(ServerLevel level, int phase) {
        if (this.faultSplitCastData != null) {
            return this.faultSplitCastData;
        }

        Vec3 facing = flatten(Vec3.directionFromRotation(0.0F, this.boss.getYHeadRot()));
        Vec3 forwardRelease = this.boss.position().add(0.0D, 0.5D, 0.0D).add(facing.scale(3.6D));
        Vec3 splitOrigin = faultSplitOrigin(level, forwardRelease);
        double lineLength = 104.0D + phase * 12.0D;
        int terrainHalfWidth = 7 + phase;
        int lineHalfWidth = 0;
        int shiftDistance = 1;
        int telegraphTicks = Math.max(FAULT_SPLIT_TELEGRAPH_TICKS, this.boss.getSkillTriggerTick());
        int visualLifetime = Math.max(telegraphTicks + FAULT_SPLIT_DISPLAY_MOVE_TICKS + FAULT_SPLIT_DISPLAY_HOLD_TICKS + 20, 256);
        this.faultSplitCastData = new FaultSplitCastData(splitOrigin, facing, lineLength, terrainHalfWidth, lineHalfWidth, shiftDistance, telegraphTicks, visualLifetime);
        return this.faultSplitCastData;
    }

    private Vec3 faultSplitOrigin(ServerLevel level, Vec3 baseOrigin) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(baseOrigin.x), Mth.floor(baseOrigin.z));
        return new Vec3(baseOrigin.x, groundY, baseOrigin.z);
    }

    private void maybeBeginSkill(ServerLevel serverLevel, LivingEntity target, double distanceSq) {
        double distance = Math.sqrt(distanceSq);
        boolean hasLineOfSight = this.boss.hasLineOfSight(target);
        List<WeightedSkill> candidates = new ArrayList<>();

        this.addSkillCandidate(candidates, DestructionGodHerobrineEntity.SKILL_WORLD_REND, 10, hasLineOfSight && distance >= 8.0D && distance <= 56.0D);
        this.addSkillCandidate(candidates, DestructionGodHerobrineEntity.SKILL_SCYTHE_FAULT_SPLIT, 10, distance >= 6.0D && distance <= 42.0D);
        this.addSkillCandidate(candidates, DestructionGodHerobrineEntity.SKILL_DESTRUCTION_LIGHTNING, 10, distance >= 12.0D);
        this.addSkillCandidate(candidates, DestructionGodHerobrineEntity.SKILL_DESTRUCTION_GOD_ORB, 10, distance >= 10.0D && distance <= 56.0D);
        this.addSkillCandidate(candidates, DestructionGodHerobrineEntity.SKILL_THUNDER_SKYNET, 10, distance >= 12.0D && distance <= 72.0D);

        WeightedSkill choice = this.pickWeightedSkill(serverLevel, candidates);
        if (choice == null) {
            return;
        }
        this.boss.beginSkill(choice.skill());
    }

    private void addSkillCandidate(List<WeightedSkill> candidates, int skill, int weight, boolean condition) {
        if (condition && weight > 0 && this.boss.isSkillReady(skill)) {
            candidates.add(new WeightedSkill(skill, weight));
        }
    }

    @Nullable
    private WeightedSkill pickWeightedSkill(ServerLevel level, List<WeightedSkill> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (WeightedSkill candidate : candidates) {
            totalWeight += Math.max(0, candidate.weight());
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = level.random.nextInt(totalWeight);
        for (WeightedSkill candidate : candidates) {
            roll -= Math.max(0, candidate.weight());
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private void performArcSlash(ServerLevel level, Vec3 forward, double range, double arcDegrees, float damage, double knockback, boolean applySlow, boolean launch) {
        List<LivingEntity> victims = collectSlashTargets(range);
        for (LivingEntity victim : victims) {
            if (!isWithinArc(victim, forward, arcDegrees)) {
                continue;
            }
            victim.hurt(this.boss.damageSources().mobAttack(this.boss), damage);
            Vec3 pushDir = victim.position().subtract(this.boss.position());
            if (pushDir.lengthSqr() < 1.0E-4D) {
                pushDir = forward;
            } else {
                pushDir = pushDir.normalize();
            }
            victim.push(pushDir.x * knockback, launch ? 0.55D : 0.28D, pushDir.z * knockback);
            if (applySlow) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 1));
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.boss.getX() + forward.x * 1.4D, this.boss.getY() + 1.15D, this.boss.getZ() + forward.z * 1.4D, 2, 0.15D, 0.05D, 0.15D, 0.0D);
        level.playSound(null, this.boss.getX(), this.boss.getY(), this.boss.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.8F, 0.55F);
    }

    private List<LivingEntity> collectSlashTargets(double range) {
        AABB box = this.boss.getBoundingBox().inflate(range, 2.0D, range);
        return this.boss.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && entity != this.boss);
    }

    private boolean isWithinArc(LivingEntity victim, Vec3 forward, double arcDegrees) {
        Vec3 toVictim = victim.position().subtract(this.boss.position());
        toVictim = new Vec3(toVictim.x, 0.0D, toVictim.z);
        if (toVictim.lengthSqr() < 1.0E-4D) {
            return true;
        }
        double dot = forward.normalize().dot(toVictim.normalize());
        double threshold = Math.cos(Math.toRadians(arcDegrees * 0.5D));
        return dot >= threshold;
    }

    private Vec3 flatten(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0D, vector.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return flat.normalize();
    }

    private void applyAerialChase(LivingEntity target, double distanceSq) {
        if (this.boss.isCastingSkill()) {
            return;
        }
        Vec3 toBoss = this.boss.position().subtract(target.position());
        Vec3 radial = new Vec3(toBoss.x, 0.0D, toBoss.z);
        if (radial.lengthSqr() < 1.0E-4D) {
            double angle = this.boss.tickCount * 0.11D;
            radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            radial = radial.normalize();
        }
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double orbitAngle = this.boss.tickCount * 0.08D;
        double standoffRadius = Mth.clamp(PRESSURE_INNER_RADIUS + Math.sqrt(distanceSq) * 0.18D, PRESSURE_INNER_RADIUS, PRESSURE_OUTER_RADIUS);
        Vec3 desired = target.position()
                .add(radial.scale(standoffRadius))
                .add(tangent.scale(Math.sin(orbitAngle) * 4.2D))
                .add(0.0D, 4.2D, 0.0D);
        this.boss.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, 1.05D);
        Vec3 toward = desired.subtract(this.boss.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return;
        }
        horizontal = horizontal.normalize();
        double chaseSpeed = distanceSq > PRESSURE_OUTER_RADIUS * PRESSURE_OUTER_RADIUS ? 0.30D : distanceSq < PRESSURE_INNER_RADIUS * PRESSURE_INNER_RADIUS ? 0.16D : 0.22D;
        Vec3 motion = this.boss.getDeltaMovement();
        this.boss.setDeltaMovement(motion.x * 0.72D + horizontal.x * chaseSpeed, motion.y, motion.z * 0.72D + horizontal.z * chaseSpeed);
    }

    private void maintainThunderSkyNetControl(LivingEntity target) {
        Vec3 toBoss = this.boss.position().subtract(target.position());
        Vec3 radial = new Vec3(toBoss.x, 0.0D, toBoss.z);
        if (radial.lengthSqr() < 1.0E-4D) {
            double angle = this.boss.tickCount * 0.09D;
            radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            radial = radial.normalize();
        }
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        double orbitRadius = 26.0D + Math.sin(this.boss.tickCount * 0.045D) * 5.5D;
        double orbitSwing = Math.sin(this.boss.tickCount * 0.06D) * 8.5D;
        Vec3 desired = target.position()
                .add(radial.scale(orbitRadius))
                .add(tangent.scale(orbitSwing))
                .add(0.0D, 14.0D, 0.0D);
        this.boss.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, 0.85D);
        Vec3 toward = desired.subtract(this.boss.position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            horizontal = horizontal.normalize();
            Vec3 motion = this.boss.getDeltaMovement();
            this.boss.setDeltaMovement(motion.x * 0.82D + horizontal.x * 0.12D, motion.y, motion.z * 0.82D + horizontal.z * 0.12D);
        }
    }

    private float randomSlashRoll(ServerLevel level) {
        float[] candidates = new float[]{-90.0F, -68.0F, -45.0F, -22.0F, 0.0F, 0.0F, 22.0F, 45.0F, 68.0F, 90.0F};
        return candidates[level.random.nextInt(candidates.length)];
    }


    private Vec3 randomLightningBeamDirection(ServerLevel level, Vec3 towardTarget) {
        Vec3 random = new Vec3(level.random.nextDouble() * 2.0D - 1.0D,
                level.random.nextDouble() * 1.7D - 0.85D,
                level.random.nextDouble() * 2.0D - 1.0D);
        if (random.lengthSqr() < 1.0E-4D) {
            random = new Vec3(0.0D, -0.25D, 1.0D);
        }
        random = random.normalize();
        if (towardTarget.lengthSqr() > 1.0E-4D) {
            Vec3 targetDir = towardTarget.normalize();
            Vec3 tangent = new Vec3(-targetDir.z, 0.0D, targetDir.x);
            if (tangent.lengthSqr() < 1.0E-4D) {
                tangent = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                tangent = tangent.normalize();
            }
            double fan = (level.random.nextDouble() * 2.0D - 1.0D) * 1.25D;
            random = random.scale(0.86D)
                    .add(targetDir.scale(0.18D))
                    .add(tangent.scale(fan))
                    .add(0.0D, -0.22D, 0.0D)
                    .normalize();
        }
        return random;
    }

    private List<Vec3> createWhiteHolePositions(ServerLevel level, Vec3 center, int count, double radius) {
        List<Vec3> positions = new ArrayList<>();
        if (count <= 0) {
            return positions;
        }

        double innerRadius = radius * 0.78D;
        double outerRadius = radius * 1.28D;
        double minSpacing = Math.max(9.0D, radius * 0.55D);
        int attempts = count * 18;

        while (positions.size() < count && attempts-- > 0) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double localRadius = Mth.lerp(level.random.nextDouble(), innerRadius, outerRadius);
            double y = center.y + (level.random.nextDouble() - 0.5D) * 9.0D + (positions.size() % 3 == 0 ? 3.0D : -1.5D);
            Vec3 candidate = new Vec3(center.x + Math.cos(angle) * localRadius, y, center.z + Math.sin(angle) * localRadius);
            boolean tooClose = false;
            for (Vec3 existing : positions) {
                if (existing.distanceToSqr(candidate) < minSpacing * minSpacing) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                positions.add(candidate);
            }
        }

        while (positions.size() < count) {
            double angle = (Math.PI * 2.0D * positions.size() / count) + level.random.nextDouble() * 0.35D;
            double localRadius = innerRadius + (positions.size() & 1) * (outerRadius - innerRadius) * 0.65D;
            double y = center.y + ((positions.size() & 1) == 0 ? 3.8D : -3.2D);
            positions.add(new Vec3(center.x + Math.cos(angle) * localRadius, y, center.z + Math.sin(angle) * localRadius));
        }
        return positions;
    }

    private Vec3 worldRendOrigin(ServerLevel level, LivingEntity target, Vec3 baseOrigin) {
        int targetSurfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(target.getX()), Mth.floor(target.getZ()));
        double cutY = Mth.clamp(target.getY() + 1.25D, targetSurfaceY - 6.0D, targetSurfaceY + 10.0D);
        return new Vec3(baseOrigin.x, cutY, baseOrigin.z);
    }

    private Vec3 groundCenter(ServerLevel level, Vec3 baseCenter) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(baseCenter.x), Mth.floor(baseCenter.z));
        return new Vec3(baseCenter.x, groundY, baseCenter.z);
    }

    private Vec3 selectOrbImpact(ServerLevel level, LivingEntity target, Vec3 castDirection) {
        Vec3 targetPos = target.position();
        Vec3 predicted = targetPos.add(target.getDeltaMovement().scale(22.0D));
        Vec3 impactBase = predicted.add(castDirection.scale(4.0D));
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(impactBase.x), Mth.floor(impactBase.z));
        return new Vec3(impactBase.x, groundY, impactBase.z);
    }

    private void sendWorldRendCinematic(ServerLevel level, Vec3 origin) {
        AABB area = new AABB(origin.x - 96.0D, origin.y - 64.0D, origin.z - 96.0D, origin.x + 96.0D, origin.y + 64.0D, origin.z + 96.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            PacketHandler.sendToPlayer(new SPacketWorldRendCinematic(origin.x, origin.y, origin.z), player);
        }
    }

    @Override
    public void stop() {
        this.faultSplitCastData = null;
        this.boss.getNavigation().stop();
    }

    private record FaultSplitCastData(Vec3 origin, Vec3 direction, double lineLength, int terrainHalfWidth, int lineHalfWidth, int shiftDistance, int telegraphTicks, int visualLifetime) {
    }

    private record WeightedSkill(int skill, int weight) {
    }
}


