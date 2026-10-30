package com.whitecloud233.modid.herobrine_companion.entity.cooking;

import java.util.UUID;

public final class CookingRules {
    /** 无邀请。邀请动作编号的单一来源，{@code HeroInvitationHelper.ACTION_NONE} 是它的别名。 */
    public static final int ACTION_NONE = 0;
    public static final int INVITED_ACTION_COOK = 4;
    /** 石磨拉磨：与做菜同属厨具邀请，但由石磨自己负责摆放和转向。 */
    public static final int INVITED_ACTION_MILLSTONE = 5;
    /**
     * 磨盘转满一圈的 tick 数，越小越快。与石磨 datamap 里的 {@code rot_speed_tick} 保持一致。
     * 客户端推磨姿态的步频也按这个值算，所以两边改一处即可。
     */
    public static final float MILLSTONE_ROT_SPEED_TICK = 20.0F;

    /** 石磨一次最多吃这么多材料（对应 {@code MillstoneBlockEntity.MAX_INPUT_COUNT}）。 */
    public static final int MILLSTONE_MAX_INPUT_COUNT = 8;
    public static final int MIN_COOK_REPEAT_COUNT = 1;
    public static final int MAX_COOK_REPEAT_COUNT = 64;

    // ==========================================================================
    // 厨具会话状态键前缀
    //
    // 每个厨具模组必须使用不同前缀，否则切换厨具时会话状态会互相污染
    // （详见 CookSessionState 的类注释）。这些值是存档契约，发布后不可更改。
    // ==========================================================================

    /** 农夫乐事的会话键前缀。 */
    public static final String FD_STATE_PREFIX = "HeroFd";

    /** 森罗物语的会话键前缀。 */
    public static final String KALEIDOSCOPE_STATE_PREFIX = "Hero";

    private CookingRules() {
    }

    public static int clampRepeatCount(int count) {
        return Math.max(MIN_COOK_REPEAT_COUNT, Math.min(MAX_COOK_REPEAT_COUNT, count));
    }

    public static boolean canManage(UUID requester, UUID owner, boolean boundContract, boolean alive, boolean sameLevel) {
        return requester != null && owner != null && (requester.equals(owner) || boundContract) && alive && sameLevel;
    }

    public enum Recipient { HERO, MONSTER, OWNER }

    public record SharingWeights(int hero, int monster, int owner) {
        public int total() { return hero + monster + owner; }

        public Recipient choose(int roll) {
            if (roll < 0 || roll >= total()) throw new IllegalArgumentException("Invalid sharing roll");
            if (roll < hero) return Recipient.HERO;
            return roll < hero + monster ? Recipient.MONSTER : Recipient.OWNER;
        }
    }

    public static SharingWeights sharingWeights(int trust, boolean nearbyOwner, boolean nearbyMonster) {
        int ownerWeight = nearbyOwner && trust >= 20 ? (trust >= 80 ? 4 : trust >= 50 ? 2 : 1) : 0;
        return new SharingWeights(3, nearbyMonster ? 2 : 0, ownerWeight);
    }
}
