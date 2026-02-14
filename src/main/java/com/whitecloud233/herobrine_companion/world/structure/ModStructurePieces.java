package com.whitecloud233.herobrine_companion.world.structure;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModStructurePieces {
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, HerobrineCompanion.MODID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> END_RING_PIECE = STRUCTURE_PIECES.register("end_ring_piece", () -> (StructurePieceType) EndRingPiece::new);
    
    public static final DeferredHolder<StructurePieceType, StructurePieceType> UNSTABLE_ZONE_PIECE = STRUCTURE_PIECES.register("unstable_zone_piece", () -> UnstableZonePiece::new);
}
