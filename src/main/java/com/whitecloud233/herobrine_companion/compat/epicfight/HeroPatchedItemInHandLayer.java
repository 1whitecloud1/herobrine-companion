package com.whitecloud233.herobrine_companion.compat.epicfight;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class HeroPatchedItemInHandLayer<E extends LivingEntity, T extends LivingEntityPatch<E>, M extends EntityModel<E>> extends PatchedItemInHandLayer<E, T, M> {
}
