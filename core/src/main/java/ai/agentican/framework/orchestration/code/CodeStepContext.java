package ai.agentican.framework.orchestration.code;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.WorkflowRunStore;

import java.util.concurrent.atomic.AtomicBoolean;

public record CodeStepContext(
        String taskId,
        String stepId,
        AtomicBoolean cancelled,
        WorkflowRunStore stateStore,
        HitlManager hitlManager) { }
