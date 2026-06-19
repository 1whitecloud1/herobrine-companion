package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.registries.ForgeRegistries;

public class HeroGunAdapter {

    private static final String[] VIRTUAL_BULLET_KEYWORDS = new String[] {
            "bullet", "ammo", "round", "cartridge", "shell", "slug", "musket_ball", "musketball"
    };

    public static ItemStack getProjectile(HeroEntity hero, ItemStack weaponStack) {
        if (weaponStack == null || weaponStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (weaponStack.getItem() instanceof ProjectileWeaponItem || weaponStack.getItem() instanceof CrossbowItem) {
            ItemStack virtualProjectile = getVirtualProjectileFor(hero, weaponStack);
            return virtualProjectile.isEmpty() ? new ItemStack(Items.ARROW) : virtualProjectile;
        }

        UseAnim useAnim = weaponStack.getUseAnimation();
        if (useAnim == UseAnim.BOW || useAnim == UseAnim.CROSSBOW) {
            ItemStack virtualProjectile = getVirtualProjectileFor(hero, weaponStack);
            return virtualProjectile.isEmpty() ? new ItemStack(Items.ARROW) : virtualProjectile;
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getVirtualProjectileFor(HeroEntity hero, ItemStack weaponStack) {
        ResourceLocation weaponId = ForgeRegistries.ITEMS.getKey(weaponStack.getItem());
        String weaponPath = weaponId != null ? weaponId.getPath().toLowerCase() : "";

        if (!looksLikeGunWeapon(weaponPath)) {
            return new ItemStack(Items.ARROW);
        }

        for (net.minecraft.world.item.Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) {
                continue;
            }

            String itemPath = itemId.getPath().toLowerCase();
            for (String keyword : VIRTUAL_BULLET_KEYWORDS) {
                if (itemPath.contains(keyword)) {
                    return new ItemStack(item);
                }
            }
        }

        return new ItemStack(Items.ARROW);
    }

    private static boolean looksLikeGunWeapon(String weaponPath) {
        return weaponPath.contains("gun")
                || weaponPath.contains("firearm")
                || weaponPath.contains("rifle")
                || weaponPath.contains("pistol")
                || weaponPath.contains("revolver")
                || weaponPath.contains("musket")
                || weaponPath.contains("shotgun")
                || weaponPath.contains("cannon")
                || weaponPath.contains("blaster");
    }
}
