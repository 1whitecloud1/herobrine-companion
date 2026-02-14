package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.GlitchVillagerEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class GlitchVillagerSpawner {

    public void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        // Check every 200 ticks (10 seconds)
        if (level.getGameTime() % 200 != 0) return;

        // Check if a Glitch Villager already exists
        boolean exists = false;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GlitchVillagerEntity && entity.isAlive()) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) return;

            RandomSource random = level.getRandom();
            // Small chance to spawn (e.g., 10% every 10 seconds if conditions met)
            if (random.nextFloat() < 0.1F) {
                for (ServerPlayer player : players) {
                    attemptSpawn(level, player, random);
                }
            }
        }
    }

    private void attemptSpawn(ServerLevel level, ServerPlayer player, RandomSource random) {
        // Find nearby villagers
        AABB searchBox = player.getBoundingBox().inflate(64);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, searchBox);

        if (!villagers.isEmpty()) {
            // Pick a random villager to spawn near, or replace?
            // The prompt says "encounter a villager named N. in a village".
            // So we should spawn it near existing villagers to simulate it being part of a village.
            Villager targetVillager = villagers.get(random.nextInt(villagers.size()));
            BlockPos spawnPos = targetVillager.blockPosition();

            // Try to find a valid spot nearby
            for (int i = 0; i < 10; i++) {
                int x = spawnPos.getX() + random.nextInt(10) - 5;
                int z = spawnPos.getZ() + random.nextInt(10) - 5;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);

                BlockPos pos = new BlockPos(x, y, z);
                if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()) && level.getBlockState(pos.below()).canOcclude()) {
                    GlitchVillagerEntity entity = ModEvents.GLITCH_VILLAGER.get().create(level);
                    if (entity != null) {
                        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
                        level.addFreshEntity(entity);
                        return; // Spawned one, stop
                    }
                }
            }
        }
    }
}

