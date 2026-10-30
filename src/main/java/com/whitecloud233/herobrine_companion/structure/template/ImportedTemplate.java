package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.CompoundTag;

/** Palette plus sparse vanilla blocks or compact dense regions, owned by the import until committed. */
public record ImportedTemplate(CompoundTag nbt, String format, int x, int y, int z, int blocks) { }
