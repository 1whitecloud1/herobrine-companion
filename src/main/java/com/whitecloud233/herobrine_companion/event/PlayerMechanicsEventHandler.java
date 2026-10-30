package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.herobrine_companion.entity.logic.spawn.GlitchVillagerSpawner;
import com.whitecloud233.herobrine_companion.entity.logic.spawn.HeroSpawner;
import com.whitecloud233.herobrine_companion.world.structure.UnstableZoneRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

// 1.21.1: 默认的 Bus 就是 GAME（替代旧版 FORGE），直接省略 bus 属性
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class PlayerMechanicsEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerMechanicsEventHandler");

    /** 深渊凝视夜视因第三方监听器异常而无法续期时，只告警一次，避免刷屏。 */
    private static final AtomicBoolean ABYSSAL_GAZE_VISION_WARNED = new AtomicBoolean();

    private static final HeroSpawner spawner = new HeroSpawner();
    private static final GlitchVillagerSpawner glitchVillagerSpawner = new GlitchVillagerSpawner();

    // 1.21.1: TickEvent 拆分为了 Pre 和 Post 类
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            spawner.tick(serverLevel);
            glitchVillagerSpawner.tick(serverLevel);
            UnstableZoneRuntime.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            if (player.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                // 深渊凝视：持续夜视。只在效果剩余 ≤10秒(200 tick)时续期，避免每 tick 都 addEffect
                // ——每 tick 都会触发一次效果变更事件（事件风暴，且第三方监听器可能在此崩溃）。
                MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
                if (current == null || current.getDuration() <= 200) {
                    refreshAbyssalGazeVision(player);
                }
            }

            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }

                if (player.getPersistentData().getBoolean("herobrine_companion.should_restore_flying")) {
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();

                    if (player.tickCount > 20) {
                        player.getPersistentData().remove("herobrine_companion.should_restore_flying");
                    }
                }
            }

            if (player instanceof ServerPlayer serverPlayer) {
                HeroQuestManager.tickAll(serverPlayer);
            }
        }
    }

    /** 续期深渊凝视的夜视效果。防御第三方监听器异常，避免让整条玩家 tick 崩溃。 */
    private static void refreshAbyssalGazeVision(Player player) {
        try {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, false));
        } catch (RuntimeException exception) {
            if (ABYSSAL_GAZE_VISION_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("Abyssal Gaze night vision could not be refreshed because an effect listener threw: {}", exception.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 委托桥：若目标交互被当前委托消费（交付物品 / 驱散镜像），取消原交互
            boolean consumed = HeroQuestManager.onEntityInteract(player, event.getTarget(), event.getItemStack());
            if (consumed) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                // 1.21.1: save 方法只接收 Registry 上下文，并直接返回序列化好的 ListTag
                // 1.21.1: 传入空的 ListTag 获取序列化数据
                ListTag inventoryTag = player.getInventory().save(new ListTag());

                // 【核心修复】：这就是之前我不小心删掉的那一行！
                CompoundTag data = player.getPersistentData();
                data.put("SoulBoundInventory", inventoryTag);
                data.putFloat("SoulBoundXP", player.experienceProgress);
                data.putInt("SoulBoundLevel", player.experienceLevel);
                data.putInt("SoulBoundTotalXP", player.totalExperience);

                player.getInventory().clearContent();
            }

            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                player.getPersistentData().putBoolean("herobrine_companion.transcendence_permit_flying", player.getAbilities().flying);
            }
        }

        if (event.getEntity() instanceof ServerPlayer diedPlayer && !diedPlayer.level().isClientSide) {
            // 委托桥：处理“被世界选中却没能接住”等死亡失败分支
            HeroQuestManager.onPlayerDeath(diedPlayer);
        }

        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            HeroQuestManager.onKill(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true);
            }
        }
    }
}