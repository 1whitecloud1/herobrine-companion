package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncHeroVisitPacket;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

    // --- Event Handling ---

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel toLevel = (ServerLevel) player.level();
        ServerLevel fromLevel = player.server.getLevel(event.getFrom());

        // A. 进入 End Ring
        if (event.getTo() == ModStructures.END_RING_DIMENSION_KEY) {
            handleEnterEndRing(fromLevel, toLevel, player);
        }

        // B. 从 End Ring 回归
        if (event.getFrom() == ModStructures.END_RING_DIMENSION_KEY && event.getTo() == Level.OVERWORLD) {
            handleReturnToOverworld(toLevel, player);
        }
    }

    // --- Dimension Transition Logic ---

    private static void handleEnterEndRing(ServerLevel fromLevel, ServerLevel endLevel, ServerPlayer player) {
        // 1. 尝试在旧维度（如主世界）寻找跟随玩家的 Hero
        CompoundTag carriedHeroData = null;
        if (fromLevel != null) {
            for (var entity : fromLevel.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isAlive()) {
                    // [Fix] 确保是玩家的 Hero
                    if (hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                        carriedHeroData = new CompoundTag();
                        hero.saveWithoutId(carriedHeroData);
                        HeroDataHandler.updateGlobalTrust(hero); // 确保数据保存
                        hero.discard();
                        break;
                    }
                }
            }
        }

        // 2. 在新维度生成 Hero
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
                // 清洗 UUID
                if (carriedHeroData.contains("UUID")) carriedHeroData.remove("UUID");
                if (carriedHeroData.contains("UUIDMost")) carriedHeroData.remove("UUIDMost");
                if (carriedHeroData.contains("UUIDLeast")) carriedHeroData.remove("UUIDLeast");

                hero.load(carriedHeroData);

                // 恢复信任度 (优先从 NBT 读，然后同步全局)
                if (carriedHeroData.contains("TrustLevel")) {
                    hero.setTrustLevel(carriedHeroData.getInt("TrustLevel"));
                }
                HeroDataHandler.syncGlobalTrust(hero);

            } else {
                // [Fix] 绑定主人，防止信任度丢失
                hero.setOwnerUUID(player.getUUID());
                hero.setTrustLevel(0);
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

    private static void handleReturnToOverworld(ServerLevel level, ServerPlayer player) {
        if (player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
            PacketHandler.sendToPlayer(new SyncHeroVisitPacket(true), player);
        }

        CompoundTag playerData = player.getPersistentData();
        if (playerData.contains("HeroPendingRespawn") && playerData.contains("HeroRespawnData")) {
            CompoundTag heroData = playerData.getCompound("HeroRespawnData");
            HeroEntity newHero = new HeroEntity(ModEvents.HERO.get(), level);

            if (heroData.contains("UUID")) heroData.remove("UUID");
            if (heroData.contains("UUIDMost")) heroData.remove("UUIDMost");
            if (heroData.contains("UUIDLeast")) heroData.remove("UUIDLeast");
            
            newHero.load(heroData);
            
            if (heroData.contains("TrustLevel")) {
                newHero.setTrustLevel(heroData.getInt("TrustLevel"));
            }
            HeroDataHandler.syncGlobalTrust(newHero);

            // 随机环绕生成
            double radius = 3.0D;
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
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

            level.addFreshEntity(newHero);

            playerData.remove("HeroPendingRespawn");
            playerData.remove("HeroRespawnData");
        }
    }

    // --- Entity Logic (Called by HeroEntity/HeroLogic) ---

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
        // [新增] 离开前保存大脑记忆
        // 我们需要把当前实体的数据（包括大脑）保存下来，以便重生时恢复
        // 但由于 leaveWorld 通常是 discard，我们只能依赖 HeroWorldData 或者临时保存到玩家身上
        // 这里简化处理：如果是被攻击离开，我们假设他只是暂时躲起来，重生时会继承全局数据
        // 但大脑数据是存储在实体 NBT 里的，如果 discard 了，新生成的实体就是新的大脑
        
        // 解决方案：将大脑数据序列化并保存到 HeroWorldData (如果支持) 或者 玩家 NBT
        // 目前 HeroWorldData 只存了 TrustLevel 和 SkinVariant
        // 为了简单起见，我们让他在重生时保留记忆，这需要修改 respawnNearPlayer 方法
        
        // 暂时先保存到 hero 自身，如果 respawnNearPlayer 能获取到旧数据最好
        // 但目前的逻辑是 create 新实体。
        
        // 既然是“失望离开”，那他重生时应该带着这份“失望” (记忆)
        // 我们可以把大脑数据临时存到 HeroWorldData 的一个临时字段里
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            CompoundTag brainData = new CompoundTag();
            hero.getHeroBrain().save(brainData);
            data.setTempBrainData(brainData); // 需要在 HeroWorldData 中添加此方法
        }

        HeroDataHandler.updateGlobalTrust(hero);

        if (messageKey != null) {
            hero.level().getEntitiesOfClass(Player.class, hero.getBoundingBox().inflate(32.0D))
                    .forEach(p -> p.sendSystemMessage(Component.translatable(messageKey)));
        }

        if (hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, hero.getX(), hero.getY() + 1, hero.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            serverLevel.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
        }
        hero.discard();
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
    // [新增] 在玩家附近生成 Hero
    public static void respawnNearPlayer(ServerLevel level, ServerPlayer player) {
        // 检查是否已经存在 Hero
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity && entity.isAlive()) {
                return; // 已经存在，不重复生成
            }
        }

        HeroEntity hero = ModEvents.HERO.get().create(level);
        if (hero != null) {
            // 随机位置
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 4.0 + level.random.nextDouble() * 4.0;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getY();

            // 简单的位置调整，防止卡墙
            if (!level.getBlockState(new net.minecraft.core.BlockPos((int)x, (int)y, (int)z)).isAir()) {
                y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)x, (int)z);
            }

            hero.moveTo(x, y, z, level.random.nextFloat() * 360F, 0);

            // 同步信任度
            HeroDataHandler.syncGlobalTrust(hero);
            
            // [修复] 强制同步皮肤状态
            HeroWorldData data = HeroWorldData.get(level);
            hero.setSkinVariant(data.getGlobalSkinVariant());
            
            // [新增] 尝试恢复大脑记忆
            if (data.getTempBrainData() != null) {
                hero.getHeroBrain().load(data.getTempBrainData());
                // 恢复后清除临时数据，或者保留以防再次消失？保留比较安全
            }

            // 添加防重复生成的标签 (可选)
            hero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
            
            // [新增] 恢复调试标签 (如果之前有)
            // 由于我们无法知道之前的实体是否有标签，这里只能让玩家重新打标签
            // 或者我们可以把 brain_debug 状态也存进 HeroWorldData，但这有点过度设计了
            // 简单点：如果玩家是创造模式，或者有特定权限，自动开启？
            // 不，还是让玩家重新输入指令吧，或者在聊天栏提示一下。

            level.addFreshEntity(hero);

            // 特效
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
        }
    }
}
