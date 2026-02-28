package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.logic.HeroQuestHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroSpawner;
import com.whitecloud233.herobrine_companion.entity.logic.GlitchVillagerSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;

// 1.21.1 修复：移除 bus 参数，默认为 GAME
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CommonEvents {

    // 创建一个静态的生成器实例
    private static final HeroSpawner spawner = new HeroSpawner();
    private static final GlitchVillagerSpawner glitchVillagerSpawner = new GlitchVillagerSpawner();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // 只在服务端运行，且只针对 ServerLevel
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 调用你的生成器逻辑
            spawner.tick(serverLevel);
            glitchVillagerSpawner.tick(serverLevel);
        }
    }

    // [新增] 监听生物死亡事件，用于更新任务进度
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        Entity source = event.getSource().getEntity();

        // 检查击杀者是否为玩家
        if (source instanceof ServerPlayer player) {
            HeroQuestHandler.onMobKill(player, entity);
        }

        // Check if the dead entity was a quest target
        Set<String> tags = entity.getTags();
        for (String tag : tags) {
            if (tag.startsWith("quest_target_for:")) {
                String playerUUIDStr = tag.substring("quest_target_for:".length());
                try {
                    UUID playerUUID = UUID.fromString(playerUUIDStr);
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
                        if (player != null) {
                            HeroQuestHandler.failQuest(player, HeroQuestHandler.QUEST_PACIFY_ENDERMAN, "message.herobrine_companion.quest_target_died");
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore invalid UUIDs
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HeroQuestHandler.onEndermanInteract(player, event.getTarget(), event.getItemStack());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HeroQuestHandler.tickPacifyQuest(player);
        }
    }

    private static void checkAndTeleportHero(ServerPlayer player) {
        // 遍历全局活跃的 Hero 列表
        for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
            // 找到属于该玩家且处于陪伴模式的 Hero
            if (hero.isCompanionMode() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {

                hero.teleportTo(player.getX(), player.getY(), player.getZ());
                // 找到一个就退出，避免多重处理
                return;
            }
        }
    }
}