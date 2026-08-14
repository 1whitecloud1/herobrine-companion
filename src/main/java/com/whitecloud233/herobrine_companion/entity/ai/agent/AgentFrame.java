package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;
import java.util.UUID;

/**
 * 单次 Agent 循环开始时的实体状态快照。不可变、一次性捕获。
 *
 * <p>设计意图（低耦合 + 依赖倒置）：后续所有阶段（传感器 / 分类 / 规划 / 执行）都只读取本快照，
 * 不直接触碰 {@link HeroEntity} 具体类。这样核心循环与"Hero 实体"解耦，
 * 既能单元测试，也能原样移植到其它目标仓库。</p>
 *
 * <p>单一职责：只在构建时读取一次实体状态，之后完全只读。</p>
 */
public final class AgentFrame {

    private final long gameTime;
    private final MindState mindState;
    private final UUID ownerUuid;
    private final double ownerDistanceSqr;
    private final int nearbyThreats;
    private final boolean challengeActive;
    private final boolean battleActive;
    private final boolean companionMode;
    private final BlockPos position;
    private final boolean debugFlag;
    // P5 感知扩展
    private final UUID targetUuid;
    private final String targetType;
    private final String mainHandItemPath;
    private final float ownerHealth;
    private final boolean isRaining;

    private AgentFrame(long gameTime, MindState mindState, UUID ownerUuid, double ownerDistanceSqr,
                       int nearbyThreats, boolean challengeActive, boolean battleActive,
                       boolean companionMode, BlockPos position, boolean debugFlag,
                       UUID targetUuid, String targetType, String mainHandItemPath,
                       float ownerHealth, boolean isRaining) {
        this.gameTime = gameTime;
        this.mindState = mindState;
        this.ownerUuid = ownerUuid;
        this.ownerDistanceSqr = ownerDistanceSqr;
        this.nearbyThreats = nearbyThreats;
        this.challengeActive = challengeActive;
        this.battleActive = battleActive;
        this.companionMode = companionMode;
        this.position = position;
        this.debugFlag = debugFlag;
        this.targetUuid = targetUuid;
        this.targetType = targetType;
        this.mainHandItemPath = mainHandItemPath;
        this.ownerHealth = ownerHealth;
        this.isRaining = isRaining;
    }

    /** 从实体构建一份快照。感知距离固定为 24 格，扫描只在快照创建处发生一次。 */
    public static AgentFrame capture(HeroEntity hero) {
        long gameTime = hero.level().getGameTime();

        UUID ownerUuid = hero.getOwnerUUID();
        double ownerDistSqr = -1.0D;
        float ownerHealth = -1.0F;
        if (ownerUuid != null && hero.level().getPlayerByUUID(ownerUuid) instanceof ServerPlayer owner) {
            ownerDistSqr = owner.distanceToSqr(hero);
            ownerHealth = owner.getHealth();
        }

        int threats;
        if (hero.level().isClientSide) {
            threats = 0;
        } else {
            List<Monster> nearby = hero.level().getEntitiesOfClass(
                    Monster.class, hero.getBoundingBox().inflate(24.0D));
            threats = nearby.size();
        }

        // P5：目标实体 + 主手武器 + 天气（均为轻量读取，无额外扫描）。
        UUID targetUuid = null;
        String targetType = "";
        if (hero.getTarget() != null && hero.getTarget().isAlive()) {
            targetUuid = hero.getTarget().getUUID();
            targetType = hero.getTarget().getType().getDescriptionId();
        }
        net.minecraft.resources.ResourceLocation itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hero.getMainHandItem().getItem());
        String mainHandItemPath = itemKey == null ? "" : itemKey.toString();
        boolean isRaining = hero.level().isRaining();

        // 挑战态统一走实体自身的运行期标记，与挑战状态机保持一致。
        return new AgentFrame(
                gameTime,
                hero.getMindState(),
                ownerUuid,
                ownerDistSqr,
                threats,
                hero.isChallengeActiveState(),
                hero.isBattleModeActive(),
                hero.isCompanionMode(),
                hero.blockPosition(),
                hero.isDebugAnim(),
                targetUuid,
                targetType,
                mainHandItemPath,
                ownerHealth,
                isRaining);
    }

    public long gameTime() { return gameTime; }
    public MindState mindState() { return mindState; }
    public UUID ownerUuid() { return ownerUuid; }
    /** 与主人的距离平方；无主人时为 -1。 */
    public double ownerDistanceSqr() { return ownerDistanceSqr; }
    /** 24 格内敌对怪物数量。 */
    public int nearbyThreats() { return nearbyThreats; }
    public boolean isChallengeActive() { return challengeActive; }
    public boolean isBattleActive() { return battleActive; }
    public boolean isCompanionMode() { return companionMode; }
    public BlockPos position() { return position; }
    public boolean isDebugFlag() { return debugFlag; }

    /** 当前目标实体 UUID（无目标为 null）。 */
    public UUID targetUuid() { return targetUuid; }
    /** 当前目标实体类型描述键（如 entity.minecraft.zombie；无目标为空串）。 */
    public String targetType() { return targetType; }
    /** 主手物品注册名（如 efn:yamato_dmc；空手/未注册为空串）。 */
    public String mainHandItemPath() { return mainHandItemPath; }
    /** 主人生命值；无主/不在线为 -1。 */
    public float ownerHealth() { return ownerHealth; }
    /** 是否正在下雨。 */
    public boolean isRaining() { return isRaining; }

    /** 是否有主人且在一定范围内（用于"陪伴/看护"意图的粗判）。 */
    public boolean isOwnerWithin(double radiusSqr) {
        return ownerUuid != null && ownerDistanceSqr >= 0.0D && ownerDistanceSqr <= radiusSqr;
    }
}