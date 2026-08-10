package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * 粒子任务：在目标位置释放一次倒转传送门粒子爆发（与 HeroBrain 修复粒子同风格）。瞬时完成。
 */
public final class ParticleTask extends PlannedTask {

    public static final String ID = "particle";

    private BlockPos target;

    public ParticleTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.target = getBlockPos("pos");
        if (this.target == null || !(context.hero().level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int count = Math.max(1, Math.min(64, getInt("count", 10)));
        serverLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                count, 0.3D, 0.3D, 0.3D, 0.05D);
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        return this.target == null ? TaskState.FAILED : TaskState.DONE;
    }

    @Override
    public String progressText() {
        return target == null ? "粒子(无目标)" : "粒子爆发 @ " + posToString(target);
    }
}