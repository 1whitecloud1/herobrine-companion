package com.whitecloud233.modid.herobrine_companion.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroAI;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.modid.herobrine_companion.fight.goal.HeroPhase1Goal;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketStartCollapse;
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
import java.util.UUID;

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

        // 👇 新增：检查全局锁
        HeroWorldData worldData = HeroWorldData.get(currentLevel);
        UUID currentChallenger = worldData.getActiveChallengerUUID();

        if (currentChallenger != null) {
            ServerPlayer challenger = server.getPlayerList().getPlayer(currentChallenger);
            // 如果锁定的玩家在线，且真的在挑战中，则拦截当前玩家
            if (challenger != null && challenger.getPersistentData().getBoolean("IsChallengeActive")) {
                player.sendSystemMessage(Component.literal("§c[系统] 试炼场地已被玩家 §e" + challenger.getName().getString() + " §c占用，请稍后再试！"));
                return;
            } else {
                // 如果锁定的玩家已经离线或者状态异常，说明是死锁，强行解开
                worldData.setActiveChallengerUUID(null);
            }
        }
        // 正式上锁
        worldData.setActiveChallengerUUID(player.getUUID());

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
        hero.removeTag(EndRingContext.TAG_INTRO);
        hero.removeTag(EndRingContext.TAG_FIXED);
        hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
// 【新增】：每次开启挑战，必须清空上一次的假死状态，防止永久无敌！
        hero.getPersistentData().remove("IsFakeOutPhase");
        if (target != null) {
            target.getPersistentData().remove("HeroFakeOutPhase");
        }
        hero.goalSelector.removeAllGoals(goal -> true);
        hero.targetSelector.removeAllGoals(goal -> true);
        hero.setTarget(null);
        hero.getNavigation().stop();

        hero.moveControl = new HeroMoveControl(hero);

        hero.clearChallengeAfterimages();
        hero.getPersistentData().putInt("ChallengeMode", challengeMode);
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

            // ✅ 新增：战前备份并剥夺生存模式飞行能力
            if (!target.isCreative() && !target.isSpectator()) {
                target.getPersistentData().putBoolean("PreChallengeMayFly", target.getAbilities().mayfly);
                target.getAbilities().mayfly = false;
                target.getAbilities().flying = false;
                target.onUpdateAbilities();
            }
        }
    }

    public static void endChallenge(HeroEntity hero, boolean playerWon) {
        hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);
        hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, 0);
        hero.getPersistentData().putBoolean("IsChallengeActive", false);
        hero.getPersistentData().remove("ChallengePhaseTicks");
        hero.clearChallengeAfterimages();
        // 【新增】：结束时清空假死标记
        hero.getPersistentData().remove("IsFakeOutPhase");
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
// 在 endChallenge 和 failChallenge 方法的末尾添加：
        if (hero.getServer() != null) {
            HeroWorldData.get(hero.getServer().overworld()).setActiveChallengerUUID(null);
        }
        if (playerWon && !hero.level().isClientSide) {
            hero.level().playSound(null, hero.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            ServerPlayer challengePlayer = resolveChallengePlayer(hero);
            if (challengePlayer != null) {
                clearPlayerChallengeFlags(challengePlayer);
                challengePlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.challenge_victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                hero.increaseTrust(5);

                returnToSavedDimension(hero, challengePlayer);
            }
        }
    }

    private static ServerPlayer resolveChallengePlayer(HeroEntity hero) {
        if (hero.getServer() == null) {
            return null;
        }

        if (hero.getOwnerUUID() != null) {
            Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
            if (owner instanceof ServerPlayer serverPlayer && serverPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                return serverPlayer;
            }
        }

        UUID activeChallenger = HeroWorldData.get(hero.getServer().overworld()).getActiveChallengerUUID();
        if (activeChallenger != null) {
            ServerPlayer lockedPlayer = hero.getServer().getPlayerList().getPlayer(activeChallenger);
            if (lockedPlayer != null && lockedPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                return lockedPlayer;
            }
        }

        if (hero.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                    return player;
                }
            }
        }

        return null;
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

        // [核心修复 1] 在传送 Hero 之前，临时再给他发一张跨维度的“免检通行证”！
        hero.getPersistentData().putBoolean("IsChallengeActive", true);

        // 将 Hero 拉回去
        hero.changeDimension(returnLevel, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                Entity e = repositionEntity.apply(false);
                e.setPos(rx + 1.0, ry, rz + 1.0);

                // [核心修复 2] 落地主世界后，立刻没收他的免检通行证，并重新装载日常 AI！
                if (e instanceof HeroEntity newHero) {
                    newHero.getPersistentData().putBoolean("IsChallengeActive", false);
                    newHero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);

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

        // ✅ 新增：胜利并传送回主世界后，恢复飞行权限
        restoreFlightAbilities(player);

        playerData.remove("HeroFakeOutPhase");
        playerData.remove("IsChallengeActive");
        // ==========================================
        // 【新增】：玩家已安全回到主世界，瞬间在后台重置 End Ring 场地！
        // ==========================================
        ServerLevel endRingLevel = server.getLevel(ModStructures.END_RING_DIMENSION_KEY);
        if (endRingLevel != null) {
            com.whitecloud233.modid.herobrine_companion.world.structure.EndRingRestorer.restoreArena(endRingLevel);
        }
    }

    public static void failChallenge(ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        playerData.putBoolean("IsChallengeActive", false);
        playerData.remove("HeroFakeOutPhase");

        ServerLevel level = (ServerLevel) player.level();
        HeroEntity activeHero = null;


        // 寻找正在与玩家战斗的 Hero (已优化为 O(1) 查找)
        for (HeroEntity hero : com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (hero.level() == level && hero.isAlive() && hero.getPersistentData().getBoolean("IsChallengeActive")) {
                activeHero = hero;
                break;
            }
        }

        if (activeHero != null) {
            // 清理 Hero 战斗状态
            activeHero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, false);
            activeHero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, 0);
            activeHero.getPersistentData().putBoolean("IsChallengeActive", false);
            activeHero.clearChallengeAfterimages();

            // 【核心修复】：在这里使用 activeHero 清除存档的进度！
            activeHero.getPersistentData().remove("ChallengePhaseTicks");
