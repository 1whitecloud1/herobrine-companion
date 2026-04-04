package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
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
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
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
        CompoundTag carriedHeroData = null;

        // 🚀 优化 1：使用 ACTIVE_HEROES 替代 fromLevel.getAllEntities()
        if (fromLevel != null) {
            for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == fromLevel && hero.isAlive() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    // ============== [重构精简] ==============
                    HeroStateManager.backupToGlobal(hero);
                    carriedHeroData = new CompoundTag();
                    hero.saveWithoutId(carriedHeroData);
                    hero.remove(Entity.RemovalReason.DISCARDED);
                    // =====================================
                    break;
                }
            }
        }

            HeroEntity heroToUpdate = null;
            boolean isNewEntity = false;

        // 🚀 优化 2：使用 ACTIVE_HEROES 替代 endLevel.getAllEntities()
        for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (hero.level() == endLevel && hero.isAlive() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                heroToUpdate = hero;
                break;
            }
        }

            if (heroToUpdate == null) {
                heroToUpdate = new HeroEntity(ModEvents.HERO.get(), endLevel);
                heroToUpdate.setUUID(UUID.randomUUID());
                isNewEntity = true;
            }
        // =======================================================
        // 【核心修复】：必须先 load NBT 数据，再覆盖坐标和 Tag！
        // =======================================================
        if (carriedHeroData != null) {
            if (carriedHeroData.contains("UUID")) carriedHeroData.remove("UUID");
            if (carriedHeroData.contains("UUIDMost")) carriedHeroData.remove("UUIDMost");
            if (carriedHeroData.contains("UUIDLeast")) carriedHeroData.remove("UUIDLeast");

            // 清洗掉旧的坐标和动量，防止对后续设定造成干扰
            if (carriedHeroData.contains("Pos")) carriedHeroData.remove("Pos");
            if (carriedHeroData.contains("Motion")) carriedHeroData.remove("Motion");

            // 先恢复数据
            heroToUpdate.load(carriedHeroData);
        }

        // 然后再强行把实体按在 End Ring 中心！
        heroToUpdate.moveTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z, 0, 0);
        heroToUpdate.setDeltaMovement(0, 0, 0);
        heroToUpdate.setFallDistance(0);

        // 重新打上剧情专用的 Tag（防止被旧 NBT 洗掉）
        if (!heroToUpdate.getTags().contains(EndRingContext.TAG_FIXED)) heroToUpdate.addTag(EndRingContext.TAG_FIXED);
        if (!heroToUpdate.getTags().contains(EndRingContext.TAG_INTRO)) heroToUpdate.addTag(EndRingContext.TAG_INTRO);


        // ============== [重构精简] ==============
        heroToUpdate.setOwnerUUID(player.getUUID());
        HeroStateManager.restoreFromGlobal(heroToUpdate, player);
        HeroDataHandler.syncGlobalTrust(heroToUpdate);
        // =====================================

        if (isNewEntity) {
            endLevel.addFreshEntity(heroToUpdate);
        }

        player.getPersistentData().putBoolean("HasVisitedHeroDimension", true);
        PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
    }

        private static void handleReturnToOverworld(ServerLevel fromLevel, ServerLevel toLevel, ServerPlayer player) {
            if (player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
                PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
            }

            CompoundTag carriedHeroData = null;

            // 🚀 优化 3：使用 ACTIVE_HEROES 替代 fromLevel.getAllEntities()
            if (fromLevel != null) {
                for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                    if (hero.level() == fromLevel && hero.isAlive() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                        // ============== [重构精简] ==============
                        HeroStateManager.backupToGlobal(hero);
                        carriedHeroData = new CompoundTag();
                        hero.saveWithoutId(carriedHeroData);
                        hero.remove(Entity.RemovalReason.DISCARDED);
                        // =====================================
                        break;
                    }
                }
            }

            CompoundTag playerData = player.getPersistentData();
            if (carriedHeroData == null && playerData.contains("HeroPendingRespawn") && playerData.contains("HeroRespawnData")) {
                carriedHeroData = playerData.getCompound("HeroRespawnData");
                playerData.remove("HeroPendingRespawn");
                playerData.remove("HeroRespawnData");
            }

            if (carriedHeroData != null) {
                HeroEntity newHero = new HeroEntity(ModEvents.HERO.get(), toLevel);

                if (carriedHeroData.contains("UUID")) carriedHeroData.remove("UUID");
                if (carriedHeroData.contains("UUIDMost")) carriedHeroData.remove("UUIDMost");
                if (carriedHeroData.contains("UUIDLeast")) carriedHeroData.remove("UUIDLeast");

                newHero.load(carriedHeroData);

                // ============== [重构精简] ==============
                newHero.setOwnerUUID(player.getUUID());
                HeroStateManager.restoreFromGlobal(newHero, player);
                HeroDataHandler.syncGlobalTrust(newHero);
                // =====================================

                double radius = 3.0D;
                double angle = toLevel.random.nextDouble() * Math.PI * 2.0D;
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;

                double spawnX = player.getX() + offsetX;
                double spawnZ = player.getZ() + offsetZ;
                double spawnY = player.getY();

                float yaw = (float) (Math.atan2(player.getZ() - spawnZ, player.getX() - spawnX) * (180.0D / Math.PI)) - 90.0F;
                newHero.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);

                newHero.setUUID(UUID.randomUUID());
                newHero.removeTag(EndRingContext.TAG_INTRO);
                newHero.removeTag(EndRingContext.TAG_TELEPORTING);
                newHero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
                newHero.setCustomName(Component.translatable("entity.herobrine_companion.hero"));
                newHero.setNoGravity(false);

                toLevel.addFreshEntity(newHero);
            }
        }

        public static void handleVoidProtection(HeroEntity hero) {
            if (hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
                if (hero.getTags().contains(EndRingContext.TAG_FIXED) || hero.getY() < 0) {
                    hero.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                    hero.setDeltaMovement(0, 0, 0);
                    hero.setFallDistance(0);
                }
            }
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

            HeroEntity hero = ModEvents.HERO.get().create(level);
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