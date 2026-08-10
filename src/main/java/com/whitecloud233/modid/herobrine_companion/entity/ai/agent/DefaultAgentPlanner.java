package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 默认规划器：把"意图 + 路由"翻译成"说明性"计划。
 *
 * <p>M1 核心承诺是骨架与可解释性，不新增行为；M2 起规划结果同样不越权——
 * 世界行为交还既有 goal 系统，工具请求由执行器经统一注册表处理，这里只负责产出可读的行动说明。</p>
 */
public final class DefaultAgentPlanner implements AgentPlanner {

    @Override
    public AgentPlan plan(AgentFrame frame, AgentIntentionClassifier.Classification classification) {
        AgentIntention intention = classification.intention();
        String note = switch (intention) {
            case DEFER -> "让位给挑战/战斗既有层，Agent 不干预";
            case ATTEND -> "交由既有陪伴/保护 goal 驱动移动与看护";
            case WATCH -> "保持观察，交由既有 ambient 观察逻辑";
            case REMEDIATE -> "交由既有修复异常 goal（HeroBrain 方块修复 / FixAnomalyGoal）";
            case INVESTIGATE -> "交由既有检查 goal（InspectBlockGoal）";
            case INTERACT -> "交由既有互动/恶作剧 goal";
            case WANDER -> "交由既有乱逛/巡视 goal";
            case COMPUTE -> "路由到统一工具注册表执行（白名单/校验/确认/审计）";
            case IDLE -> "保持静默";
        };
        return new AgentPlan(intention, classification.channel(), classification.reason(), note);
    }
}