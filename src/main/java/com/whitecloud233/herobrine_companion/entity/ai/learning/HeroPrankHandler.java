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
        
        // Very rare chance to prank (once every few minutes on average)
        if (hero.tickCount % 1200 != 0) return; // Check every minute
        if (hero.getRandom().nextFloat() > 0.1) return; // 10% chance per minute

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
            case 0: // Fake Creeper Sound
                player.playNotifySound(SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
                HeroDialogueHandler.onPrank(hero, player);
                break;
            case 1: // Random Torch Break (Visual only? No, let's just break one nearby torch)
                BlockPos pos = player.blockPosition();
                BlockPos torchPos = null;
                for (int x = -5; x <= 5; x++) {
                    for (int y = -2; y <= 2; y++) {
                        for (int z = -5; z <= 5; z++) {
                            BlockPos p = pos.offset(x, y, z);
                            if (player.level().getBlockState(p).is(Blocks.TORCH) || player.level().getBlockState(p).is(Blocks.WALL_TORCH)) {
                                torchPos = p;
                                break;
                            }
                        }
                    }
                }
                if (torchPos != null) {
                    player.level().destroyBlock(torchPos, true);
                    player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
                    HeroDialogueHandler.onPrank(hero, player);
                }
                break;
            case 2: // Jumpscare Teleport (Teleport right in front of player for a split second)
                // This is handled by the TeleportToPlayerGoal mostly, but we can force a sound here
                player.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 0.5f, 0.5f);
                break;
        }
    }
}
