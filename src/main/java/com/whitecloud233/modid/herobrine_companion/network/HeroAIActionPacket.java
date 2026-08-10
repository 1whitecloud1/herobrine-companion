package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * AI 动作包：只保留"世界湮灭"类动作（实体/区块清除，玩家施法范畴，仍走命令层）。
 *
 * <p>英雄自身行为（升空 / 落地 / 接受挑战 / 陪伴切换）已迁移为 agent 工具
 * （见 {@code entity.ai.agent.tool} 的 {@code HeroAscendTool} 等），不再经本包分发。</p>
 */
public class HeroAIActionPacket {
    public static final String ACTION_DISCARD_ENTITIES = "action:discard_entities";
    public static final String ACTION_DISCARD = "action:discard";
    private static final double DISCARD_ENTITY_RADIUS = 32.0D;
    private static final int DISCARD_CHUNK_RADIUS = 10;

    private final String action;

    public HeroAIActionPacket(String action) {
        this.action = action == null ? "" : action;
    }

    public HeroAIActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.action, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                performAction(player, this.action);
            }
        });
    }

    public static boolean isSupportedAction(String action) {
        String normalizedAction = normalizeAction(action);
        return ACTION_DISCARD_ENTITIES.equals(normalizedAction)
                || ACTION_DISCARD.equals(normalizedAction);
    }

    public static boolean performAction(ServerPlayer player, String action) {
        String normalizedAction = normalizeAction(action);
        if (player == null || player.getServer() == null || !isSupportedAction(normalizedAction)) {
            return false;
        }

        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            return false;
        }

        return switch (normalizedAction) {
            case ACTION_DISCARD_ENTITIES -> discardNearbyEntities(player, hero);
            case ACTION_DISCARD -> discardChunksAroundPlayer(player, hero);
            default -> false;
        };
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private static boolean discardNearbyEntities(ServerPlayer player, HeroEntity hero) {
        AABB bounds = new AABB(player.blockPosition()).inflate(DISCARD_ENTITY_RADIUS, 12.0D, DISCARD_ENTITY_RADIUS);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_start"));

        int discardedEntities = 0;
        for (Entity entity : player.serverLevel().getEntities(null, bounds)) {
            if (entity == null || entity.isRemoved() || entity == player || entity == hero
                    || entity instanceof ServerPlayer || entity instanceof HeroEntity) {
                continue;
            }
            entity.discard();
            discardedEntities++;
        }

        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_complete", discardedEntities));
        return discardedEntities > 0;
    }

    private static boolean discardChunksAroundPlayer(ServerPlayer player, HeroEntity hero) {
        hero.getNavigation().stop();
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_escalated"));
        return ClearAreaPacket.startVoidDomain(player, DISCARD_CHUNK_RADIUS, false, false,
                Component.translatable("message.herobrine_companion.hero_ai.discard_reality"));
    }
}
