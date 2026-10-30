package com.whitecloud233.modid.herobrine_companion.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.NbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 挑战状态的唯一读取入口。
 *
 * <p><b>为什么需要它：</b>「挑战进行中」这个事实在代码里有三份副本——玩家的
 * {@code persistentData}、Hero 的 {@code persistentData}（都是存档数据），
 * 以及 Hero 的 {@code entityData}（同步给客户端）。三者由不同代码写入、在不同时机清除，
 * 历史上靠「每 tick 轮询自愈」和「受伤时补一刀」这类补丁维持一致。
 * 把判定收进一个方法后，调用方不必再关心该读哪一份，也不会再写出
 * 「拿玩家标记判断 Hero 状态」这种能编译但语义错位的代码。</p>
 *
 * <p><b>本类只读，不写。</b>写入仍然由 {@code HeroChallengeManager} 负责，
 * 等读取路径稳定后再迁移写入，避免一次性改动 {@code changeDimension} 回调这类难以验证的路径。</p>
 */
public final class ChallengeSession {

    private ChallengeSession() {
    }

    // ==========================================================================
    // 挑战进行中
    // ==========================================================================

    /**
     * 这只 Hero 是否处于挑战中。
     *
     * <p>取 {@code entityData} 与 {@code persistentData} 的并集：前者是客户端权威值，
     * 后者是存档权威值。任何一方为真都算在挑战中。并集语义等价于原先
     * {@code HeroChallengeState} 里「每 tick 检查 entityData 丢失就补回」的自愈逻辑，
     * 但不需要等下一个 tick、也不依赖调用顺序。</p>
     */
    public static boolean isHeroInChallenge(HeroEntity hero) {
        if (hero == null) {
            return false;
        }
        return hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || hero.getPersistentData().getBoolean(NbtKeys.HERO_IN_CHALLENGE);
    }

    /**
     * 这名玩家是否处于挑战中。
     *
     * <p>只读玩家侧存档标记，不涉及 Hero。挑战占用锁、禁放方块、禁飞行都以它为准。</p>
     */
    public static boolean isPlayerInChallenge(Player player) {
        if (player == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        return data.getBoolean(NbtKeys.PLAYER_IN_CHALLENGE);
    }

    /** {@link #isPlayerInChallenge(Player)} 的 NBT 重载，供只有 CompoundTag 的调用点使用。 */
    public static boolean isPlayerInChallenge(CompoundTag playerData) {
        return playerData != null && playerData.getBoolean(NbtKeys.PLAYER_IN_CHALLENGE);
    }

    // ==========================================================================
    // 假死演出（「虚晃一枪」）阶段
    // ==========================================================================

    /** Boss 是否处于假死演出中。演出期间 Boss 绝对无敌，AI 已被清空。 */
    public static boolean isHeroFakeOutPhase(HeroEntity hero) {
        return hero != null && hero.getPersistentData().getBoolean(NbtKeys.HERO_FAKE_OUT_PHASE);
    }

    /** 这名玩家是否处于假死演出中。演出期间玩家锁半颗心、关闭 UI。 */
    public static boolean isPlayerFakeOutPhase(Player player) {
        return player != null && player.getPersistentData().getBoolean(NbtKeys.PLAYER_FAKE_OUT_PHASE);
    }
}
