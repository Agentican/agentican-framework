package ai.agentican.framework.orchestration.code;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.state.TaskStateStore;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Framework services handed to a {@link CodeStep} at dispatch time. Lets the
 * executor cooperate with cancellation, persist state, and raise HITL
 * checkpoints without coupling to the runtime classes that own those
 * concerns.
 */
public record StepContext(
        String taskId,
        String stepId,
        AtomicBoolean cancelled,
        TaskStateStore stateStore,
        HitlManager hitlManager) { }
