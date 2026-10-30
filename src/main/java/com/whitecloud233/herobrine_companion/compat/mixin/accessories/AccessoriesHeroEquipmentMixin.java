package com.whitecloud233.herobrine_companion.compat.mixin.accessories;

import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesMenuCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The original menu is constructed on both the client and the dedicated server. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.menu.variants.AccessoriesMenu", remap = false)
public abstract class AccessoriesHeroEquipmentMixin {

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("RETURN"))
    private void herobrineCompanion$bindEquipment(int containerId, Inventory inventory,
                                                LivingEntity target, CallbackInfo ci) {
        if (target instanceof HeroEntity hero) {
            HeroAccessoriesMenuCompat.bindEquipmentSlots((AbstractContainerMenu) (Object) this, inventory, hero);
        }
    }
}
