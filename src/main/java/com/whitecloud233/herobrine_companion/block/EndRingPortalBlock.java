package com.whitecloud233.herobrine_companion.block;

import com.mojang.serialization.MapCodec;
import com.whitecloud233.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;

public class EndRingPortalBlock extends Block implements EntityBlock {
    public static final MapCodec<EndRingPortalBlock> CODEC = simpleCodec(EndRingPortalBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public EndRingPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndRingPortalBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && !entity.isPassenger() && !entity.isVehicle()) {
            
            ResourceKey<Level> destinationKey = ModStructures.END_RING_DIMENSION_KEY;
            
            if (level.dimension() == ModStructures.END_RING_DIMENSION_KEY) {
                destinationKey = Level.OVERWORLD; 
            }

            ServerLevel destinationLevel = serverLevel.getServer().getLevel(destinationKey);
            if (destinationLevel != null) {
                if (destinationKey == Level.OVERWORLD) {
                    BlockPos spawn = destinationLevel.getSharedSpawnPos();
                    entity.teleportTo(destinationLevel, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), 0.0f, 0.0f);
                } else {
                    // Teleport to (55.5, 104, 0.5) - On the inner ring (Radius 50-60)
                    entity.teleportTo(destinationLevel, 55.5, 104, 0.5, Set.of(), 0.0f, 0.0f);
                }
            }
        }
    }
}
