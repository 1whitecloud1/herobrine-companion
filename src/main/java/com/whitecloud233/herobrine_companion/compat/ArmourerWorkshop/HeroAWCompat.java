package com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import moe.plushie.armourers_workshop.core.capability.SkinWardrobe;

public class HeroAWCompat {

    public static boolean isLoaded() {
        return ModList.get().isLoaded("armourers_workshop");
    }

    public static boolean isAwItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return registryName != null && registryName.getNamespace().equals("armourers_workshop");
    }

    // Synchronize Profile and Identity every frame
    public static void syncContext(EntityRenderer<?> renderer, net.minecraft.world.entity.Entity entity) {
        if (!isLoaded()) return;
        try {
            var context = moe.plushie.armourers_workshop.core.client.other.EntityRendererContext.of(renderer);
            //System.out.println("[Herobrine AW] syncContext: context=" + context);

            var wardrobe = SkinWardrobe.of(entity);
           // System.out.println("[Herobrine AW] syncContext: wardrobe=" + wardrobe);

            if (wardrobe != null) {
                var profile = wardrobe.profile();
               // System.out.println("[Herobrine AW] syncContext: profile=" + profile);

                if (profile != null) {
                    context.setEntityProfile(profile);
                    context.setEntityType(EntityType.PLAYER);
                }
            }
        } catch (Throwable e) {
            System.err.println("[Herobrine AW] Critical failure in syncContext!");
            e.printStackTrace();
        }
    }
}