// 【新增】：失败时清空假死标记
            activeHero.getPersistentData().remove("IsFakeOutPhase");
            player.getPersistentData().remove("HeroFakeOutPhase");
            // 恢复血量和重力
            activeHero.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            activeHero.setHealth(activeHero.getMaxHealth());
            activeHero.setNoGravity(false);
            activeHero.setFloating(false);

            // 【核心：强制保存数据给死去的玩家带走】
            CompoundTag heroData = new CompoundTag();
            activeHero.saveWithoutId(heroData);

            // 更新全局信任度，确保 NBT 完整
            HeroDataHandler.updateGlobalTrust(activeHero);
            heroData.putInt("TrustLevel", activeHero.getTrustLevel());

            // 挂起重生状态，触发你写好的跨维度复活逻辑
            playerData.put("HeroRespawnData", heroData);
            playerData.putBoolean("HeroPendingRespawn", true);

            // 挂起一个提示标记，等玩家活过来再告诉他失败了
            playerData.putBoolean("ChallengeFailedMessagePending", true);

            // 销毁擂台上的假体
            activeHero.discard();
        }

        // 清除返回坐标
        playerData.remove("ChallengeReturnDim");
        playerData.remove("ChallengeReturnX");
        playerData.remove("ChallengeReturnY");
        playerData.remove("ChallengeReturnZ");

        // ✅ 新增：试炼失败时，恢复飞行权限
        restoreFlightAbilities(player);

        // ==========================================
        // 【新增】：即使玩家战败，也要将场地重置，为下一次挑战做准备！
        // ==========================================
        ServerLevel endRingLevel = player.getServer().getLevel(ModStructures.END_RING_DIMENSION_KEY);
        if (endRingLevel != null) {
            com.whitecloud233.modid.herobrine_companion.world.structure.EndRingRestorer.restoreArena(endRingLevel);
        }
        // 在 endChallenge 和 failChallenge 方法的末尾添加：
        if (player.getServer() != null) {
            HeroWorldData.get(player.getServer().overworld()).setActiveChallengerUUID(null);
        }
    } // 这是 failChallenge 方法的大括号

    // ✅ 新增：战后恢复玩家飞行权限的通用方法
    public static void restoreFlightAbilities(ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        if (playerData.contains("PreChallengeMayFly")) {
            boolean couldFly = playerData.getBoolean("PreChallengeMayFly");
            // 如果玩家战前能飞，且现在不是创造/旁观模式，就把飞行还给他
            if (couldFly && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            // 阅后即焚，清理掉备份数据
            playerData.remove("PreChallengeMayFly");
        }
    }

    private static void clearPlayerChallengeFlags(ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        playerData.remove("HeroFakeOutPhase");
        playerData.remove("IsChallengeActive");
    }

    // ==========================================
    // [新增] 试炼第一阶段：虚晃一枪（撤回神力）
    // ==========================================
    public static void triggerFakeOutPhase(HeroEntity hero) {
        // 1. 标记 Boss 进入假死演出阶段
        hero.getPersistentData().putBoolean("IsFakeOutPhase", true);

        // 2. 切断神明与世界的联系：清空所有行动 AI
        hero.goalSelector.removeAllGoals(goal -> true);
        hero.targetSelector.removeAllGoals(goal -> true);
        hero.setTarget(null);
        hero.getNavigation().stop();

        // 3. 物理静止：强制悬浮在半空，不受重力影响
        hero.setDeltaMovement(0, 0, 0);
        hero.setNoGravity(true);

        // 4. 处理场内玩家并发送视觉崩坏数据包
        if (!hero.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) hero.level();

            // 遍历当前维度（End Ring）的所有玩家
            for (ServerPlayer player : serverLevel.players()) {
                // 只对正在参与试炼的玩家生效
                if (player.getPersistentData().getBoolean("IsChallengeActive")) {

                    // 给玩家打上标记，用于稍后在事件中锁血（保持半颗心不死）
                    player.getPersistentData().putBoolean("HeroFakeOutPhase", true);

                    // 【核心】使用你自己的 PacketHandler 发送崩坏数据包！
                    // 这会通知客户端开始黑屏和隐藏UI
                    com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToPlayer(
                            new SPacketStartCollapse(),
                            player
                    );
                }
            }
        }
    }
}
