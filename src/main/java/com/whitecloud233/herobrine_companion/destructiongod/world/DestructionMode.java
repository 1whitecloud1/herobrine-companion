package com.whitecloud233.herobrine_companion.destructiongod.world;

/** 地形破坏模式。现在由 {@link DestructionTuning#MODE} 固定，不再从配置读取。 */
enum DestructionMode {
    VISUAL,
    SAFE,
    DIVINE;

    /** 当前生效模式：固定为 {@link DestructionTuning#MODE}。 */
    static DestructionMode current() {
        return DestructionTuning.MODE;
    }
}

