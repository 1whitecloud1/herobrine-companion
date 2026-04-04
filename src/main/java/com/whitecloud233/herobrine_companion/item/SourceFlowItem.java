package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class SourceFlowItem extends Item {
    public SourceFlowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 物品右键逻辑：交由公共方法执行
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            boolean success = performTeleportToHero(serverPlayer);
            if (success) {
                return InteractionResultHolder.success(stack);
            } else {
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * 【核心提取】：将寻找并传送到 Hero 身边的逻辑提取为公共方法
     */
    public static boolean performTeleportToHero(ServerPlayer serverPlayer) {
        ServerLevel serverLevel = serverPlayer.serverLevel();
        UUID playerUUID = serverPlayer.getUUID();
        HeroWorldData data = HeroWorldData.get(serverLevel);
        UUID activeHeroId = data.getActiveHeroUUID(playerUUID);

        if (activeHeroId != null) {
            for (ServerLevel lvl : serverLevel.getServer().getAllLevels()) {
                Entity entity = lvl.getEntity(activeHeroId);
                if (entity instanceof HeroEntity hero && hero.isAlive()) {
                    serverPlayer.teleportTo(lvl, entity.getX(), entity.getY(), entity.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
                    lvl.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    return true;
                }
            }
        }

        GlobalPos lastKnownPos = data.getLastKnownHeroPos(playerUUID);
        if (lastKnownPos != null) {
            ServerLevel targetLevel = serverLevel.getServer().getLevel(lastKnownPos.dimension());
            if (targetLevel != null) {
                serverPlayer.teleportTo(targetLevel, lastKnownPos.pos().getX() + 0.5, lastKnownPos.pos().getY(), lastKnownPos.pos().getZ() + 0.5, serverPlayer.getYRot(), serverPlayer.getXRot());
                targetLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }

        serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_found").withStyle(ChatFormatting.RED));
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.source_flow.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}