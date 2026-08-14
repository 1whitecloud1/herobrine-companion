package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 瞬移任务：把 Hero 直接传送到目标坐标（同维度）。瞬时完成。
 */
public final class TeleportTask extends PlannedTask {

    public static final String ID = "teleport";

    private BlockPos target;

    public TeleportTask(Map<String, String> args) {
        super(ID, args);
    }

    @Override
    public void onStart(AgentTaskContext context) {
        this.target = getBlockPos("pos");
        if (this.target != null) {
            context.hero().teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        }
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        return this.target == null ? TaskState.FAILED : TaskState.DONE;
    }

    @Override
    public String progressText() {
        return target == null ? "瞬移(无目标)" : "传送到 " + posToString(target);
    }
}