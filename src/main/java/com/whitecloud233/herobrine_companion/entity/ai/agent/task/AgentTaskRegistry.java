package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 任务类型注册表：任务 id → 工厂。用于存档反序列化（按 id 重建任务）与任务清单。
 *
 * <p>开闭原则：新增任务类型只需 {@link #register}，队列本体无需改动。</p>
 */
public final class AgentTaskRegistry {

    private final Map<String, Function<Map<String, String>, PlannedTask>> factories = new LinkedHashMap<>();

    /** 注册默认任务集（M3 内置安全任务 + P4 真实行为任务）。 */
    public void registerDefaults() {
        register(NavigateTask.ID, NavigateTask::new);
        register(TeleportTask.ID, TeleportTask::new);
        register(ParticleTask.ID, ParticleTask::new);
        register(ReportTask.ID, ReportTask::new);
        register(RepairAreaTask.ID, RepairAreaTask::new);
        register(InspectAreaTask.ID, InspectAreaTask::new);
    }

    public void register(String id, Function<Map<String, String>, PlannedTask> factory) {
        if (id == null || id.isBlank() || factory == null) {
            throw new IllegalArgumentException("Task registration requires a non-blank id and a factory");
        }
        if (factories.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate task id: " + id);
        }
    }

    /** 按 id 重建任务；未知 id 返回 null。 */
    public PlannedTask create(String id, Map<String, String> args) {
        if (id == null) {
            return null;
        }
        Function<Map<String, String>, PlannedTask> factory = factories.get(id);
        return factory == null ? null : factory.apply(args == null ? Map.of() : args);
    }

    public Set<String> knownIds() {
        return Collections.unmodifiableSet(factories.keySet());
    }
}