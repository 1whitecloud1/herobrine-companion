package com.whitecloud233.herobrine_companion.util;

/**
 * 战斗与击杀确认相关的 NBT 键。
 *
 * <p>从 {@link NbtKeys} 拆出：这些键都写在 {@code hero.getPersistentData()} 上，
 * 由「玩家确认攻击」这一条业务逻辑独占，和挑战、臣服、Jean 那些互不相干。</p>
 */
public final class CombatKeys {

    private CombatKeys() {
    }

    // ==========================================================================
    // 误伤确认窗口（「第一次打是误伤，短时间内再打才算动手」）
    //
    // 注意：这一对键会被两个类同时读写——HeroCombatHandler（实体受伤路径）
    // 与 HeroProtectionEvents（LivingAttackEvent 路径）。两条路径必须看到
    // 同一份窗口数据，否则会出现「事件层认为是误伤、受伤层认为是确认攻击」
    // 的分裂判断。历史上两处各自定义了一份字面量，改名时极易只改一处。
    // 现在统一以 {@link AttackConfirmation} 为唯一读写入口。
    // ==========================================================================

    /** 上一次误伤该 Hero 的玩家 UUID。 */
    public static final String PENDING_ATTACK_CONFIRM_PLAYER = "PendingAttackConfirmPlayer";

    /** 误伤确认窗口的截止 gameTime。 */
    public static final String PENDING_ATTACK_CONFIRM_UNTIL = "PendingAttackConfirmUntil";
}
