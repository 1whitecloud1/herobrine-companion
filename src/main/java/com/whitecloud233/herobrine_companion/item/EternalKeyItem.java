package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
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
        super(properties.stacksTo(1).fireResistant()); // 钥匙通常不可堆叠且防火
    }

    // 添加悬浮提示 (Tooltip) —— 这是最重要的引导部分
    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        // 谜语 1: 暗示地点 (The End)
        tooltipComponents.add(Component.translatable("item.herobrine_companion.eternal_key.desc_1")
                .withStyle(ChatFormatting.GRAY));
        
        // 谜语 2: 暗示方块 (Bedrock)
        tooltipComponents.add(Component.translatable("item.herobrine_companion.eternal_key.desc_2")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // ... (保持你原本的逻辑不变) ...
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        // 1. 检查是不是基岩
        if (!state.is(Blocks.BEDROCK)) {
            if (!level.isClientSide && player != null) {
                // 如果玩家试图点其他方块，提示错误
                player.displayClientMessage(Component.translatable("message.herobrine_companion.key_invalid_block"), true);
            }
            return InteractionResult.PASS;
        }

        // 2. 检查维度 (必须是末地)
        if (level.dimension() != Level.END) {
            if (!level.isClientSide && player != null) {
                // 提示：维度错误
                player.displayClientMessage(Component.translatable("message.herobrine_companion.system_key_silent"), true);
            }
            return InteractionResult.FAIL;
        }

        // 3. 执行开启
        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            // 替换方块为传送门
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