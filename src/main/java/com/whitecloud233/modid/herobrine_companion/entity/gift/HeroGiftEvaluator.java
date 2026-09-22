package com.whitecloud233.modid.herobrine_companion.entity.gift;

import com.whitecloud233.modid.herobrine_companion.entity.gift.HeroGiftResult.BrainSignal;
import com.whitecloud233.modid.herobrine_companion.entity.gift.HeroGiftResult.TasteDelta;

import java.util.ArrayList;
import java.util.List;

/**
 * 赠礼评分与判定 —— Bedrock hero_player_offer_evaluator.py 的纯逻辑移植。
 *
 * <p>单一职责:只做评分与决策。输入 {@link HeroGiftOffer},输出 {@link HeroGiftResult};
 * 不接触存档、网络、表现。所有数值断言语义与 Bedrock 版逐行一致。
 */
public final class HeroGiftEvaluator {

    private HeroGiftEvaluator() {
    }

    public static final int COOLDOWN_DEFAULT = 200;
    public static final int COOLDOWN_ACCEPT = 400;
    public static final int COOLDOWN_ASK = 1200;
    public static final int COOLDOWN_TABOO = 1200;
    public static final int COOLDOWN_REPEAT = 6000;
    public static final int COOLDOWN_JUDGED = 6000;
    public static final int COOLDOWN_EMPTY_FIRST = 100;

    // ------------------------------------------------------------------
    // 评分
    // ------------------------------------------------------------------

    public static HeroGiftResult.Score scoreOffer(HeroGiftOffer offer) {
        int memory = offer.memory();
        int warmth = offer.warmth();
        int restraint = offer.restraint();
        int rift = offer.rift();
        int pollution = offer.pollution();
        int suspicion = offer.suspicion();

        int repeatCount = Math.max(1, offer.repeatCount());
        suspicion += Math.max(0, repeatCount - 1) * 6;

        if (offer.recentViolence() >= 5) {
            pollution += 15;
        } else if (offer.recentViolence() >= 3) {
            pollution += 8;
        }
        if (offer.recentVillageHarm() > 0) {
            pollution += 14;
            suspicion += 8;
        }
        if (offer.recentCare() > 0 && "repair".equals(offer.category())) {
            warmth += 10;
        }
        if (offer.isFood() && offer.playerHungerLow()) {
            restraint += 12;
        }
        if (offer.isFood() && offer.nearFire() && offer.isNight()) {
            warmth += 8;
        }
        // 分档怀疑:贵重物换来的是怀疑曲线,不是好感
        int tier = offer.valueTier();
        if (tier == 2) {
            int premiumCount = offer.premiumCount();
            if (premiumCount == 0) {
                suspicion += 6;
            } else if (premiumCount == 1) {
                suspicion += 10;
            } else {
                suspicion += 22;
            }
        } else if (tier == 3) {
            int zenithCount = offer.zenithCount();
            if (zenithCount == 0) {
                suspicion += 10;
            } else if (zenithCount == 1) {
                suspicion += 18;
            } else {
                suspicion += 22;
            }
        }
        int tasteLevel = offer.tasteLevel();
        if (tasteLevel <= -2) {
            suspicion += 4;
        } else if (tasteLevel >= 4) {
            warmth += 1;
            suspicion = Math.max(0, suspicion - 3);
        } else if (tasteLevel >= 3) {
            warmth += 1;
            suspicion = Math.max(0, suspicion - 2);
        }
        return new HeroGiftResult.Score(memory, warmth, restraint, rift, pollution, suspicion);
    }

    static TasteDelta tasteDelta(HeroGiftResult.Score score, int repeatCount) {
        int divisor = repeatCount == 2 ? 2 : 1;
        if (repeatCount >= 3) {
            divisor = 1000;
        }
        return new TasteDelta(
                Math.min(3, Math.max(0, score.memory() / 4)) / divisor,
                Math.min(3, Math.max(0, score.warmth() / 4)) / divisor,
                Math.min(3, Math.max(0, score.restraint() / 4)) / divisor,
                Math.min(3, Math.max(0, score.pollution() / 5)) / divisor);
    }

