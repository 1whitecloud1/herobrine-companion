package com.whitecloud233.modid.herobrine_companion.entity.ai;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

public final class HeroCombatWeaponHelper {
    private static final int DEFAULT_BOW_DRAW_TICKS = 20;
    private static final int DEFAULT_GENERIC_DRAW_TICKS = 12;
    private static final int DEFAULT_BOW_COOLDOWN = 20;
    private static final int DEFAULT_CROSSBOW_COOLDOWN = 30;
    private static final int DEFAULT_GUN_COOLDOWN = 14;

    private HeroCombatWeaponHelper() {
    }

    public static boolean isRangedLoadout(HeroEntity hero) {
        return hero != null && isRangedLoadout(hero.getMainHandItem());
    }

    public static boolean isRangedLoadout(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof CrossbowItem) {
            return true;
        }

        UseAnim useAnim = stack.getUseAnimation();
        if (useAnim == UseAnim.BOW || useAnim == UseAnim.CROSSBOW) {
            return true;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemPath = itemId != null ? itemId.toString().toLowerCase(Locale.ROOT) : "";
        return containsRangedKeyword(itemPath);
    }

    public static boolean isCrossbowWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isGunLikeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemPath = itemId != null ? itemId.toString().toLowerCase(Locale.ROOT) : "";
        return itemPath.contains("gun")
                || itemPath.contains("firearm")
                || itemPath.contains("rifle")
                || itemPath.contains("pistol")
                || itemPath.contains("revolver")
                || itemPath.contains("musket")
                || itemPath.contains("shotgun")
                || itemPath.contains("sniper")
                || itemPath.contains("launcher")
                || itemPath.contains("cannon")
                || itemPath.contains("blaster");
    }

    public static int getChargeTicks(ItemStack stack) {
        if (isCrossbowWeapon(stack)) {
            return CrossbowItem.getChargeDuration(stack);
        }

        UseAnim useAnim = stack.getUseAnimation();
        if (useAnim == UseAnim.BOW) {
            return DEFAULT_BOW_DRAW_TICKS;
        }
        return DEFAULT_GENERIC_DRAW_TICKS;
    }

    public static int getPostShotCooldown(ItemStack stack) {
        if (isCrossbowWeapon(stack)) {
            return DEFAULT_CROSSBOW_COOLDOWN;
        }
        if (stack != null && stack.getUseAnimation() == UseAnim.BOW) {
            return DEFAULT_BOW_COOLDOWN;
        }
        if (isGunLikeWeapon(stack)) {
            return DEFAULT_GUN_COOLDOWN;
        }
        return DEFAULT_CROSSBOW_COOLDOWN;
    }

    public static float getPreferredAttackRadius(ItemStack stack) {
        if (isGunLikeWeapon(stack)) {
            return 18.0F;
        }
        if (isCrossbowWeapon(stack)) {
            return 16.0F;
        }
        if (stack != null && stack.getUseAnimation() == UseAnim.BOW) {
            return 15.0F;
        }
        return 14.0F;
    }

    public static boolean fireBowAtTarget(HeroEntity hero, LivingEntity target, ItemStack bowStack, int chargeTicks) {
        if (hero == null || target == null || bowStack == null || bowStack.isEmpty() || hero.level().isClientSide) {
            return false;
        }

        ItemStack projectileStack = hero.getProjectile(bowStack);
        ArrowItem arrowItem;
        if (projectileStack.isEmpty() || !(projectileStack.getItem() instanceof ArrowItem)) {
            projectileStack = new ItemStack(Items.ARROW);
            arrowItem = (ArrowItem) Items.ARROW;
        } else {
            arrowItem = (ArrowItem) projectileStack.getItem();
        }

        float power = BowItem.getPowerForTime(Math.max(chargeTicks, DEFAULT_BOW_DRAW_TICKS));
        if (power < 0.1F) {
            return false;
        }

        AbstractArrow arrow = arrowItem.createArrow(hero.level(), projectileStack, hero);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow);
        }

        double dx = target.getX() - hero.getX();
        double dz = target.getZ() - hero.getZ();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();

        aimAtTarget(hero, target);
        arrow.shoot(dx, dy, dz, power * 3.0F, 6.0F);
        arrow.setCritArrow(power >= 1.0F);

        int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bowStack);
        if (powerLevel > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) powerLevel * 0.5D + 0.5D);
        }

        int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bowStack);
        if (punchLevel > 0) {
            arrow.setKnockback(punchLevel);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bowStack) > 0) {
            arrow.setSecondsOnFire(100);
        }

        hero.level().addFreshEntity(arrow);
        hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F / (hero.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        hero.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean fireCrossbow(HeroEntity hero, ItemStack stack) {
        return fireCrossbow(hero, null, stack);
    }

    public static boolean fireCrossbow(HeroEntity hero, LivingEntity target, ItemStack stack) {
        if (hero == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) {
            return false;
        }
        if (!CrossbowItem.isCharged(stack)) {
            return false;
        }

        try {
            if (target != null) {
                aimAtTarget(hero, target);
            }
            CrossbowItem.performShooting(hero.level(), hero, InteractionHand.MAIN_HAND, stack, 1.6F, 1.0F);
            CrossbowItem.setCharged(stack, false);
            hero.swing(InteractionHand.MAIN_HAND);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void aimAtTarget(HeroEntity hero, LivingEntity target) {
        double dx = target.getX() - hero.getX();
        double dy = target.getEyeY() - hero.getEyeY();
        double dz = target.getZ() - hero.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontalDistance) * (180.0F / (float) Math.PI)));

        hero.setYRot(yaw);
        hero.setXRot(pitch);
        hero.setYHeadRot(yaw);
        hero.setYBodyRot(yaw);
    }

    private static boolean containsRangedKeyword(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        return value.contains("bow")
                || value.contains("crossbow")
                || value.contains("ranged")
                || value.contains("gun")
                || value.contains("firearm")
                || value.contains("rifle")
                || value.contains("pistol")
                || value.contains("revolver")
                || value.contains("musket")
                || value.contains("shotgun")
                || value.contains("sniper")
                || value.contains("launcher")
                || value.contains("cannon")
                || value.contains("blaster");
    }
}

