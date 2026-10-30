package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncHeroVisitPacket;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.UUID;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroDimensionHandler {
    @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            // 如果是挑战传送，直接放行
            if (ChallengeSession.isPlayerInChallenge(player)) {
                return;
            }

            ServerLevel toLevel = (ServerLevel) player.level();
            ServerLevel fromLevel = player.server.getLevel(event.getFrom());
            if (event.getTo() == ModStructures.END_RING_DIMENSION_KEY) {
                handleEnterEndRing(fromLevel, toLevel, player);
            }

            if (event.getFrom() == ModStructures.END_RING_DIMENSION_KEY && event.getTo() == Level.OVERWORLD) {
                handleReturnToOverworld(fromLevel, toLevel, player);
            }
        }

    private static void handleEnterEndRing(ServerLevel fromLevel, ServerLevel endLevel, ServerPlayer player) {
        if (transferHero(fromLevel, endLevel, player, true)) {
            player.getPersistentData().putBoolean("HasVisitedHeroDimension", true);
            PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
        }
    }

    private static void handleReturnToOverworld(ServerLevel fromLevel, ServerLevel toLevel, ServerPlayer player) {
        if (player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
            PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
        }
        transferHero(fromLevel, toLevel, player, false);
    }

    private static boolean transferHero(ServerLevel fromLevel, ServerLevel toLevel, ServerPlayer player, boolean entering) {
        HeroEntity source = HeroLifecycleHandler.findOwnedHero(fromLevel != null ? fromLevel : toLevel, player.getUUID());
        CompoundTag playerData = player.getPersistentData();
        CompoundTag snapshot = null;
        if (source != null) {
            HeroStateManager.backupToGlobal(source);
            snapshot = new CompoundTag();
            source.saveWithoutId(snapshot);
        } else if (playerData.getBoolean("HeroPendingRespawn") && playerData.contains("HeroRespawnData", 10)) {
            // 兼容旧版本已挂起的实体。只有成功加入目标维度后才能消费这份快照。
            snapshot = playerData.getCompound("HeroRespawnData").copy();
        }

        HeroEntity target = source != null && source.level() == toLevel ? source : ModEntities.HERO.get().create(toLevel);
        if (target == null) return false;
        boolean needsSpawn = target != source;
        if (needsSpawn && snapshot != null) target.load(HeroDimensionSnapshot.prepare(snapshot));
        target.setOwnerUUID(player.getUUID());
        HeroStateManager.restoreFromGlobal(target, player);
        HeroDataHandler.syncGlobalTrust(target);

        target.removeTag(EndRingContext.TAG_INTRO);
        target.removeTag(EndRingContext.TAG_FIXED);
        target.removeTag(EndRingContext.TAG_TELEPORTING);
        target.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        target.getPersistentData().remove("IntroTick");
        target.setFloating(false);
        target.setNoGravity(false);
        target.setDeltaMovement(0, 0, 0);
        target.setFallDistance(0);
        target.getNavigation().stop();
        target.setTarget(null);
        if (entering) {
            net.minecraft.world.phys.Vec3 anchor = EndRingContext.heroAnchor();
            target.moveTo(anchor.x, anchor.y, anchor.z, 0, 0);
            target.addTag(EndRingContext.TAG_INTRO);
            target.addTag(EndRingContext.TAG_FIXED);
        } else {
            target.moveTo(player.getX() + 2, player.getY(), player.getZ(), player.getYRot(), 0);
            target.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
            target.markTeleportFollowHold();
        }

        if (needsSpawn) {
            target.setUUID(UUID.randomUUID());
            // Join 监听器不能抢先删除源实体或拿旧副本的空装备覆盖完整快照。
            target.addTag(EndRingContext.TAG_TRANSFER_STAGED);
            if (!toLevel.addFreshEntity(target)) return false;
        }
        HeroWorldData worldData = HeroWorldData.get(toLevel);
        worldData.setActiveHeroUUID(player.getUUID(), target.getUUID());
        worldData.setLastKnownHeroPos(player.getUUID(), net.minecraft.core.GlobalPos.of(toLevel.dimension(), target.blockPosition()));
        for (HeroEntity other : HeroLifecycleHandler.getLoadedHeroes(player.server, player.getUUID())) {
            if (other != target) other.discard();
        }
        HeroStateManager.backupToGlobal(target);
        playerData.remove("HeroPendingRespawn");
        playerData.remove("HeroRespawnData");
        return true;
    }
    public static void handleVoidProtection(HeroEntity hero) {
        if (hero.level().isClientSide || hero.level().dimension() != ModStructures.END_RING_DIMENSION_KEY
                || ChallengeSession.isHeroInChallenge(hero)) {
            return;
        }
        // Restores the invariant for existing saves as well as newly spawned actors.
        hero.addTag(EndRingContext.TAG_FIXED);
        if (hero.isPassenger()) hero.stopRiding();
        if (hero.isFloating()) hero.setFloating(false);
        hero.getNavigation().stop();
        if (hero.getMoveControl() instanceof com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl moveControl) {
            moveControl.stopMoving();
        }
        hero.setTarget(null);
        hero.setSprinting(false);
        net.minecraft.world.phys.Vec3 anchor = EndRingContext.heroAnchor();
        if (hero.distanceToSqr(anchor.x, anchor.y, anchor.z) > 1.0E-8D) {
            hero.teleportTo(anchor.x, anchor.y, anchor.z);
        }
        hero.setDeltaMovement(0, 0, 0);
        hero.setFallDistance(0);
    }

    public static void leaveWorld(HeroEntity hero, @Nullable String messageKey) {
        int cooldownMinutes = 0;

        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                CompoundTag brainData = new CompoundTag();
                hero.getHeroBrain().save(brainData);
                data.setTempBrainData(ownerUUID, brainData);
                data.setRespawnCooldown(ownerUUID, serverLevel, cooldownMinutes);
            }
        }

            // ============== [重构精简] ==============
            HeroStateManager.backupToGlobal(hero);
            // =====================================

        if (messageKey != null) {
            if (hero.getOwnerUUID() != null) {
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner != null) {
                    owner.sendSystemMessage(Component.translatable(messageKey));
                }
            }
        }

        if (hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, hero.getX(), hero.getY() + 1, hero.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            serverLevel.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
        }
        hero.remove(Entity.RemovalReason.DISCARDED);
    }

        public static void teleportRandomly(HeroEntity hero) {
            for (int i = 0; i < 64; ++i) {
                double d0 = hero.getX() + (hero.getRandom().nextDouble() - 0.5D) * 16.0D;
                double d1 = hero.getY() + (double) (hero.getRandom().nextInt(16) - 8);
                double d2 = hero.getZ() + (hero.getRandom().nextDouble() - 0.5D) * 16.0D;
                if (hero.randomTeleport(d0, d1, d2, true)) {
                    hero.level().playSound(null, hero.xo, hero.yo, hero.zo, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
                    break;
                }
            }
        }

        public static void respawnNearPlayer(ServerLevel level, ServerPlayer player) {
            // 🚀 优化 4：使用 ACTIVE_HEROES 替代 level.getAllEntities() 防止重复复活
            for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == level && hero.isAlive()) {
                    if (player.getUUID().equals(hero.getOwnerUUID())) {
                        return;
                    }
                }
            }

            HeroEntity hero = ModEntities.HERO.get().create(level);
            if (hero != null) {
                // 【核心修复1】先进行所有 NBT 数据的恢复（这步会把被删掉 Pos 的实体强行设到 0,0,0）
                boolean hasCombatData = com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager.restoreFromPlayerNBT(hero, player, "HeroCombatRespawnData");
                // 兼容容错机制
                if (!hasCombatData) {
                    com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager.restoreFromPlayerNBT(hero, player, "HeroRespawnData");
                }
                com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager.restoreFromGlobal(hero, player);
                com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler.syncGlobalTrust(hero);

                HeroWorldData worldData = HeroWorldData.get(level);
                if (worldData.getTempBrainData(player.getUUID()) != null) {
                    hero.getHeroBrain().load(worldData.getTempBrainData(player.getUUID()));
                }

                // 【核心修复2】再去计算安全的坐标，防止被 NBT 覆盖
                double bestX = player.getX();
                double bestY = player.getY();
                double bestZ = player.getZ();
                boolean foundSafePos = false;

                // 尝试寻找安全的落脚点 (最多尝试 10 次)
                for (int i = 0; i < 10; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2.0D;
                    double distance = 3.0 + level.random.nextDouble() * 4.0;
                    double testX = player.getX() + Math.cos(angle) * distance;
                    double testZ = player.getZ() + Math.sin(angle) * distance;
                    int startY = (int) player.getY();

                    // 高度相近：在玩家 Y 轴上下 4 格内寻找安全的空间
                    for (int dy = 4; dy >= -4; dy--) {
                        net.minecraft.core.BlockPos testPos = net.minecraft.core.BlockPos.containing(testX, startY + dy, testZ);
                        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(testPos.below());
                        net.minecraft.world.level.block.state.BlockState feet = level.getBlockState(testPos);
                        net.minecraft.world.level.block.state.BlockState head = level.getBlockState(testPos.above());

                        // 绝对不卡方块：脚下必须是实体方块（能站立），脚和头所在位置必须没有碰撞体积（不会窒息）
                        if (ground.canOcclude() && !feet.canOcclude() && !head.canOcclude()) {
                            bestX = testPos.getX() + 0.5;
                            bestY = testPos.getY();
                            bestZ = testPos.getZ() + 0.5;
                            foundSafePos = true;
                            break;
                        }
                    }
                    if (foundSafePos) break;
                }

                // 托底机制
                if (!foundSafePos) {
                    bestX = player.getX();
                    bestY = player.getY();
                    bestZ = player.getZ();
                }

                // 【核心修复3】在读取完 NBT 之后，立即强制设定计算好的坐标！
                hero.moveTo(bestX, bestY, bestZ, level.random.nextFloat() * 360F, 0);
                hero.setDeltaMovement(0, 0, 0);
                hero.setFallDistance(0);

                // 绑定主人
                hero.setOwnerUUID(player.getUUID());
                hero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);

                level.addFreshEntity(hero);

                // 粒子和音效跟随最新的生成坐标
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, bestX, bestY + 1, bestZ, 30, 0.5, 0.5, 0.5, 0.1);
                level.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
            }
        }
    }
