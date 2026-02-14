package com.whitecloud233.modid.herobrine_companion.item;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class RecallStoneItem extends Item {

    public RecallStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            Optional<GlobalPos> lastDeathPos = serverPlayer.getLastDeathLocation();

            if (lastDeathPos.isPresent()) {
                GlobalPos target = lastDeathPos.get();
                ServerLevel targetLevel = serverPlayer.server.getLevel(target.dimension());

                if (targetLevel != null) {
                    // Teleport logic
                    serverPlayer.teleportTo(targetLevel, target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5, serverPlayer.getYRot(), serverPlayer.getXRot());
                    
                    // Play sound
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    targetLevel.playSound(null, target.pos().getX(), target.pos().getY(), target.pos().getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                    // Consume durability
                    stack.hurtAndBreak(1, serverPlayer, (p) -> p.broadcastBreakEvent(hand));
                    
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.recall_success"));
                    
                    return InteractionResultHolder.success(stack);
                }
            } else {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.no_death_point"));
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.recall_stone.desc"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}
