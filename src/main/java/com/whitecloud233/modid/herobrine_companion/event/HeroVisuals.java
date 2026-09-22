package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state.HeroMindStateRegistry;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroGestureLibrary;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroIdleClips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class HeroVisuals {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 手势淡入/淡出步长(每 tick):0.25 → 4 tick ≈ 0.2 s,对齐 Bedrock hero_offer 的 blend_transition。 */
    private static final float OFFER_BLEND_STEP = 0.25F;
    /** 待机变体淡入/淡出步长(每 tick):0.2 → 5 tick = 0.25 s,对齐 Bedrock grounded 的 blend_transition。 */
    private static final float VARIANT_BLEND_STEP = 0.2F;
    public static void tickClientAnimations(HeroEntity hero) {
        if (hero.level().isClientSide) {
            tickOfferGesture(hero);
            tickIdleVariants(hero);
            // 权重步进放在最后:新起的手势/变体本 tick 就能拿到正确的目标权重
            tickBlendWeights(hero);
        }
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
        HeroMindStateRegistry.tickClientAmbient(hero);
    }

    // --- 赠礼手势:客户端播放驱动(服务端只发 cue,见 HeroEntity.playOfferGesture)---
    private static void tickOfferGesture(HeroEntity hero) {
        int clipId = hero.getOfferGestureId();
        if (clipId == 0) {
            // 服务端已收尾:本地裁剪保留到淡出结束(blend_out),由 tickBlendWeights 统一清理,
            // 避免姿态在权重还没降下来时被瞬间踢回基础姿态
            if (hero.clientOfferBlend <= 0.001F && hero.gestureLocalClipId != 0) {
                hero.gestureEventCursor = 0;
                hero.gestureLocalClipId = 0;
                hero.gestureLocalFinished = false;
            }
            return;
        }
        int token = hero.getOfferGestureToken();
        if (token != hero.gestureEventTokenSeen) {
            // 新一次手势:以客户端本地 tick 为起点,不依赖服务端 tickCount(两端起点不同)
            hero.gestureEventTokenSeen = token;
            hero.gestureEventCursor = 0;
            hero.gestureLocalClipId = clipId;
            hero.gestureLocalStartTick = hero.tickCount;
            hero.gestureLocalFinished = false;
            LOGGER.debug("[HeroGift] offer gesture started client-side: clipId={} token={} localTick={}",
                    clipId, token, hero.tickCount);
        }
        HeroClip clip = HeroGestureLibrary.byClipId(clipId);
        if (clip == null) {
            hero.clearOfferGestureClient();
            hero.gestureLocalClipId = 0;
            hero.gestureLocalFinished = false;
            return;
        }
        if (hero.gestureLocalFinished) {
            return;
        }
        int elapsed = hero.tickCount - hero.gestureLocalStartTick;
        int durationTicks = Math.max(1, Math.round(clip.duration * 20.0F));
        if (elapsed >= durationTicks) {
            // 播完即停:姿态钉在末帧,权重由 tickBlendWeights 淡出到 0(不再硬切)
            hero.gestureLocalFinished = true;
            hero.gestureEventCursor = clip.events.length;
            return;
        }
        float t = elapsed / 20.0F;
        while (hero.gestureEventCursor < clip.events.length) {
            HeroClip.Event e = clip.events[hero.gestureEventCursor];
            if (e.time() > t) {
                break;
            }
            if (e.kind() == HeroClip.EV_SOUND) {
                playClipSound(hero, e.arg());
            } else if (e.kind() == HeroClip.EV_END) {
                // 收尾交给权重淡出(直接清会让末段瞬间跳回基础姿态)
                hero.gestureLocalFinished = true;
                hero.gestureEventCursor = clip.events.length;
                break;
            }
            hero.gestureEventCursor++;
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
        if (hero.idleVariantActive && hero.idleVariantClip >= 0) {
            HeroClip clip = HeroIdleClips.IDLE_VARIANTS[hero.idleVariantClip];
            int durationTicks = Math.round(clip.duration * 20.0F);
            int elapsed = hero.tickCount - hero.idleVariantStartTick;
            if (elapsed >= durationTicks) {
                // 播放窗口结束:姿态由权重淡出(blend_out),idleVariantClip 保留到淡完再被下一轮覆盖
                hero.idleVariantActive = false;
                hero.idleVariantNextAt = hero.tickCount + 120 + hero.getRandom().nextInt(121);
                return;
            }
            if ((hero.idleVariantClip == 4 || hero.idleVariantClip == 5) && hero.tickCount % 10 == 0) {
                spawnLightningIdleParticles(hero);
            }
            return;
        }
        // 上一段还在淡出时不叠新的变体,避免两段裁剪互相覆盖
        if (hero.clientVariantBlend > 0.001F) {
            return;
        }
        if (hero.tickCount < hero.idleVariantNextAt) {
            return;
        }
        hero.idleVariantNextAt = hero.tickCount + 120 + hero.getRandom().nextInt(121);
        if (hero.isBusyForIdleVisual() || hero.isFloating()) {
            return;
        }
        if (hero.getDeltaMovement().horizontalDistanceSqr() > 0.02D) {
            return;
        }
        int pool = hero.isHandsFreeForIdleVisual() ? 6 : 2;
        int roll = hero.getRandom().nextInt(pool);
        if (roll == hero.idleVariantLast) {
            roll = (roll + 1) % pool;
        }
        hero.idleVariantClip = roll;
        hero.idleVariantStartTick = hero.tickCount;
        hero.idleVariantLast = roll;
        hero.idleVariantActive = true;
    }

    /**
     * 混合权重步进:手势 / 待机变体的淡入淡出(替代原来的"一帧内硬接管")。
     *
     * <p>步长对齐 Bedrock:{@code hero_offer} 控制器 {@code blend_transition = 0.2 s},
     * {@code hero_locomotion} 各状态 {@code 0.25~0.3 s}。权重本体存在实体上(模型实例是渲染器共享的),
     * 渲染时按 partial tick 插值,见 {@code HeroEntity.getOfferBlend/getVariantBlend}。
     */
    private static void tickBlendWeights(HeroEntity hero) {
        int clipId = hero.getOfferGestureId();
        boolean offerOn = !hero.gestureLocalFinished && clipId != 0 && clipId == hero.gestureLocalClipId;
        hero.clientOfferBlendO = hero.clientOfferBlend;
        hero.clientOfferBlend = approach(hero.clientOfferBlend, offerOn ? 1.0F : 0.0F, OFFER_BLEND_STEP);
        if (!offerOn && hero.clientOfferBlend <= 0.001F && hero.gestureLocalClipId != 0) {
            // 淡出结束:清本地播放状态与展示手持物(与姿态归位同一帧,不会看到"手里东西先消失")
            hero.gestureLocalClipId = 0;
            hero.gestureEventCursor = 0;
            hero.gestureLocalFinished = false;
            hero.clearOfferGestureClient();
        }

        boolean variantOn = hero.idleVariantActive
                && !hero.isBusyForIdleVisual()
                && !hero.isFloating()
                && hero.getDeltaMovement().horizontalDistanceSqr() <= 0.02D;
        hero.clientVariantBlendO = hero.clientVariantBlend;
        hero.clientVariantBlend = approach(hero.clientVariantBlend, variantOn ? 1.0F : 0.0F, VARIANT_BLEND_STEP);
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
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
