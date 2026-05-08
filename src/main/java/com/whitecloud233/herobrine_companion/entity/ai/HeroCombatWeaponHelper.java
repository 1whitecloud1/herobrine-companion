package com.whitecloud233.herobrine_companion.entity.ai;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class HeroCombatWeaponHelper {
    private static final double PROJECTILE_GRAVITY = 0.05D;
    private static final double MIN_HORIZONTAL_DISTANCE = 1.0E-4D;
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

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        return getChargeTicks(stack, null);
    }

    public static int getChargeTicks(ItemStack stack, LivingEntity shooter) {
        if (isCrossbowWeapon(stack)) {
            return CrossbowItem.getChargeDuration(stack, shooter);
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

    public static boolean fireRangedWeaponAtTarget(HeroEntity hero, LivingEntity target, ItemStack weaponStack, int chargeTicks) {
        if (hero == null || target == null || weaponStack == null || weaponStack.isEmpty()) {
            return false;
        }

        if (isCrossbowWeapon(weaponStack) || weaponStack.getUseAnimation() == UseAnim.CROSSBOW) {
            return fireCrossbow(hero, target, weaponStack);
        }

        if (weaponStack.getUseAnimation() == UseAnim.BOW || weaponStack.getItem() instanceof BowItem) {
            return fireBowAtTarget(hero, target, weaponStack, chargeTicks);
        }

        return fireVirtualProjectileAtTarget(hero, target, weaponStack, Math.max(chargeTicks, DEFAULT_GENERIC_DRAW_TICKS), 2.6F, 4.5F, SoundEvents.CROSSBOW_SHOOT);
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

        AbstractArrow arrow = arrowItem.createArrow(hero.level(), projectileStack, hero, bowStack);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow, projectileStack, bowStack);
        }

        float launchVelocity = power * 3.0F;
        Vec3 shotVector = createProjectileShotVector(arrow, hero, target, launchVelocity);

        aimAlongShotVector(hero, shotVector);
        arrow.shoot(shotVector.x, shotVector.y, shotVector.z, launchVelocity, 6.0F);
        arrow.setCritArrow(power >= 1.0F);

        hero.level().addFreshEntity(arrow);
        hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F / (hero.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        hero.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean fireCrossbow(HeroEntity hero, ItemStack stack) {
        return fireCrossbow(hero, null, stack);
    }

    public static boolean fireCrossbow(HeroEntity hero, LivingEntity target, ItemStack stack) {
        if (hero == null || target == null || stack == null || stack.isEmpty() || hero.level().isClientSide) {
            return false;
        }

        return fireVirtualProjectileAtTarget(hero, target, stack, CrossbowItem.getChargeDuration(stack, hero), 3.15F, 1.0F, SoundEvents.CROSSBOW_SHOOT);
    }

    private static void aimAlongShotVector(HeroEntity hero, Vec3 shotVector) {
        double horizontalDistance = Math.sqrt(shotVector.x * shotVector.x + shotVector.z * shotVector.z);
        float yaw = (float) (Mth.atan2(shotVector.z, shotVector.x) * (180.0F / (float) Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(shotVector.y, horizontalDistance) * (180.0F / (float) Math.PI)));

        hero.setYRot(yaw);
        hero.setXRot(pitch);
        hero.setYHeadRot(yaw);
        hero.setYBodyRot(yaw);
    }

    private static Vec3 createProjectileShotVector(AbstractArrow arrow, HeroEntity hero, LivingEntity target, float launchVelocity) {
        double dx = target.getX() - hero.getX();
        double dz = target.getZ() - hero.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double targetY = target.getEyeY() - Math.max(0.1D, target.getBbHeight() * 0.15D);
        double baseDy = targetY - arrow.getY();

        if (horizontalDistance < MIN_HORIZONTAL_DISTANCE) {
            return new Vec3(dx, baseDy, dz);
        }

        double speed = Math.max(0.1D, launchVelocity);
        double speedSq = speed * speed;
        double discriminant = speedSq * speedSq - PROJECTILE_GRAVITY * (PROJECTILE_GRAVITY * horizontalDistance * horizontalDistance + 2.0D * baseDy * speedSq);
        double adjustedDy;

        if (discriminant >= 0.0D) {
            double sqrt = Math.sqrt(discriminant);
            double tanTheta = (speedSq - sqrt) / (PROJECTILE_GRAVITY * horizontalDistance);
            adjustedDy = horizontalDistance * tanTheta;
        } else {
            adjustedDy = baseDy + horizontalDistance * 0.2D;
        }

        return new Vec3(dx, adjustedDy, dz);
    }

    private static boolean fireVirtualProjectileAtTarget(HeroEntity hero,
                                                         LivingEntity target,
                                                         ItemStack weaponStack,
                                                         int chargeTicks,
                                                         float velocity,
                                                         float inaccuracy,
                                                         net.minecraft.sounds.SoundEvent shootSound) {
        if (hero == null || target == null || weaponStack == null || weaponStack.isEmpty() || hero.level().isClientSide) {
            return false;
        }

        ItemStack projectileStack = hero.getProjectile(weaponStack);
        ArrowItem arrowItem;
        if (projectileStack.isEmpty() || !(projectileStack.getItem() instanceof ArrowItem)) {
            projectileStack = new ItemStack(Items.ARROW);
            arrowItem = (ArrowItem) Items.ARROW;
        } else {
            arrowItem = (ArrowItem) projectileStack.getItem();
        }

        AbstractArrow arrow = arrowItem.createArrow(hero.level(), projectileStack, hero, weaponStack);
        float powerScale = weaponStack.getUseAnimation() == UseAnim.BOW
                ? BowItem.getPowerForTime(Math.max(chargeTicks, DEFAULT_BOW_DRAW_TICKS))
                : Mth.clamp((float) chargeTicks / (float) Math.max(1, getChargeTicks(weaponStack, hero)), 0.65F, 1.0F);
        float launchVelocity = velocity * powerScale;
        Vec3 shotVector = createProjectileShotVector(arrow, hero, target, launchVelocity);

        aimAlongShotVector(hero, shotVector);
        arrow.shoot(shotVector.x, shotVector.y, shotVector.z, launchVelocity, inaccuracy);
        arrow.setCritArrow(powerScale >= 0.95F);

        hero.level().addFreshEntity(arrow);
        hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), shootSound, SoundSource.HOSTILE, 1.0F,
                1.0F / (hero.getRandom().nextFloat() * 0.4F + 1.2F) + powerScale * 0.5F);
        hero.swing(InteractionHand.MAIN_HAND);
        return true;
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

