package com.whitecloud233.herobrine_companion.entity.gift;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * 赠礼表现反馈 —— 音效/粒子/心照恩典/夜间引路灯/生日烛光。
 *
 * <p>单一职责:只管"表演",不决策。判定结果由 HeroOfferService 传入,
 * 具体音效/粒子映射与 Bedrock hero_player_offer_feedback 一致。
 */
public final class HeroGiftFeedbackService {

    private HeroGiftFeedbackService() {
    }

    // ------------------------------------------------------------------
    // 音效与粒子（等价 apply_offer_feedback 的映射）
    // ------------------------------------------------------------------

    public static void lookAtPlayer(HeroEntity hero, ServerPlayer player) {
        hero.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    public static void playOfferFeedback(HeroEntity hero, ServerPlayer player,
                                         HeroGiftResult result, String mood) {
        String code = result.code();
        SoundEvent sound;
        ParticleOptions particle;
        float pitch;
        if ("grumpy".equals(mood)) {
            sound = SoundEvents.GENERIC_EXTINGUISH_FIRE;
            particle = null;
            pitch = 0.7F;
        } else if ("delighted".equals(mood)) {
            sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
            particle = ParticleTypes.HAPPY_VILLAGER;
            pitch = 1.5F;
        } else if (result.accepted()) {
            sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
            particle = ParticleTypes.SMOKE;
            pitch = 1.25F;
        } else if ("judged".equals(code)) {
            sound = SoundEvents.LIGHTNING_BOLT_THUNDER;
            particle = new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.8F, 0.1F, 0.1F), 1.0F);
            pitch = 0.6F;
        } else {
            sound = SoundEvents.GENERIC_EXTINGUISH_FIRE;
            particle = ParticleTypes.ANGRY_VILLAGER;
            pitch = 0.9F;
        }
        if (hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, hero.getX(), hero.getY() + 1.1D, hero.getZ(),
                    sound, SoundSource.NEUTRAL, 0.55F, pitch);
            if (particle != null) {
                serverLevel.sendParticles(particle, hero.getX(), hero.getY() + 1.1D, hero.getZ(),
                        6, 0.3D, 0.3D, 0.3D, 0.02D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 心照恩典 / 眷顾效果
    // ------------------------------------------------------------------

    public static void applyGraceBuff(ServerPlayer player, List<HeroGiftSecret.GraceBuff> buffs) {
        for (HeroGiftSecret.GraceBuff buff : buffs) {
            applyEffect(player, buff.effect(), buff.duration(), buff.amplifier());
        }
    }

    public static void applyNightGrace(ServerPlayer player) {
        for (String effect : HeroGiftSecret.NIGHT_GRACE_EFFECTS) {
            applyEffect(player, effect, HeroGiftSecret.NIGHT_GRACE_DURATION_SECONDS, 0);
        }
        player.level().playSound(null, player.getX(), player.getY() + 0.6D, player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.2F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.6D, player.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.02D);
        }
    }

    /** 施加一个按名字指定的效果(赠礼心照恩典、生日蛋糕祝福共用;未知名忽略)。 */
    public static void applyEffect(ServerPlayer player, String name, int durationSeconds, int amplifier) {
        Holder<MobEffect> effect = switch (name) {
            case "absorption" -> MobEffects.ABSORPTION;
            case "speed" -> MobEffects.MOVEMENT_SPEED;
            case "haste" -> MobEffects.DIG_SPEED;
            case "strength" -> MobEffects.DAMAGE_BOOST;
            case "resistance" -> MobEffects.DAMAGE_RESISTANCE;
            case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
            case "water_breathing" -> MobEffects.WATER_BREATHING;
            case "health_boost" -> MobEffects.HEALTH_BOOST;
            case "saturation" -> MobEffects.SATURATION;
            case "luck" -> MobEffects.LUCK;
            case "night_vision" -> MobEffects.NIGHT_VISION;
            case "jump_boost" -> MobEffects.JUMP;
            case "regeneration" -> MobEffects.REGENERATION;
            case "invisibility" -> MobEffects.INVISIBILITY;
            case "conduit_power" -> MobEffects.CONDUIT_POWER;
            case "dolphin_grace" -> MobEffects.DOLPHINS_GRACE;
            case "village_hero" -> MobEffects.HERO_OF_THE_VILLAGE;
            default -> null;
        };
        if (effect != null) {
            player.addEffect(new MobEffectInstance(effect, durationSeconds * 20, amplifier));
        }
    }

    // ------------------------------------------------------------------
    // 夜间引路灯（红石火把）
    // ------------------------------------------------------------------

    /** 在玩家 2 格邻域找一个安全位置放红石火把;成功返回坐标串 "x,y,z"。 */
    public static String placeNightHint(Level level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}};
        for (int[] off : offsets) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos target = center.offset(off[0], dy, off[1]);
                BlockPos support = target.below();
                if (!level.isEmptyBlock(target)) {
                    continue;
                }
                if (level.isEmptyBlock(support) || level.getBlockState(support).getFluidState().isSource()) {
                    continue;
                }
                level.setBlock(target, Blocks.REDSTONE_TORCH.defaultBlockState(), 3);
                level.playSound(null, target.getX() + 0.5D, target.getY() + 0.6D, target.getZ() + 0.5D,
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.45F, 0.75F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                                    new org.joml.Vector3f(0.8F, 0.1F, 0.1F), 1.0F),
                            target.getX() + 0.5D, target.getY() + 0.6D, target.getZ() + 0.5D,
                            10, 0.3D, 0.3D, 0.3D, 0.02D);
                }
                return target.getX() + "," + target.getY() + "," + target.getZ();
            }
        }
        return null;
    }

    public static void removeNightHint(Level level, String posStr) {
        if (posStr == null) {
            return;
        }
        try {
            String[] parts = posStr.split(",");
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
            if (level.getBlockState(pos).is(Blocks.REDSTONE_TORCH)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 生日烛光（粒子近似;Bedrock 为客户端方块几何轨道）
    // ------------------------------------------------------------------

    public static void spawnBirthdayCandleParticles(Level level, HeroEntity hero) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double yaw = Math.toRadians(hero.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0D) + hero.tickCount * 0.06D;
            double radius = 1.1D;
            double x = hero.getX() - sin * radius * Math.cos(angle) + cos * radius * Math.sin(angle);
            double z = hero.getZ() + cos * radius * Math.cos(angle) + sin * radius * Math.sin(angle);
            double y = hero.getY() + 0.15D + Math.sin(hero.tickCount * 0.1D + i) * 0.12D;
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }
}