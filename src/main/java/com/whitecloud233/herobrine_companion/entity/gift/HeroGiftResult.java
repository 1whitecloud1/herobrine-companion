package com.whitecloud233.herobrine_companion.entity.gift;

import java.util.List;

/**
 * 赠礼判定结果(纯数据载体,由 HeroGiftEvaluator 产出)。
 *
 * <p>字段与 Bedrock hero_player_offer_evaluator 的 result 字典一致;
 * 消费方(HeroOfferService / HeroGiftFeedbackService)按 code 决定台词、
 * 消耗、信任变化、回礼与表现。
 */
public record HeroGiftResult(
        String code,
        boolean accepted,
        boolean consume,
        int trustDelta,
        int cooldownTicks,
        boolean pendingReturn,
        Score score,
        TasteDelta tasteDelta,
        List<BrainSignal> brainSignals,
        String returnItemName
) {

    /** 得分明细。 */
    public record Score(int memory, int warmth, int restraint, int rift, int pollution, int suspicion) {
        public int positive() {
            return memory + warmth + restraint + rift;
        }
    }

    /** 口味档案增量(每轴上限 3,重复赠送衰减)。 */
    public record TasteDelta(int memoryTaste, int warmthTaste, int restraintTaste, int pollutionTaste) {
    }

    /** 心智状态信号(与 Bedrock hero_brain_state.input_brain_signal 的输入类型对应)。 */
    public record BrainSignal(Type type, double amount) {
        public enum Type {
            NOSTALGIA, CREATIVITY, META, MONSTER_INTEREST, VIOLENCE, ENTROPY
        }
    }

    public boolean isAccepted() {
        return accepted;
    }
}