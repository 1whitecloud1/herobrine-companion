package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.Map;
import java.util.Set;

/**
 * 夜幕连招门面（编排层）：只做"把请求转发给数据/查找/构建/tick/特效各层"。
 * 对外保持原有 API（HeroEpicFightPatch / HeroNightfallAnimationRegistry / HeroEpicFightWeaponProfiles 依赖），
 * 内部不再持有任何具体实现细节。
 */
public final class HeroNightfallMovesets {
    private HeroNightfallMovesets() {
    }

    public static boolean isSupported(ItemStack stack) {
        return HeroNightfallProfiles.resolve(stack) != null;
    }

    public static double getAttackRadius(ItemStack stack, double fallback) {
        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(stack);
        return profile == null || !profile.combatSafe() ? fallback : profile.attackRadius();
    }

    static Set<AnimationAccessor<? extends StaticAnimation>> collectReferencedOriginalAnimations() {
        return HeroNightfallAnimationLookup.collectReferencedOriginalAnimations(HeroNightfallProfiles.all());
    }

    public static void applyLivingAnimations(ItemStack stack, Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations) {
        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(stack);
        if (profile == null) {
            return;
        }

        for (Map.Entry<LivingMotion, HeroNightfallAnimationField> entry : profile.livingOverrides().entrySet()) {
            AnimationAccessor<? extends StaticAnimation> animation = entry.getValue().resolve();
            if (isUsableAnimation(animation, entry.getValue())) {
                livingAnimations.put(entry.getKey(), animation);
            }
        }
    }

    @Nullable
    public static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCombatBehaviors(HeroEpicFightPatch patch, ItemStack stack) {
        return HeroNightfallCombatBehaviors.build(HeroNightfallProfiles.resolve(stack));
    }

    @Nullable
    public static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCombatBehaviors(ItemStack stack) {
        return buildCombatBehaviors(null, stack);
    }

    public static void tickSkillEffects(HeroEpicFightPatch patch, HeroEntity hero) {
        HeroNightfallSkillTicker.tickSkillEffects(patch, hero);
    }

    private static boolean isUsableAnimation(@Nullable AnimationAccessor<? extends StaticAnimation> animation, HeroNightfallAnimationField source) {
        return animation != null && (!animation.isEmpty() || HeroNightfallAnimationLookup.isDeferredEfnAnimationOwner(source.owner()));
    }
}
