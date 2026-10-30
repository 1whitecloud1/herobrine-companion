package com.whitecloud233.modid.herobrine_companion.config;

/** 可经界面修改的服务端规则。wireId 固定，不能使用 ordinal 作为协议编号。 */
public enum ServerRule {
    POEM_EXPLOSION(0, false),
    KING_AURA(1, false),
    BLOCK_RESTORATION(2, false),
    CLEAN_ITEMS(3, false),
    CLEAVE_SKILL(4, false),
    SOUL_BOUND_PACT(5, true),
    ABYSSAL_GAZE(6, true),
    TRANSCENDENCE_PERMIT(7, true),
    AI_VISION_INTERVAL(8, 30, 5, 600),
    AWAKENED_DIALOGUE(9, true),
    LEAF_VANISH(10, true),
    PRANKSTER_FAKE_CHEST(11, true),
    POEM_ACTIONS(12, true),
    // 战斗模式限制三件套：信任门槛 / 单次时长 / 收刀冷却，均为 0 = 不限制。
    BATTLE_MODE_TRUST_THRESHOLD(13, 200, 0, 1000),
    BATTLE_MODE_DURATION_SECONDS(14, 300, 0, 3600),
    BATTLE_MODE_COOLDOWN_SECONDS(15, 300, 0, 3600);

    private final int wireId;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final boolean toggle;

    ServerRule(int wireId, boolean defaultValue) {
        this(wireId, defaultValue ? 1 : 0, 0, 1, true);
    }

    ServerRule(int wireId, int defaultValue, int min, int max) {
        this(wireId, defaultValue, min, max, false);
    }

    ServerRule(int wireId, int defaultValue, int min, int max, boolean toggle) {
        this.wireId = wireId;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.toggle = toggle;
    }

    public int wireId() { return wireId; }
    public int defaultValue() { return defaultValue; }
    public boolean isToggle() { return toggle; }
    public int normalize(int value) { return Math.max(min, Math.min(max, value)); }

    public static ServerRule fromWireId(int id) {
        for (ServerRule rule : values()) if (rule.wireId == id) return rule;
        throw new IllegalArgumentException("Unknown server rule id: " + id);
    }
}
