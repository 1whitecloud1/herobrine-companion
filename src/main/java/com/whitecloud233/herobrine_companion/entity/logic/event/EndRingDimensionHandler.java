package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.fight.ChallengeSession;
import com.whitecloud233.herobrine_companion.init.ModItems;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.structure.EndRingRestorer;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class EndRingDimensionHandler {

    // 记录本会话是否已经重建过 End Ring 竞技场，避免每次进入都全量重建
    private static final Set<ResourceKey<Level>> ARENA_RESTORED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> DISCONNECT_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) initializeVisit(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) initializeVisit(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DISCONNECT_TICKS.remove(event.getEntity().getUUID());
        event.getEntity().getPersistentData().remove("SkyGazeTicks");
    }

    private static void initializeVisit(ServerPlayer player) {
        DISCONNECT_TICKS.remove(player.getUUID());
        if (player.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            CompoundTag data = player.getPersistentData();
            if (ChallengeSession.isPlayerInChallenge(data)) return;
            // 兜底：无论结构生成是否被整合包改动/禁用，首次进入时程序化重建竞技场圆环
            if (ARENA_RESTORED.add(ModStructures.END_RING_DIMENSION_KEY)) {
                ServerLevel endRingLevel = player.server.getLevel(ModStructures.END_RING_DIMENSION_KEY);
                if (endRingLevel != null) {
                    EndRingRestorer.restoreArena(endRingLevel);
                }
            }

            // 登录事件发生在玩家 NBT 和连接恢复后；不要在 EntityJoinLevelEvent 里提前保存玩家。
            boolean pendingReturn = EndRingStoryState.resume(data, player.level().getGameTime());
            if (pendingReturn || player.getY() < 103) {
                teleportToRing(player);
            }
        } else {
            player.getPersistentData().remove("SkyGazeTicks");
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;

        if (player.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (!ChallengeSession.isPlayerInChallenge(player)) {
                handleEndRingGameplay(player);
            }
        }
    }

    private static void handleEndRingGameplay(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        Integer disconnectTicks = DISCONNECT_TICKS.get(player.getUUID());
        if (disconnectTicks != null) {
            // TickTask 的构造参数不是延时调度；用真正的玩家 tick 倒计时，并隔离返回主世界判定。
            if (player.getY() < -50) teleportToRing(player);
            if (disconnectTicks <= 1) {
                DISCONNECT_TICKS.remove(player.getUUID());
                teleportToRing(player);
                player.server.getPlayerList().saveAll();
                player.connection.disconnect(Component.translatable("message.herobrine_companion.system_server_closed"));
            } else {
                DISCONNECT_TICKS.put(player.getUUID(), disconnectTicks - 1);
            }
            return;
        }
        long timeEntered = data.getLong("EnteredEndRingTime");

        if (player.level().getGameTime() - timeEntered > 600 && data.getInt("WakeUpStage") == 0) {
            data.putInt("WakeUpStage", 1);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_strange_presence"));
        }

        if (player.getY() < -50) {
            handleVoidFall(player, data);
            return;
        }
        if (EndRingStoryState.tickSkyGaze(data, player.getXRot())) executeReturnToOverworld(player, data);
    }

    private static void handleVoidFall(ServerPlayer player, CompoundTag data) {
        int stage = data.getInt("WakeUpStage");
        teleportToRing(player);

        if (EndRingStoryState.beginVoidReturn(data)) {
            DISCONNECT_TICKS.put(player.getUUID(), 60);
            player.server.getPlayerList().saveAll();

            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_server_closed"));
        } else if (stage < 3 && player.tickCount >= 100) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready2"));
        }
    }

    private static void executeReturnToOverworld(ServerPlayer player, CompoundTag data) {
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            BlockPos spawnPos = player.getRespawnDimension() == Level.OVERWORLD && player.getRespawnPosition() != null
                    ? player.getRespawnPosition() : overworld.getSharedSpawnPos();

            // HeroDimensionHandler 是唯一的实体迁移入口，保留原实体直到新实体成功加入。
            player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_wake_up"));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.CONFUSION, 200, 0));

            if (!data.getBoolean("HasReceivedFragment4")) {
                ItemStack fragment = new ItemStack(ModItems.LORE_FRAGMENT.get());
                CompoundTag tag = new CompoundTag();
                tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_4");
                fragment.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));

                if (!player.getInventory().add(fragment)) player.drop(fragment, false);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.chat_hero",
                        Component.translatable("message.herobrine_companion.fragment_4_received")).withStyle(ChatFormatting.YELLOW));
                data.putBoolean("HasReceivedFragment4", true);
            }
        }
    }

    private static void teleportToRing(ServerPlayer player) {
        player.teleportTo(player.serverLevel(), EndRingContext.SAFE_X, EndRingContext.SAFE_Y,
                EndRingContext.SAFE_Z, player.getYRot(), 0);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        if (player.level() instanceof ServerLevel serverLevel) {
            for (HeroEntity hero : HeroLifecycleHandler.getLoadedHeroes(serverLevel.getServer(), player.getUUID())) {
                // 确保在同一维度，并且是当前玩家的主人
                if (hero.level() == serverLevel && hero.isAlive()
                        && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {

                    hero.removeTag(EndRingContext.TAG_INTRO);
                    com.whitecloud233.herobrine_companion.entity.logic.data.HeroDimensionHandler.handleVoidProtection(hero);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 服务器关闭时清空会话标记，避免跨存档残留
        ARENA_RESTORED.clear();
        DISCONNECT_TICKS.clear();
    }
}
