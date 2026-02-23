package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SyncHeroVisitPacket;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroDimensionHandler {

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
        if (fromLevel != null) {
            for (var entity : fromLevel.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isAlive()) {
                    // [Fix] 确保是玩家的 Hero
                    if (hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                        carriedHeroData = new CompoundTag();
                        hero.saveWithoutId(carriedHeroData);
                        
                        // [Fix] 仅在信任度 > 0 时更新全局数据，防止意外覆盖
                        if (hero.getTrustLevel() > 0) {
                            HeroDataHandler.updateGlobalTrust(hero);
                        }
                        
                        hero.remove(Entity.RemovalReason.DISCARDED);
                        break;
                    }
                }
            }
        }

        boolean alreadyHasHero = false;
        for (var entity : endLevel.getAllEntities()) {
            if (entity instanceof HeroEntity hero && entity.isAlive()) {
                // [Fix] 确保是玩家的 Hero
                if (hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    alreadyHasHero = true;
                    break;
                }
            }
        }

        if (!alreadyHasHero) {
            HeroEntity hero = new HeroEntity(ModEvents.HERO.get(), endLevel);
            
            if (carriedHeroData != null) {
                if (carriedHeroData.contains("UUID")) carriedHeroData.remove("UUID");
                if (carriedHeroData.contains("UUIDMost")) carriedHeroData.remove("UUIDMost");
                if (carriedHeroData.contains("UUIDLeast")) carriedHeroData.remove("UUIDLeast");

                hero.load(carriedHeroData);
                
                if (carriedHeroData.contains("TrustLevel")) {
                    hero.setTrustLevel(carriedHeroData.getInt("TrustLevel"));
                }
                
                // [Fix] 尝试恢复信任度
                if (hero.getTrustLevel() == 0) {
                    HeroDataHandler.restoreTrustFromPlayer(hero);
                    // Fallback: 直接使用 player 对象查询数据
                    if (hero.getTrustLevel() == 0) {
                        HeroWorldData data = HeroWorldData.get(endLevel);
                        int trust = data.getTrust(player.getUUID());
                        if (trust > 0) hero.setTrustLevel(trust);
                    }
                }
                
                HeroDataHandler.syncGlobalTrust(hero);
                
            } else {
                hero.setTrustLevel(0);
                // Fallback: 即使没有携带数据，也尝试从玩家数据恢复
                HeroWorldData data = HeroWorldData.get(endLevel);
                int trust = data.getTrust(player.getUUID());
                if (trust > 0) hero.setTrustLevel(trust);
                
                HeroDataHandler.syncGlobalTrust(hero);
            }

            hero.setPos(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
            hero.addTag(EndRingContext.TAG_FIXED);
            hero.addTag(EndRingContext.TAG_INTRO); 
            hero.setUUID(UUID.randomUUID());
            
            endLevel.addFreshEntity(hero);
        }

        player.getPersistentData().putBoolean("HasVisitedHeroDimension", true);
        PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
    }

    private static void handleReturnToOverworld(ServerLevel fromLevel, ServerLevel toLevel, ServerPlayer player) {
        if (player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
            PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
        }

        CompoundTag carriedHeroData = null;

        // 1. 尝试在旧维度（End Ring）寻找跟随玩家的 Hero
        if (fromLevel != null) {
            for (var entity : fromLevel.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isAlive()) {
                    if (hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                        carriedHeroData = new CompoundTag();
                        hero.saveWithoutId(carriedHeroData);
                        
                        // [Fix] 仅在信任度 > 0 时更新全局数据
                        if (hero.getTrustLevel() > 0) {
                            HeroDataHandler.updateGlobalTrust(hero);
                        }
                        
                        hero.remove(Entity.RemovalReason.DISCARDED);
                        break;
                    }
                }
            }
        }

        // 2. 如果没找到，检查是否有挂起的重生数据
        CompoundTag playerData = player.getPersistentData();
        if (carriedHeroData == null && playerData.contains("HeroPendingRespawn") && playerData.contains("HeroRespawnData")) {
            carriedHeroData = playerData.getCompound("HeroRespawnData");
            playerData.remove("HeroPendingRespawn");
            playerData.remove("HeroRespawnData");
        }

        // 3. 生成 Hero
        if (carriedHeroData != null) {
            HeroEntity newHero = new HeroEntity(ModEvents.HERO.get(), toLevel);

            if (carriedHeroData.contains("UUID")) carriedHeroData.remove("UUID");
            if (carriedHeroData.contains("UUIDMost")) carriedHeroData.remove("UUIDMost");
            if (carriedHeroData.contains("UUIDLeast")) carriedHeroData.remove("UUIDLeast");
            
            newHero.load(carriedHeroData);
            
            if (carriedHeroData.contains("TrustLevel")) {
                newHero.setTrustLevel(carriedHeroData.getInt("TrustLevel"));
            }
            
            // [Fix] 尝试恢复信任度
            if (newHero.getTrustLevel() == 0) {
                HeroDataHandler.restoreTrustFromPlayer(newHero);
                
                // [Fix] Fallback: 直接使用 player 对象查询数据 (防止 restoreTrustFromPlayer 找不到玩家实体)
                if (newHero.getTrustLevel() == 0) {
                    HeroWorldData data = HeroWorldData.get(toLevel);
                    int trust = data.getTrust(player.getUUID());
                    if (trust > 0) {
                        newHero.setTrustLevel(trust);
                    }
                }
            }
            
            HeroDataHandler.syncGlobalTrust(newHero);

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
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            CompoundTag brainData = new CompoundTag();
            hero.getHeroBrain().save(brainData);
            data.setTempBrainData(brainData);
        }
        HeroDataHandler.updateGlobalTrust(hero);

        if (messageKey != null) {
            hero.level().getEntitiesOfClass(Player.class, hero.getBoundingBox().inflate(32.0D))
                    .forEach(p -> p.sendSystemMessage(Component.translatable(messageKey)));
        }

        if (hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, hero.getX(), hero.getY() + 1, hero.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            serverLevel.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
            
            int cooldownMinutes = 0; 
            HeroWorldData.get(serverLevel).setRespawnCooldown(serverLevel, cooldownMinutes);
        }
        hero.remove(Entity.RemovalReason.DISCARDED);
    }

    public static void teleportRandomly(HeroEntity hero) {
        for (int i = 0; i < 64; ++i) {
            double d0 = hero.getX() + (hero.getRandom().nextDouble() - 0.5D) * 16.0D;
            double d1 = hero.getY() + (double)(hero.getRandom().nextInt(16) - 8);
            double d2 = hero.getZ() + (hero.getRandom().nextDouble() - 0.5D) * 16.0D;
            if (hero.randomTeleport(d0, d1, d2, true)) {
                hero.level().playSound(null, hero.xo, hero.yo, hero.zo, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
                break;
            }
        }
    }

    public static void respawnNearPlayer(ServerLevel level, ServerPlayer player) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity && entity.isAlive()) {
                return; 
            }
        }

        HeroEntity hero = ModEvents.HERO.get().create(level);
        if (hero != null) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 4.0 + level.random.nextDouble() * 4.0;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getY();

            if (!level.getBlockState(new net.minecraft.core.BlockPos((int)x, (int)y, (int)z)).isAir()) {
                y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)x, (int)z);
            }

            hero.moveTo(x, y, z, level.random.nextFloat() * 360F, 0);
            
            HeroDataHandler.syncGlobalTrust(hero);
            
            HeroWorldData worldData = HeroWorldData.get(level);
            if (worldData.shouldUseHerobrineSkin()) {
                hero.setUseHerobrineSkin(true);
            } else {
                CompoundTag data = player.getPersistentData();
                if (data.contains("HeroCombatRespawnData")) {
                    CompoundTag heroData = data.getCompound("HeroCombatRespawnData");
                    if (heroData.contains("UseHerobrineSkin")) {
                        hero.setUseHerobrineSkin(heroData.getBoolean("UseHerobrineSkin"));
                    }
                    data.remove("HeroCombatRespawnData");
                }
            }

            if (worldData.getTempBrainData() != null) {
                hero.getHeroBrain().load(worldData.getTempBrainData());
            }
            hero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);

            level.addFreshEntity(hero);
            
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
        }
    }
}