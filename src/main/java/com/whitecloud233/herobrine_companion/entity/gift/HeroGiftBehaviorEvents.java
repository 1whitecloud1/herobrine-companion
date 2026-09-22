package com.whitecloud233.herobrine_companion.entity.gift;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 赠礼行为上下文事件订阅 —— 把世界事件落成 {@link HeroGiftBehaviorContext} 计数。
 *
 * <p>单一职责:只做"事件 → 计数"的簿记;不决策、不发言、不读写赠礼档案。
 * 对应 Bedrock on_mob_die / on_player_attack_entity / on_entity_place_block_after。
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HeroGiftBehaviorEvents {

    private HeroGiftBehaviorEvents() {
    }

    /** 推进"吃完蛋糕后延迟挂物品冷却覆盖层"的队列(见 HeroBirthdayCakeService)。 */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            HeroBirthdayCakeService.tickPendingCooldownOverlay(serverPlayer);
        }
    }
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && event.getEntity() instanceof Monster) {
            int tick = player.level().getGameTime() > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : (int) player.level().getGameTime();
            HeroGiftBehaviorContext context = HeroGiftBehaviorContext.of(player.getUUID());
            context.recordKill(tick);
            if (isVillageProtector(event.getEntity())) {
                context.recordVillageHarm(tick);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && isVillageProtector(event.getTarget())) {
            int tick = currentTick(player);
            HeroGiftBehaviorContext context = HeroGiftBehaviorContext.of(player.getUUID());
            // 20 tick 去抖(Bedrock lastVillageHarmTick 语义)
            if (!context.villageHarmRecently(tick)) {
                context.recordVillageHarm(tick);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
            if (key != null && HeroGiftCatalog.isRepairItemName(key.toString())) {
                HeroGiftBehaviorContext.of(player.getUUID()).recordCare(currentTick(player));
            }
        }
    }

    private static boolean isVillageProtector(Entity entity) {
        return entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || entity instanceof WanderingTrader;
    }

    private static int currentTick(ServerPlayer player) {
        long t = player.level().getGameTime();
        return t > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) t;
    }
}