package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.HeroWorldData;
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

import java.util.List;
import java.util.UUID;

public class SourceFlowItem extends Item {

    public SourceFlowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = (ServerLevel) level;
            
            // 1. 获取全局活跃 Hero 的 UUID 和最后已知位置
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID heroUUID = data.getActiveHeroUUID();
            GlobalPos lastKnownPos = data.getLastKnownHeroPos();
            
            Entity targetEntity = null;

            // 2. 尝试查找实体 (如果已加载)
            if (heroUUID != null) {
                for (ServerLevel lvl : serverLevel.getServer().getAllLevels()) {
                    targetEntity = lvl.getEntity(heroUUID);
                    if (targetEntity != null) {
                        break;
                    }
                }
            }

            // 3. 传送逻辑
            if (targetEntity instanceof HeroEntity hero) {
                // A. 实体已加载：直接传送
                if (hero.level().dimension() != serverLevel.dimension()) {
                    ServerLevel targetLevel = serverLevel.getServer().getLevel(hero.level().dimension());
                    if (targetLevel != null) {
                        serverPlayer.teleportTo(targetLevel, hero.getX(), hero.getY(), hero.getZ(), hero.getYRot(), hero.getXRot());
                    }
                } else {
                    serverPlayer.teleportTo(hero.getX(), hero.getY(), hero.getZ());
                }
                playTeleportSound(level, player);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.source_flow_teleport").withStyle(ChatFormatting.AQUA));
                return InteractionResultHolder.success(stack);
                
            } else if (lastKnownPos != null) {
                // B. 实体未加载 (跨维度/远距离)：使用最后已知坐标
                ServerLevel targetLevel = serverLevel.getServer().getLevel(lastKnownPos.dimension());
                if (targetLevel != null) {
                    serverPlayer.teleportTo(targetLevel, lastKnownPos.pos().getX() + 0.5, lastKnownPos.pos().getY(), lastKnownPos.pos().getZ() + 0.5, serverPlayer.getYRot(), serverPlayer.getXRot());
                    playTeleportSound(level, player);
                    return InteractionResultHolder.success(stack);
                }
            }
            
            // C. 彻底找不到
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_found").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    private void playTeleportSound(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.source_flow.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
    
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.AQUA);
    }
}
