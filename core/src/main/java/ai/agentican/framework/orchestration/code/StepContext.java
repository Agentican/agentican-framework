package ai.agentican.framework.orchestration.code;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.TaskStateStore;

import java.util.concurrent.atomic.AtomicBoolean;

public record StepContext(
        String taskId,
        String stepId,
        AtomicBoolean cancelled,
        TaskStateStore stateStore,
        HitlManager hitlManager) { }