    static List<BrainSignal> brainSignals(HeroGiftResult.Score score, boolean accepted) {
        List<BrainSignal> signals = new ArrayList<>();
        if (accepted) {
            if (score.memory() >= 6) {
                signals.add(new BrainSignal(BrainSignal.Type.NOSTALGIA, Math.min(0.05, score.memory() * 0.003)));
            }
            if (score.warmth() + score.restraint() >= 8) {
                signals.add(new BrainSignal(BrainSignal.Type.CREATIVITY, Math.min(0.04, (score.warmth() + score.restraint()) * 0.002)));
            }
            if (score.rift() >= 8) {
                signals.add(new BrainSignal(BrainSignal.Type.META, Math.min(0.05, score.rift() * 0.003)));
            }
            if (score.restraint() >= 6) {
                signals.add(new BrainSignal(BrainSignal.Type.MONSTER_INTEREST, Math.min(0.03, score.restraint() * 0.002)));
            }
        }
        if (score.pollution() >= 10) {
            signals.add(new BrainSignal(BrainSignal.Type.VIOLENCE, Math.min(0.08, score.pollution() * 0.003)));
        }
        if (score.suspicion() >= 16) {
            signals.add(new BrainSignal(BrainSignal.Type.ENTROPY, Math.min(0.05, score.suspicion() * 0.002)));
        }
        return signals;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    public static HeroGiftResult evaluate(HeroGiftOffer offer) {
        String kind = offer.kind();
        if ("empty".equals(kind)) {
            int emptyCount = offer.emptyOfferCount();
            if (emptyCount <= 0) {
                return result("empty_first", scoreOffer(offer), false, false, 0, COOLDOWN_EMPTY_FIRST, false,
                        null, null, null);
            }
            if (emptyCount == 1 && offer.playerHungerLow()) {
                return result("empty_return", scoreOffer(offer), true, false, 1, COOLDOWN_ASK, false,
                        null, null, "minecraft:bread");
            }
            return result("empty_repeat", scoreOffer(offer), false, false, 0, COOLDOWN_DEFAULT, false,
                    null, null, null);
        }

        HeroGiftResult.Score score = scoreOffer(offer);
        int repeatCount = Math.max(1, offer.repeatCount());
        HeroGiftResult out;
        if (repeatCount >= 4) {
            out = result("rejected_repeat", score, false, false, 0, COOLDOWN_REPEAT, false, null, null, null);
        } else if ("destructive".equals(offer.category()) && offer.recentVillageHarm() > 0) {
            out = result("judged", score, false, false, 0, COOLDOWN_JUDGED, false, null, null, null);
        } else if (score.pollution() >= 22) {
            out = result("judged", score, false, false, 0, COOLDOWN_JUDGED, false, null, null, null);
        } else if (score.suspicion() >= 20) {
            out = result("rejected_suspicious", score, false, false, 0, COOLDOWN_DEFAULT, false, null, null, null);
        } else if (offer.requestAsk()) {
            // 至珍首件:祂不收,改口索取。物品不消耗。
            out = result("request_asked", score, false, false, 0, COOLDOWN_ASK, false, null, null, null);
        } else if (kind.equals("food") && offer.playerHungerLow()) {
            // 最后一口留给玩家
            out = result("returned_last_bite", score, true, false, 1, COOLDOWN_ACCEPT, false, null, null, null);
        } else if (offer.tasteLevel() <= -3) {
            // 旧账优先于当次评分
            out = result("rejected_taboo", score, false, false, 0, COOLDOWN_TABOO, false, null, null, null);
        } else if (offer.requestMatch()) {
            // 要求达成:说到做到,收得踏实
            out = result("request_fulfilled", score, true, true, 2, COOLDOWN_ACCEPT, true, null, null, null);
        } else {
            if (score.pollution() >= 10) {
                out = result("warning", score, true, true, 0, COOLDOWN_ACCEPT, false, null, null, null);
            } else if (score.positive() >= 14) {
                String code = kind.equals("food") ? "food_accepted" : "remembered";
                out = result(code, score, true, true, 2, COOLDOWN_ACCEPT, true, null, null, null);
            } else {
                String code = kind.equals("food") ? "food_accepted" : "accepted";
                out = result(code, score, true, true, 1, COOLDOWN_ACCEPT, true, null, null, null);
            }
        }

        // 重复赠送衰减:第 2 次信任减半,第 3 次起归零且不再回礼
        TasteDelta taste = tasteDelta(score, repeatCount);
        int trust = out.trustDelta();
        boolean pendingReturn = out.pendingReturn();
        if (repeatCount == 2) {
            trust = trust >= 2 ? 1 : 0;
        } else if (repeatCount >= 3) {
            trust = 0;
            pendingReturn = false;
        }
        return new HeroGiftResult(out.code(), out.accepted(), out.consume(), trust,
                out.cooldownTicks(), pendingReturn, out.score(), taste,
                brainSignals(score, out.accepted()), out.returnItemName());
    }

    private static HeroGiftResult result(String code, HeroGiftResult.Score score,
                                         boolean accepted, boolean consume, int trustDelta,
                                         int cooldownTicks, boolean pendingReturn,
                                         TasteDelta taste, List<BrainSignal> signals,
                                         String returnItemName) {
        return new HeroGiftResult(code, accepted, consume, trustDelta, cooldownTicks, pendingReturn,
                score, taste != null ? taste : tasteDelta(score, 1),
                signals != null ? signals : brainSignals(score, accepted),
                returnItemName);
    }
}