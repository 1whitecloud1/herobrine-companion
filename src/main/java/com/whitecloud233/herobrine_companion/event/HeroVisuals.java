package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.util.DebugLog;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.HeroMindStateRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.whitecloud233.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.herobrine_companion.entity.visual.HeroGestureLibrary;
import com.whitecloud233.herobrine_companion.entity.visual.HeroIdleClips;
import com.whitecloud233.herobrine_companion.entity.visual.HeroVisualState;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class HeroVisuals {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void tickClientLocomotion(HeroEntity hero) {
        if (!hero.level().isClientSide) return;
        HeroVisualState state = hero.getVisualState();
        if (!state.isSetupDone()) {
            setupHiddenTeam(hero);
            state.markSetupDone();
        }
        state.tickLocomotion(hero.isFloating(), hero.isGroundWalking(),
                hero.getX() - hero.xOld, hero.getZ() - hero.zOld);
    }

    public static void setupHiddenTeam(HeroEntity hero) {
        if (!hero.level().isClientSide) return;
        net.minecraft.world.scores.Scoreboard scoreboard = hero.level().getScoreboard();
        String teamName = "hero_hidden_hud";
        net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
        }
        scoreboard.addPlayerToTeam(hero.getStringUUID(), team);
    }

    public static void tickClientAnimations(HeroEntity hero) {
        if (!hero.level().isClientSide) return;
        tickOfferGesture(hero);
        tickIdleVariants(hero);
        tickBlendWeights(hero);
    }

    public static void tickClientActionParticles(HeroEntity hero, boolean thunderWasActive) {
        if (!hero.level().isClientSide) return;
        if (hero.debugAnimTick > 10 && hero.debugAnimTick < 90) spawnDebugParticles(hero);
        if (thunderWasActive) spawnThunderParticles(hero);
    }

    public static void tickClientAmbient(HeroEntity hero) {
        HeroMindStateRegistry.tickClientAmbient(hero);
    }

    // --- 赠礼手势:客户端播放驱动(服务端只发 cue,见 HeroEntity.playOfferGesture)---
    private static void tickOfferGesture(HeroEntity hero) {
        HeroVisualState state = hero.getVisualState();
        int clipId = hero.getOfferGestureId();
        if (clipId == 0) {
            // 服务端已收尾时，仍须保留本地裁剪直到淡出结束。
            if (state.getOfferBlend() <= 0.001F && state.getGestureClipId() != 0) {
                state.clearGesture();
            }
            return;
        }
        int token = hero.getOfferGestureToken();
        if (token != state.getGestureTokenSeen()) {
            state.startGesture(clipId, token, hero.tickCount);
            DebugLog.debug(LOGGER, "[HeroGift] offer gesture started client-side: clipId={} token={} localTick={}",
                    clipId, token, hero.tickCount);
        }
        HeroClip clip = HeroGestureLibrary.byClipId(clipId);
        if (clip == null) {
            hero.clearOfferGestureClient();
            state.clearGesture();
            return;
        }
        if (state.isGestureFinished()) return;
        int elapsed = hero.tickCount - state.getGestureStartTick();
        int durationTicks = Math.max(1, Math.round(clip.duration * 20.0F));
        if (elapsed >= durationTicks) {
            state.finishGesture(clip.events.length);
            return;
        }
        float t = elapsed / 20.0F;
        while (state.getGestureEventCursor() < clip.events.length) {
            HeroClip.Event e = clip.events[state.getGestureEventCursor()];
            if (e.time() > t) break;
            if (e.kind() == HeroClip.EV_SOUND) {
                playClipSound(hero, e.arg());
            } else if (e.kind() == HeroClip.EV_END) {
                state.finishGesture(clip.events.length);
                break;
            }
            state.advanceGestureEvent();
        }
    }

    private static void playClipSound(HeroEntity hero, String arg) {
        if (arg == null) {
            return;
        }
        String[] parts = arg.split("\\|");
        if (parts.length < 3) {
            return;
        }
        float volume = Float.parseFloat(parts[1]);
        float pitch = Float.parseFloat(parts[2]);
        // Bedrock 音效名 → Java SoundEvent(random.eat 等少量映射;未知的忽略)
        net.minecraft.sounds.SoundEvent sound = switch (parts[0]) {
            case "random.eat" -> SoundEvents.GENERIC_EAT;
            default -> null;
        };
        if (sound != null) {
            hero.level().playLocalSound(hero.getX(), hero.getY() + 1.0D, hero.getZ(),
                    sound, SoundSource.NEUTRAL, volume, pitch, false);
        }
    }

    // --- 待机变体:纯客户端调度(Bedrock hero_locomotion 控制器 6~12 秒随机等价)---
    private static void tickIdleVariants(HeroEntity hero) {
        HeroVisualState state = hero.getVisualState();
        if (state.isIdleVariantActive() && state.getIdleVariantClip() >= 0) {
            HeroClip clip = HeroIdleClips.IDLE_VARIANTS[state.getIdleVariantClip()];
            int durationTicks = Math.round(clip.duration * 20.0F);
            int elapsed = hero.tickCount - state.getIdleVariantStartTick();
            if (elapsed >= durationTicks) {
                state.finishIdleVariant(hero.tickCount + 120 + hero.getRandom().nextInt(121));
                return;
            }
            if ((state.getIdleVariantClip() == 4 || state.getIdleVariantClip() == 5) && hero.tickCount % 10 == 0) {
                spawnLightningIdleParticles(hero);
            }
            return;
        }
        // 上一段仍在淡出时不叠加新的变体。
        if (state.getVariantBlend() > 0.001F || hero.tickCount < state.getIdleVariantNextAt()) return;
        state.scheduleIdleVariant(hero.tickCount + 120 + hero.getRandom().nextInt(121));
        if (hero.isBusyForIdleVisual() || hero.isFloating()) return;
        if (hero.getDeltaMovement().horizontalDistanceSqr() > 0.02D) return;
        int pool = hero.isHandsFreeForIdleVisual() ? 6 : 2;
        int roll = hero.getRandom().nextInt(pool);
        if (roll == state.getIdleVariantLast()) roll = (roll + 1) % pool;
        state.startIdleVariant(roll, hero.tickCount);
    }

    private static void tickBlendWeights(HeroEntity hero) {
        HeroVisualState state = hero.getVisualState();
        int clipId = hero.getOfferGestureId();
        boolean offerOn = !state.isGestureFinished() && clipId != 0 && clipId == state.getGestureClipId();
        state.tickOfferBlend(offerOn);
        if (!offerOn && state.getOfferBlend() <= 0.001F && state.getGestureClipId() != 0) {
            // 本地姿态淡出后才清展示物，避免物品比手势先消失。
            state.clearGesture();
            hero.clearOfferGestureClient();
        }

        boolean variantOn = state.isIdleVariantActive()
                && !hero.isBusyForIdleVisual()
                && !hero.isFloating()
                && hero.getDeltaMovement().horizontalDistanceSqr() <= 0.02D;
        state.tickVariantBlend(variantOn);
    }

    private static void spawnLightningIdleParticles(HeroEntity hero) {
        double yaw = Math.toRadians(hero.yBodyRot + 90);
        double handX = hero.getX() + Math.cos(yaw) * 0.55;
        double handY = hero.getY() + 1.35;
        double handZ = hero.getZ() + Math.sin(yaw) * 0.55;
        RandomSource rand = hero.getRandom();
        for (int i = 0; i < 2; i++) {
            hero.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    handX + (rand.nextDouble() - 0.5) * 0.4,
                    handY + (rand.nextDouble() - 0.5) * 0.4,
                    handZ + (rand.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        if (rand.nextInt(4) == 0) {
            hero.level().addParticle(ParticleTypes.END_ROD, handX, handY, handZ, 0, 0.02, 0);
        }
    }

    private static void spawnDebugParticles(HeroEntity hero) {
        if (hero.getRandom().nextInt(5) != 0) return;

        double yawRad = Math.toRadians(hero.yBodyRot);
        double x = hero.getX() - Math.sin(yawRad) * 0.8 + Math.cos(yawRad) * 0.3;
        double y = hero.getY() + 1.4;
        double z = hero.getZ() + Math.cos(yawRad) * 0.8 + Math.sin(yawRad) * 0.3;

        hero.level().addParticle(ParticleTypes.ENCHANT,
                x + (hero.getRandom().nextDouble() - 0.5) * 0.5,
                y + (hero.getRandom().nextDouble() - 0.5) * 0.4,
                z + (hero.getRandom().nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
    }

    private static void spawnThunderParticles(HeroEntity hero) {
        if (!hero.level().isClientSide) return;

        double yaw = Math.toRadians(hero.yBodyRot + 90);
        double handX = hero.getX() + Math.cos(yaw) * 0.6;
        double handY = hero.getY() + 2.8;
        double handZ = hero.getZ() + Math.sin(yaw) * 0.6;

        RandomSource rand = hero.getRandom();

        for (int i = 0; i < 3; i++) {
            hero.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    handX + (rand.nextDouble() - 0.5) * 0.5,
                    handY + (rand.nextDouble() - 0.5) * 0.5,
                    handZ + (rand.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }

        float progress = 1.0F - (float) hero.thunderTicks / HeroEntity.MAX_THUNDER_TICKS;
        if (progress > 0.3F) {
            for (int i = 0; i < 2; i++) {
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