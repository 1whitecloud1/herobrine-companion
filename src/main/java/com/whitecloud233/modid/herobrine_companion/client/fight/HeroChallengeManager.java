package com.whitecloud233.modid.herobrine_companion.client.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.client.fight.goal.HeroPhase1Goal;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class HeroChallengeManager {

    public static final Map<Integer, Float> DIFFICULTY_DAMAGE_MULTIPLIER = new HashMap<>();
    public static final Map<Integer, Float> DIFFICULTY_MAX_HEALTH = new HashMap<>();

    static {
        DIFFICULTY_DAMAGE_MULTIPLIER.put(0, 0.5f);
        DIFFICULTY_DAMAGE_MULTIPLIER.put(1, 1.0f);
        DIFFICULTY_DAMAGE_MULTIPLIER.put(2, 2.0f);

        DIFFICULTY_MAX_HEALTH.put(0, 500.0f);
        DIFFICULTY_MAX_HEALTH.put(1, 1000.0f);
        DIFFICULTY_MAX_HEALTH.put(2, 3000.0f);
    }

    public static void startChallenge(HeroEntity oldHero, ServerPlayer player, int challengeMode) {
        ServerLevel currentLevel = (ServerLevel) oldHero.level();
        MinecraftServer server = currentLevel.getServer();
        ServerLevel endRingLevel = server.getLevel(ModStructures.END_RING_DIMENSION_KEY);

        if (endRingLevel == null) {
            player.sendSystemMessage(Component.literal("§c[系统] 无法连接到试炼维度，挑战失败！"));
            return;
        }

        // 1. 保存主世界坐标 (仅当玩家从其他维度进入时保存)
        if (currentLevel.dimension() != ModStructures.END_RING_DIMENSION_KEY) {
            CompoundTag playerData = player.getPersistentData();
            playerData.putString("ChallengeReturnDim", currentLevel.dimension().location().toString());
            playerData.putDouble("ChallengeReturnX", player.getX());
            playerData.putDouble("ChallengeReturnY", player.getY());
            playerData.putDouble("ChallengeReturnZ", player.getZ());
        }

        // 2. 提前给玩家和 Hero 打上挑战标记，屏蔽所有的剧情和维度截杀
        player.getPersistentData().putBoolean("IsChallengeActive", true);
        oldHero.getPersistentData().putBoolean("IsChallengeActive", true);

        // 3. 无论是否已经在试炼维度，都强制将玩家拉入 102.0 决战平台！
        player.teleportTo(endRingLevel, 0.5, 102.0, 5.5, player.getYRot(), player.getXRot());

        // 4. 处理 Hero 的传送与目标锁定
        if (currentLevel.dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            // 如果已经在 End Ring，只需将 Hero 传送到擂台中心
            oldHero.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
            setupChallengeEntity(oldHero, player, challengeMode);
        } else {
            // 如果跨维度，使用安全的克隆复位逻辑
            Entity teleportedEntity = oldHero.changeDimension(endRingLevel, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                    // 【核心修复】必须作用于 apply(false) 返回的新实体！
                    // 这彻底切断了原版寻找末地/地狱传送门的底层机制对坐标的覆盖
                    Entity newEntity = repositionEntity.apply(false);
                    if (newEntity != null) {
                        newEntity.setPos(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                        newEntity.setDeltaMovement(0, 0, 0); // 清空动量，防止因传送前的跳跃飞出擂台
                    }
                    return newEntity;
                }
            });

            if (teleportedEntity instanceof HeroEntity newHero) {
                setupChallengeEntity(newHero, player, challengeMode);
            }
        }
    }

    private static void setupChallengeEntity(HeroEntity hero, ServerPlayer target, int challengeMode) {
        // 在 setupChallengeEntity 方法开头添加
        hero.removeTag(EndRingContext.TAG_INTRO);
        hero.removeTag(EndRingContext.TAG_FIXED);
        hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);

        hero.goalSelector.removeAllGoals(goal -> true);
        hero.targetSelector.removeAllGoals(goal -> true);
        hero.setTarget(null);
        hero.getNavigation().stop();

        hero.moveControl = new HeroMoveControl(hero);

        float damageMultiplier = DIFFICULTY_DAMAGE_MULTIPLIER.getOrDefault(challengeMode, 1.0f);
        float maxHealth = DIFFICULTY_MAX_HEALTH.getOrDefault(challengeMode, 1000.0f);

        hero.getPersistentData().putFloat("ChallengeDamageMultiplier", damageMultiplier);
        hero.getPersistentData().putBoolean("IsChallengeActive", true);

        hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
        hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, 0);

        hero.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        hero.setHealth(hero.getMaxHealth());

        hero.goalSelector.addGoal(1, new HeroPhase1Goal(hero));
        if (target != null) {
            target.sendSystemMessage(Component.literal("§c[系统] 试炼已启动，目标锁定！").withStyle(ChatFormatting.BOLD));
            // 缓慢下落防止网络延迟时掉虚空
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
    }

    public static void endChallenge(HeroEntity hero, boolean playerWon) {
        hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);
        hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, 0);
        hero.getPersistentData().putBoolean("IsChallengeActive", false);
        hero.getPersistentData().remove("ChallengePhaseTicks");
        hero.goalSelector.removeAllGoals(goal -> true);
        hero.targetSelector.removeAllGoals(goal -> true);
        hero.setTarget(null);
        hero.getNavigation().stop();

        hero.moveControl = new HeroMoveControl(hero);
        HeroAI.registerGoals(hero);

        hero.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        hero.setHealth(hero.getMaxHealth());

        hero.setNoGravity(false);
        hero.setFloating(false);
        hero.setDeltaMovement(0, 0, 0);

        if (playerWon && !hero.level().isClientSide) {
            hero.level().playSound(null, hero.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            if (hero.getOwnerUUID() != null) {
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner instanceof ServerPlayer serverPlayer) {
                    owner.sendSystemMessage(Component.translatable("message.herobrine_companion.challenge_victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    hero.increaseTrust(5);

                    returnToSavedDimension(hero, serverPlayer);
                }
            }
        }
    }

    private static void returnToSavedDimension(HeroEntity hero, ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains("ChallengeReturnDim")) return;

        String dimName = playerData.getString("ChallengeReturnDim");
        double rx = playerData.getDouble("ChallengeReturnX");
        double ry = playerData.getDouble("ChallengeReturnY");
        double rz = playerData.getDouble("ChallengeReturnZ");

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel returnLevel = null;
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dimName)) {
                returnLevel = lvl;
                break;
            }
        }
        if (returnLevel == null) returnLevel = server.overworld();

        // 将玩家拉回去
        player.teleportTo(returnLevel, rx, ry, rz, player.getYRot(), player.getXRot());

        // 👇 [核心修复 1] 在传送 Hero 之前，临时再给他发一张跨维度的“免检通行证”！
        // 这样你的 HeroEndringEvent 就会乖乖放行，让他回到主世界。
        hero.getPersistentData().putBoolean("IsChallengeActive", true);

        // 将 Hero 拉回去
        hero.changeDimension(returnLevel, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                // 这个 apply 会在主世界生成一个他的克隆体，并触发加入世界的查重事件
                Entity e = repositionEntity.apply(false);
                e.setPos(rx + 1.0, ry, rz + 1.0);

                // 👇 [核心修复 2] 落地主世界后，立刻没收他的免检通行证，并重新装载日常 AI！
                if (e instanceof HeroEntity newHero) {
                    newHero.getPersistentData().putBoolean("IsChallengeActive", false);
                    newHero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);

                    // 跨维度生成的克隆体可能丢失刚才设置的 AI，这里保险起见重新唤醒日常系统
                    newHero.goalSelector.removeAllGoals(goal -> true);
                    newHero.targetSelector.removeAllGoals(goal -> true);
                    newHero.setTarget(null);
                    newHero.moveControl = new HeroMoveControl(newHero);
                    HeroAI.registerGoals(newHero);
                }

                return e;
            }
        });

        // 落地后彻底清除玩家身上的挑战免检标志
        playerData.remove("ChallengeReturnDim");
        playerData.remove("ChallengeReturnX");
        playerData.remove("ChallengeReturnY");
        playerData.remove("ChallengeReturnZ");
        playerData.remove("IsChallengeActive");
    }
    public static void failChallenge(ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        playerData.putBoolean("IsChallengeActive", false);

        // ❌ 删除原来写在这里的 hero.getPersistentData().remove("ChallengePhaseTicks");

        ServerLevel level = (ServerLevel) player.level();
        HeroEntity activeHero = null;

        // 寻找正在与玩家战斗的 Hero
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity hero && hero.getPersistentData().getBoolean("IsChallengeActive")) {
                activeHero = hero;
                break;
            }
        }

        if (activeHero != null) {
            // 清理 Hero 战斗状态
            activeHero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);
            activeHero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, 0);
            activeHero.getPersistentData().putBoolean("IsChallengeActive", false);

            // ✅ 【核心修复】：在这里使用 activeHero 清除存档的进度！
            activeHero.getPersistentData().remove("ChallengePhaseTicks");

            // 恢复血量和重力
            activeHero.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            activeHero.setHealth(activeHero.getMaxHealth());
            activeHero.setNoGravity(false);
            activeHero.setFloating(false);

            // 【核心：强制保存数据给死去的玩家带走】
            CompoundTag heroData = new CompoundTag();
            activeHero.saveWithoutId(heroData);

            // 更新全局信任度，确保 NBT 完整
            com.whitecloud233.modid.herobrine_companion.entity.logic.HeroDataHandler.updateGlobalTrust(activeHero);
            heroData.putInt("TrustLevel", activeHero.getTrustLevel());

            // 挂起重生状态，触发你写好的跨维度复活逻辑
            playerData.put("HeroRespawnData", heroData);
            playerData.putBoolean("HeroPendingRespawn", true);

            // 挂起一个提示标记，等玩家活过来再告诉他失败了
            playerData.putBoolean("ChallengeFailedMessagePending", true);

            // 销毁擂台上的假体
            activeHero.discard();
        }

        // 清除返回坐标（因为玩家将走原版正常的死亡重生流程，不需要强拉）
        playerData.remove("ChallengeReturnDim");
        playerData.remove("ChallengeReturnX");
        playerData.remove("ChallengeReturnY");
        playerData.remove("ChallengeReturnZ");
    }
}