package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.CombatKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 「玩家确认攻击」窗口的唯一读写入口。
 *
 * <p><b>为什么需要它：</b>误伤确认状态写在 Hero 的 persistentData 上，但有两个完全独立的
 * 入口会读写它——{@code HeroCombatHandler}（实体受伤路径）和 {@code HeroProtectionEvents}
 * （{@code LivingAttackEvent} 路径）。两者各自实现了一份相同的
 * 「读取窗口 / 记录误伤 / 清除窗口」逻辑，连键名都是各写一份字面量。
 * 这带来两个风险：</p>
 *
 * <ul>
 *   <li>改键名时只改一处，另一处静默读不到，表现为「第一次打就掉信任」；</li>
 *   <li>两边逻辑漂移后，同一击在事件层被判为误伤、在受伤层被判为确认攻击。</li>
 * </ul>
 *
 * <p>本类只回答一个问题：<b>这次攻击是否算作玩家已确认的意图</b>。
 * 具体的惩罚、掉信任、传送等业务分支仍留在各自的调用方。</p>
 */
public final class AttackConfirmation {

    /** 第一次点击后允许玩家在多长时间内再次点击才算「确认动手」。 */
    public static final long CONFIRM_WINDOW_TICKS = 100L;

    private AttackConfirmation() {
    }

    /**
     * 这次攻击是否落在已确认的窗口内。
     *
     * <p>窗口过期时会顺手清理残留键，避免旧数据一直挂在实体上。
     * 客户端不判定——避免客户端与服务端各算一次窗口产生分歧。</p>
     */
    public static boolean isConfirmed(HeroEntity hero, Player player) {
        if (hero == null || player == null || hero.level().isClientSide) {
            return false;
        }
        CompoundTag data = hero.getPersistentData();
        long currentGameTime = hero.level().getGameTime();
        if (data.getLong(CombatKeys.PENDING_ATTACK_CONFIRM_UNTIL) < currentGameTime) {
            clear(hero);
            return false;
        }
        return data.hasUUID(CombatKeys.PENDING_ATTACK_CONFIRM_PLAYER)
                && player.getUUID().equals(data.getUUID(CombatKeys.PENDING_ATTACK_CONFIRM_PLAYER));
    }

    /** 记录一次误伤，开启确认窗口。 */
    public static void remember(HeroEntity hero, Player player) {
        if (hero == null || player == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        data.putUUID(CombatKeys.PENDING_ATTACK_CONFIRM_PLAYER, player.getUUID());
        data.putLong(CombatKeys.PENDING_ATTACK_CONFIRM_UNTIL,
                hero.level().getGameTime() + CONFIRM_WINDOW_TICKS);
    }

    /** 清除确认窗口。窗口过期、玩家被认可、或挑战开始/结束时调用。 */
    public static void clear(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        data.remove(CombatKeys.PENDING_ATTACK_CONFIRM_PLAYER);
        data.remove(CombatKeys.PENDING_ATTACK_CONFIRM_UNTIL);
    }
}
