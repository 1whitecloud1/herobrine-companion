package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class HeroPrankHandler {

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide || !hero.isCompanionMode()) return;
        if (hero.getHeroBrain().getState() != SimpleNeuralNetwork.MindState.PRANKSTER) return;

        // Very rare chance to prank (once every few minutes on average)
        if (hero.tickCount % 1200 != 0) return;
        if (hero.getRandom().nextFloat() > 0.1F) return;

        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID == null) return;

        Player owner = hero.level().getPlayerByUUID(ownerUUID);
        if (owner instanceof ServerPlayer serverPlayer) {
            executePrank(hero, serverPlayer);
        }
    }

    private static void executePrank(HeroEntity hero, ServerPlayer player) {
        int type = hero.getRandom().nextInt(3);

        switch (type) {
            case 0 -> {
                player.playNotifySound(SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
                HeroDialogueHandler.onPrank(hero, player);
                hero.getHeroBrain().inputEntropy(player.getUUID(), 0.05F);
            }
            case 1 -> {
                BlockPos pos = player.blockPosition();
                BlockPos torchPos = null;
                for (int x = -5; x <= 5 && torchPos == null; x++) {
                    for (int y = -2; y <= 2 && torchPos == null; y++) {
                        for (int z = -5; z <= 5; z++) {
                            BlockPos sample = pos.offset(x, y, z);
                            if (player.level().getBlockState(sample).is(Blocks.TORCH)
                                    || player.level().getBlockState(sample).is(Blocks.WALL_TORCH)) {
                                torchPos = sample;
                                break;
                            }
                        }
                    }
                }
                if (torchPos != null) {
                    player.level().destroyBlock(torchPos, true);
                    player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                    HeroDialogueHandler.onPrank(hero, player);
                    hero.getHeroBrain().inputEntropy(player.getUUID(), 0.05F);
                }
            }
            case 2 -> {
                player.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 0.5F, 0.5F);
                hero.getHeroBrain().inputEntropy(player.getUUID(), 0.05F);
            }
            default -> {
            }
        }
    }
}

