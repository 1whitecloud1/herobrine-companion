package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.entity.awakened.containment.AwakenedMobReleaseService;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.containment.AwakenedVesselActionGuard;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.containment.AwakenedVesselFeedback;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.containment.AwakenedVesselStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AwakenedVesselItem extends Item {
    public AwakenedVesselItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!AwakenedVesselStorage.hasCapturedMob(stack)) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (AwakenedVesselActionGuard.blocksImmediateFollowUp(player, stack, level)) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        AwakenedMobReleaseService.ReleaseResult result = AwakenedMobReleaseService.releaseFromBlock(
                serverPlayer,
                stack,
                serverLevel,
                context.getClickedPos(),
                context.getClickedFace()
        );
        if (result.success()) {
            AwakenedVesselFeedback.releaseSuccess(serverPlayer, serverLevel, context.getClickLocation(), result);
            return InteractionResult.SUCCESS;
        }

        AwakenedVesselFeedback.releaseFailure(serverPlayer, result.failure());
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!AwakenedVesselStorage.hasCapturedMob(stack)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (AwakenedVesselActionGuard.blocksImmediateFollowUp(player, stack, level)) {
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        AwakenedMobReleaseService.ReleaseResult result = AwakenedMobReleaseService.releaseFromAir(serverPlayer, stack, serverLevel);
        if (result.success()) {
            AwakenedVesselFeedback.releaseSuccess(serverPlayer, serverLevel, player.position(), result);
            return InteractionResultHolder.success(stack);
        }

        AwakenedVesselFeedback.releaseFailure(serverPlayer, result.failure());
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return AwakenedVesselStorage.hasCapturedMob(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.awakened_vessel.desc").withStyle(ChatFormatting.GRAY));
        if (AwakenedVesselStorage.hasCapturedMob(stack)) {
            tooltipComponents.add(Component.translatable(
                    "item.herobrine_companion.awakened_vessel.contains",
                    AwakenedVesselStorage.capturedName(stack)
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltipComponents.add(Component.translatable("item.herobrine_companion.awakened_vessel.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}
