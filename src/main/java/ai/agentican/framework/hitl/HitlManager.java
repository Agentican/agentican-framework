package ai.agentican.framework.hitl;

import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.util.Ids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

public class HitlManager {

    private static final Logger LOG = LoggerFactory.getLogger(HitlManager.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    private final HitlNotifier notifier;
    private final Duration timeout;

    private final ConcurrentHashMap<String, HitlCheckpoint> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<HitlResponse>> pending = new ConcurrentHashMap<>();

    public HitlManager(HitlNotifier notifier) {

        this(notifier, DEFAULT_TIMEOUT);
    }

    public HitlManager(HitlNotifier notifier, Duration timeout) {

        if (notifier == null)
            throw new IllegalArgumentException("HitlNotifier is required");

        this.notifier = notifier;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
    }

    public HitlCheckpoint createToolApprovalCheckpoint(ToolCall call, String stepName) {

        var description = "Tool call: " + call.toolName();
        var toolArgs = call.args().toString();

        return createCheckpoint(Ids.generate(), HitlCheckpointType.TOOL_CALL, stepName, description, toolArgs);
    }

    public HitlCheckpoint createStepApprovalCheckpoint(String stepName, String output) {

        var description = "Step output: " + stepName;

        return createCheckpoint(Ids.generate(), HitlCheckpointType.STEP_OUTPUT, stepName, description, output);
    }

    public HitlCheckpoint createQuestionCheckpoint(String question, String context, String stepName) {

        return createCheckpoint(Ids.generate(), HitlCheckpointType.QUESTION, stepName, question, context);
    }

    public HitlResponse awaitResponse(String checkpointId) {

        var asyncResponse = pending.get(checkpointId);

        if (asyncResponse == null)
            throw new IllegalStateException("No pending checkpoint for ID: " + checkpointId);

        var checkpoint = checkpoints.get(checkpointId);

        if (checkpoint != null)
            notifier.onCheckpoint(this, checkpoint);

        LOG.info("Awaiting HITL response for checkpoint '{}' (timeout={})", checkpointId, timeout);

        try {

            return asyncResponse.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        }
        catch (CompletionException e) {

            if (e.getCause() instanceof TimeoutException) {

                checkpoints.remove(checkpointId);

                LOG.warn("HITL checkpoint '{}' timed out after {}", checkpointId, timeout);

                return HitlResponse.reject("HITL approval timed out after " + timeout);
            }

            throw e;
        }
    }

    public void respond(String checkpointId, HitlResponse response) {

        var asyncResponse = pending.get(checkpointId);

        checkpoints.remove(checkpointId);

        if (asyncResponse == null || asyncResponse.isDone()) {

            LOG.warn("No pending checkpoint found for ID: {}", checkpointId);

            return;
        }

        LOG.info("Checkpoint '{}' resolved: approved={}", checkpointId, response.approved());

        asyncResponse.complete(response);
    }

    public void cancel(String checkpointId) {

        var asyncResponse = pending.get(checkpointId);

        checkpoints.remove(checkpointId);

        if (asyncResponse != null && !asyncResponse.isDone()) {

            LOG.info("Checkpoint '{}' cancelled", checkpointId);

            asyncResponse.complete(HitlResponse.reject("Cancelled"));
        }
    }

    public Map<String, HitlCheckpoint> pendingCheckpoints() {

        return Collections.unmodifiableMap(checkpoints);
    }

    public void rehydrate(HitlCheckpoint checkpoint) {

        if (checkpoint == null || checkpoint.id() == null) return;

        checkpoints.putIfAbsent(checkpoint.id(), checkpoint);
        pending.computeIfAbsent(checkpoint.id(), k -> new CompletableFuture<>());

        LOG.info("Checkpoint '{}' rehydrated after restart", checkpoint.id());
    }

    public boolean hasPending(String checkpointId) {

        var future = pending.get(checkpointId);
        return future != null && !future.isDone();
    }

    private HitlCheckpoint createCheckpoint(String id, HitlCheckpointType type, String stepName,
                                             String description, String content) {

        var checkpoint = new HitlCheckpoint(id, type, stepName, description, content);

        checkpoints.put(id, checkpoint);
        pending.put(id, new CompletableFuture<>());

        LOG.info("Checkpoint '{}' created: {}", id, description);

        return checkpoint;
    }
}
