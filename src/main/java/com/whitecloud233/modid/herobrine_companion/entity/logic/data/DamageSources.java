package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 从伤害来源里解析出「动手的玩家」。
 *
 * <p>直接攻击与弹射物（箭、三叉戟、枪械子弹等）两种情况都要覆盖：
 * 伤害源上的 {@code getEntity()} 是投掷者时直接返回，否则回退到
 * {@code getDirectEntity()} 的 owner。</p>
 *
 * <p><b>为什么单独抽出来：</b>{@code HeroCombatHandler}（实体受伤路径）与
 * {@code HeroProtectionEvents}（{@code LivingAttackEvent} 路径）各实现了一份逐字符
 * 相同的版本。两处必须对「谁算攻击者」给出完全一致的答案，否则同一击在事件层被
 * 判为怪物伤害、在受伤层被判为玩家伤害，从而绕过误伤确认窗口。</p>
 */
public final class DamageSources {

    private DamageSources() {
    }

    /**
     * @return 造成这次伤害的玩家；不是玩家来源（含 {@code null} 源）时返回 {@code null}
     */
    public static Player playerAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            return player;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }

        return null;
    }
}
