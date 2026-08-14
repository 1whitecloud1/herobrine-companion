package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 播报任务：向主人发送一条服务端消息（聊天或 actionbar）。无主人时静默完成。
 *
 * <p>{@link #send} 是共享的播报入口，供本任务与动作任务（{@code RepairAreaTask} /
 * {@code InspectAreaTask}）在"确实做了某事"时调用——避免固定尾随汇报造成刷屏。</p>
 */
public final class ReportTask extends PlannedTask {

    public static final String ID = "report";

    public ReportTask(Map<String, String> args) {
        super(ID, args);
    }

    /** 向主人播报一条消息（聊天或 actionbar）；无主人静默。 */
    public static void send(AgentTaskContext context, String text, boolean actionbar) {
        if (context == null) {
            return;
        }
        HeroEntity hero = context.hero();
        if (text == null || text.isBlank() || hero.getOwnerUUID() == null) {
            return;
        }
        if (hero.level().getPlayerByUUID(hero.getOwnerUUID()) instanceof ServerPlayer owner) {
            owner.displayClientMessage(Component.translatable("message.herobrine_companion.agent.task_progress", text), actionbar);
        }
    }

    @Override
    public void onStart(AgentTaskContext context) {
        boolean actionbar = "actionbar".equalsIgnoreCase(getString("mode"));
        send(context, getString("text"), actionbar);
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        return TaskState.DONE;
    }

    @Override
    public String progressText() {
        String text = getString("text");
        return text.isBlank() ? "播报(空)" : "播报: " + (text.length() <= 24 ? text : text.substring(0, 21) + "...");
    }
}
