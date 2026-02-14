package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EternalKeyItem extends Item {

    public EternalKeyItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.eternal_key.desc_1")
                .withStyle(ChatFormatting.GRAY));
        
        tooltipComponents.add(Component.translatable("item.herobrine_companion.eternal_key.desc_2")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (state.getBlock() != Blocks.BEDROCK) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.herobrine_companion.key_invalid_block"), true);
            }
            return InteractionResult.PASS;
        }

        if (level.dimension() != Level.END) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.herobrine_companion.system_key_silent"), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            level.setBlock(pos, HerobrineCompanion.END_RING_PORTAL.get().defaultBlockState(), 3);
            
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(pos.getX(), pos.getY(), pos.getZ());
                lightning.setVisualOnly(true); 
                serverLevel.addFreshEntity(lightning);
            }
            
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0f, 2.0f);
            
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.herobrine_companion.system_reality_fractures"), true);
                if (!player.isCreative()) {
                    context.getItemInHand().shrink(1);
                }
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
