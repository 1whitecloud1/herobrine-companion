package com.whitecloud233.herobrine_companion.entity.ai.combat;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Herobrine 战斗模式的攻击力来源（单一职责：把终末之诗的攻击力算给 Hero）。
 *
 * <p>战斗攻击固定继承<b>终末之诗的默认攻击力 + 信任度加成</b>，与装扮栏里实际拿的是
 * 什么武器无关——动作也一样取自终末之诗。数值口径与「玩家主手持终末之诗」完全一致：
 * 玩家基础攻击力 1.0 + 物品附加伤害（普通 {@code 8 + 信任/5}、破境/鸣雷 {@code 5 + 信任/5}、
 * 碎空 {@code 2 + 信任/20}，见 {@link PoemOfTheEndItem} 的 {@code updateAttributes}）。</p>
 *
 * <p>实现上用 ADDITION 修饰符把总攻击力「顶」到目标值，并把它记成
 * {@link #BATTLE_ATTACK_DAMAGE_ID} 的瞬时修饰符；换武器、切终末之诗模式、信任度变化
 * 都会在下一 tick 自动收敛到新数值。收刀即移除，平时不改变 Hero 的攻击力。</p>
 */
public final class HeroCombatPower {
    /** 与玩家侧一致：玩家 {@code Attributes.ATTACK_DAMAGE} 的基础值。 */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;
    /** 与 {@link PoemOfTheEndItem} 的 updateAttributes 同一份伤害表。 */
    private static final float NORMAL_BASE_DAMAGE = 8.0F;
    private static final float REALM_THUNDER_BASE_DAMAGE = 5.0F;
    private static final float VOID_SHATTER_BASE_DAMAGE = 2.0F;
    private static final float TRUST_DAMAGE_DIVISOR = 5.0F;
    private static final float VOID_SHATTER_TRUST_DIVISOR = 20.0F;
    private static final double EPSILON = 1.0E-4D;

    /**
     * Hero 战斗攻击力修饰符的固定 id（跟随实体，不随武器变化）。
     * 1.21.1 的 AttributeModifier 用 ResourceLocation 作为身份，不再是 UUID。
     */
    private static final ResourceLocation BATTLE_ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("herobrine_companion", "hero_battle_poem_damage");

    private HeroCombatPower() {
    }

    /** 终末之诗在给定信任度下的附加攻击力（玩家主手持它时吃到的那一份）。 */
    public static float poemAttackDamageBonus(int mode, int trust) {
        float clampedTrust = Math.max(0, trust);
        return switch (mode) {
            case PoemOfTheEndItem.MODE_VOID_SHATTER ->
                    VOID_SHATTER_BASE_DAMAGE + clampedTrust / VOID_SHATTER_TRUST_DIVISOR;
            case PoemOfTheEndItem.MODE_REALM_BREAKER, PoemOfTheEndItem.MODE_THUNDER_CALL ->
                    REALM_THUNDER_BASE_DAMAGE + clampedTrust / TRUST_DAMAGE_DIVISOR;
            default -> NORMAL_BASE_DAMAGE + clampedTrust / TRUST_DAMAGE_DIVISOR;
        };
    }

    /** 玩家主手持终末之诗时的总攻击力（基础 1.0 + 物品附加）。 */
    public static double poemTotalAttackDamage(int mode, int trust) {
        return PLAYER_BASE_ATTACK_DAMAGE + poemAttackDamageBonus(mode, trust);
    }

    /** 战斗模式使用的模式：手持终末之诗取它的模式，换成别的武器按普通模式（动作也只有这一套）。 */
    public static int battlePoemMode(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() instanceof PoemOfTheEndItem poem
                ? poem.getMode(stack) : PoemOfTheEndItem.MODE_NORMAL;
    }

    /** 该 Hero 此刻应具备的战斗总攻击力（继承终末之诗默认值与信任度加成）。 */
    public static double currentPoemAttackDamage(HeroEntity hero) {
        if (hero == null) {
            return poemTotalAttackDamage(PoemOfTheEndItem.MODE_NORMAL, 0);
        }
        return poemTotalAttackDamage(battlePoemMode(hero.getMainHandItem()), hero.getTrustLevel());
    }

    /**
     * 把战斗攻击力写进实体属性：只在战斗模式生效，收刀后移除修饰符。
     *
     * <p>幂等，可以每 tick 调用：数值没变时不做任何写入。</p>
     */
    public static void applyTo(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        AttributeInstance attackDamage = hero.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }
        if (!hero.isBattleModeActive() || hero.isChallengeActiveState()) {
            attackDamage.removeModifier(BATTLE_ATTACK_DAMAGE_ID);
            return;
        }

        // 先把「除战斗修饰符以外」的所有来源（基础值 + 主手武器/药水等修饰符）算出来，
        // 再补一个差值。这样无论 Hero 手里是终末之诗还是别的武器，最终都落在同一数值上。
        double natural = valueWithoutBattleModifier(attackDamage);
        double desiredAmount = currentPoemAttackDamage(hero) - natural;
        AttributeModifier existing = attackDamage.getModifier(BATTLE_ATTACK_DAMAGE_ID);
        if (existing != null && Math.abs(existing.amount() - desiredAmount) < EPSILON) {
            return;
        }
        if (existing != null) {
            attackDamage.removeModifier(BATTLE_ATTACK_DAMAGE_ID);
        }
        attackDamage.addTransientModifier(new AttributeModifier(
                BATTLE_ATTACK_DAMAGE_ID, desiredAmount, AttributeModifier.Operation.ADD_VALUE));
    }

    /** 立即清除（离开战斗模式时调用）。 */
    public static void clearFrom(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        AttributeInstance attackDamage = hero.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(BATTLE_ATTACK_DAMAGE_ID);
        }
    }

    /**
     * 复现 {@link AttributeInstance#getValue()} 的合成顺序，但跳过本类自己的修饰符。
     *
     * <p>与 {@code AttributeInstance.getValue()} 保持同一套运算（先 ADD_VALUE 求和，
     * 再乘 ADD_MULTIPLIED_BASE，最后乘 ADD_MULTIPLIED_TOTAL 的连乘），否则算出的差值会漂。</p>
     */
    private static double valueWithoutBattleModifier(AttributeInstance attribute) {
        Collection<AttributeModifier> modifiers = attribute.getModifiers();
        double addition = 0.0D;
        double multiplyBase = 0.0D;
        double multiplyTotal = 1.0D;
        for (AttributeModifier modifier : modifiers) {
            if (BATTLE_ATTACK_DAMAGE_ID.equals(modifier.id())) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> addition += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multiplyBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiplyTotal *= 1.0D + modifier.amount();
            }
        }
        return (attribute.getBaseValue() + addition) * (1.0D + multiplyBase) * multiplyTotal;
    }
}
