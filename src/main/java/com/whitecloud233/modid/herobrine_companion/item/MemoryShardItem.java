package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.entity.GlitchEchoEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MemoryShardItem extends Item {
    public MemoryShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.JUKEBOX)) {
            if (!level.isClientSide) {
                // Play glitch sound
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 2.0f);
                level.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.BLOCKS, 0.5f, 2.0f);

                // Spawn Glitch Echo
                GlitchEchoEntity echo = new GlitchEchoEntity(ModEvents.GLITCH_ECHO.get(), level);
                echo.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
                level.addFreshEntity(echo);

                // Do NOT consume the item (it's rejected)
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.herobrine_companion.memory_shard.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
