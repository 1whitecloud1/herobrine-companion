package com.whitecloud233.herobrine_companion.entity.companion;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class CompanionModeService {
    private CompanionModeService() {
    }

    /** requester 必须来自服务端收包上下文或内部 Agent 上下文，不能取自客户端载荷。 */
    public static CompanionModePolicy.Result changeMode(UUID requester, HeroEntity hero, boolean desiredMode) {
        if (hero == null || !(hero.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return CompanionModePolicy.Result.UNAVAILABLE;
        }

        boolean restricted = hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || ChallengeSession.isHeroInChallenge(hero)
                || level.dimension() == ModStructures.END_RING_DIMENSION_KEY
                || hero.getHeroBrain().getState() == SimpleNeuralNetwork.MindState.JUDGE;
        CompanionModePolicy.Result result = CompanionModePolicy.evaluate(requester,
                new CompanionModePolicy.State(hero.getOwnerUUID(), hero.getTrustLevel(), hero.isCompanionMode(),
                        hero.isAlive() && !hero.isRemoved(), restricted,
                        desiredMode && hasConflictingHero(level, requester, hero)), desiredMode);
        if (result != CompanionModePolicy.Result.CHANGED) return result;

        if (desiredMode && hero.getOwnerUUID() == null) {
            hero.setOwnerUUID(requester);
            HeroWorldData.get(level).setActiveHeroUUID(requester, hero.getUUID());
        }
        hero.setCompanionMode(desiredMode);
        return result;
    }

    private static boolean hasConflictingHero(ServerLevel level, UUID requester, HeroEntity hero) {
        if (requester == null) return false;
        HeroEntity active = HeroLifecycleHandler.findActiveHero(level, requester);
        if (active != null && active != hero) return true;
        if (hero.getOwnerUUID() != null) return false;

        // 新实体可能尚未完成首个 tick 的档案登记，不能只检查 activeHeroUUID。
        for (HeroEntity candidate : HeroBrain.ACTIVE_HEROES) {
            if (candidate != hero && candidate.getServer() == level.getServer()
                    && candidate.isAlive() && !candidate.isRemoved()
                    && requester.equals(candidate.getOwnerUUID())) {
                return true;
            }
        }
        return false;
    }
}
