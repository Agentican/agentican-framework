package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.PlanRegistry;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.state.TurnLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Json;
import ai.agentican.quarkus.store.jpa.entity.PlanEntity;
import ai.agentican.quarkus.store.jpa.entity.RunEntity;
import ai.agentican.quarkus.store.jpa.entity.TaskEntity;
import ai.agentican.quarkus.store.jpa.entity.TaskStepEntity;
import ai.agentican.quarkus.store.jpa.entity.ToolResultEntity;
import ai.agentican.quarkus.store.jpa.entity.TurnEntity;

import com.fasterxml.jackson.core.type.TypeReference;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaTaskStateStore implements TaskStateStore {

    private static final Logger LOG = LoggerFactory.getLogger(JpaTaskStateStore.class);
    private static final TypeReference<Map<String, String>> PARAMS_TYPE = new TypeReference<>() {};

    @Inject
    PlanRegistry planRegistry;

    @Override
    @Transactional
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params) {

        taskStarted(taskId, taskName, plan, params, null, null, 0);
    }

    @Override
    @Transactional
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params,
                            String parentTaskId, String parentStepId, int iterationIndex) {

        if (plan != null && plan.id() != null && !plan.id().isBlank()
                && PlanEntity.findById(plan.id()) == null) {

            var p = new PlanEntity();
            p.id = plan.id();
            p.name = plan.name() != null ? plan.name() : plan.id();
            p.description = plan.description();
            p.definitionJson = writeJson(plan);
            p.createdAt = Instant.now();
            p.persist();
        }

        var t = new TaskEntity();
        t.id = taskId;
        t.planId = plan != null ? plan.id() : null;
        t.taskName = taskName;
        t.parentTaskId = parentTaskId;
        t.parentStepId = parentStepId;
        t.iterationIndex = iterationIndex;
        t.status = null;
        t.paramsJson = writeJson(params);
        t.planSnapshotJson = plan != null ? writeJson(plan) : null;
        t.createdAt = Instant.now();
        t.persist();
    }

    @Override
    @Transactional
    public void taskCompleted(String taskId, TaskStatus status) {

        var t = (TaskEntity) TaskEntity.findById(taskId);
        if (t == null) return;

        t.status = status != null ? status.name() : null;
        t.completedAt = Instant.now();
    }

    @Override
    @Transactional
    public void stepStarted(String taskId, String stepId, String stepName) {

        var s = new TaskStepEntity();
        s.id = stepId;
        s.taskId = taskId;
        s.stepName = stepName;
        s.status = null;
        s.createdAt = Instant.now();
        s.persist();
    }

    @Override
    @Transactional
    public void stepCompleted(String taskId, String stepId, TaskStatus status, String output) {

        var s = (TaskStepEntity) TaskStepEntity.findById(stepId);
        if (s == null) return;

        s.status = status != null ? status.name() : null;
        s.output = output;
        s.completedAt = Instant.now();
    }

    @Override
    @Transactional
    public void stepTokenUsageAggregated(String taskId, String stepId, TokenUsage usage) {

        var s = (TaskStepEntity) TaskStepEntity.findById(stepId);
        if (s == null || usage == null) return;

        s.aggregateTokenUsageJson = writeJson(usage);
    }

    @Override
    @Transactional
    public void runStarted(String taskId, String stepId, String runId, String agentName) {

        var r = new RunEntity();
        r.id = runId;
        r.taskStepId = stepId;
        r.agentName = agentName;
        r.runIndex = (int) RunEntity.count("taskStepId", stepId);
        r.createdAt = Instant.now();
        r.persist();
    }

    @Override
    @Transactional
    public void runCompleted(String taskId, String runId) {

        var r = (RunEntity) RunEntity.findById(runId);
        if (r == null) return;

        r.completedAt = Instant.now();
    }

    @Override
    @Transactional
    public void turnStarted(String taskId, String runId, String turnId) {

        var t = new TurnEntity();
        t.id = turnId;
        t.runId = runId;
        t.turnIndex = (int) TurnEntity.count("runId", runId);
        t.state = "STARTED";
        t.createdAt = Instant.now();
        t.persist();
    }

    @Override
    @Transactional
    public void turnCompleted(String taskId, String turnId) {

        var t = (TurnEntity) TurnEntity.findById(turnId);
        if (t == null) return;

        t.completedAt = Instant.now();
        t.state = "COMPLETED";
    }

    @Override
    @Transactional
    public void turnAbandoned(String taskId, String turnId) {

        var t = (TurnEntity) TurnEntity.findById(turnId);
        if (t == null) return;

        t.completedAt = Instant.now();
        t.state = "ABANDONED";
    }

    @Override
    @Transactional
    public void messageSent(String taskId, String turnId, LlmRequest request) {

        var t = (TurnEntity) TurnEntity.findById(turnId);
        if (t == null) return;

        t.requestJson = writeJson(request);
        t.messageId = Ids.generate();
    }

    @Override
    @Transactional
    public void responseReceived(String taskId, String turnId, LlmResponse response) {

        var t = (TurnEntity) TurnEntity.findById(turnId);
        if (t == null) return;

        t.responseJson = writeJson(response);
        t.responseId = Ids.generate();
    }

    @Override
    @Transactional
    public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {

        if (toolCall == null) return;

        var existing = (ToolResultEntity) ToolResultEntity
                .find("turnId = ?1 AND toolCallId = ?2", turnId, toolCall.id()).firstResult();

        if (existing != null) return;

        var tr = new ToolResultEntity();
        tr.id = Ids.generate();
        tr.turnId = turnId;
        tr.toolCallId = toolCall.id();
        tr.toolName = toolCall.toolName();
        tr.content = null;
        tr.isError = false;
        tr.state = "STARTED";
        tr.createdAt = Instant.now();
        tr.persist();
    }

    @Override
    @Transactional
    public void toolCallCompleted(String taskId, String turnId, ToolResult toolResult) {

        if (toolResult == null) return;

        var existing = (ToolResultEntity) ToolResultEntity
                .find("turnId = ?1 AND toolCallId = ?2", turnId, toolResult.toolCallId()).firstResult();

        var tr = existing != null ? existing : new ToolResultEntity();

        if (existing == null) {
            tr.id = Ids.generate();
            tr.turnId = turnId;
            tr.toolCallId = toolResult.toolCallId();
            tr.createdAt = Instant.now();
        }

        tr.toolName = toolResult.toolName();
        tr.content = toolResult.content();
        tr.isError = toolResult.isError();
        tr.state = toolResult.isError() ? "FAILED" : "COMPLETED";
        tr.persist();
    }

    @Override
    @Transactional
    public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {

        var s = (TaskStepEntity) TaskStepEntity.findById(stepId);
        if (s == null || checkpoint == null) return;

        s.checkpointJson = writeJson(checkpoint);
    }

    @Override
    @Transactional
    public void hitlResponded(String taskId, String stepId, HitlResponse response) {

        var s = (TaskStepEntity) TaskStepEntity.findById(stepId);
        if (s == null || response == null) return;

        s.hitlResponseJson = writeJson(response);
    }

    @Override
    @Transactional
    public void branchPathChosen(String taskId, String stepId, String pathName) {

        var s = (TaskStepEntity) TaskStepEntity.findById(stepId);
        if (s == null) return;

        s.branchChosenPath = pathName;
    }

    @Override
    @Transactional
    public TaskLog load(String taskId) {

        var task = (TaskEntity) TaskEntity.findById(taskId);
        if (task == null) return null;

        return reconstruct(task);
    }

    @Override
    @Transactional
    public List<TaskLog> list() {

        List<TaskEntity> tasks = TaskEntity.listAll();
        return tasks.stream().map(this::reconstruct).toList();
    }

    @Override
    @Transactional
    public List<TaskLog> listInProgress() {

        List<TaskEntity> tasks = TaskEntity.list("status IS NULL");
        return tasks.stream().map(this::reconstruct).toList();
    }

    private TaskLog reconstruct(TaskEntity task) {

        Map<String, String> params = readJsonParams(task.paramsJson);

        Plan plan = null;
        boolean snapshotCorrupt = false;

        if (task.planSnapshotJson != null && !task.planSnapshotJson.isBlank()) {
            try { plan = Json.readValue(task.planSnapshotJson, Plan.class); }
            catch (Exception ex) {
                LOG.warn("Failed to deserialize plan snapshot for task {}: {}", task.id, ex.getMessage());
                snapshotCorrupt = true;
            }
        }

        if (plan == null && task.planId != null) plan = planRegistry.getById(task.planId);
        var taskLog = new TaskLog(task.id, task.taskName, plan, params,
                task.parentTaskId, task.parentStepId, task.iterationIndex, task.createdAt);
        taskLog.setPlanSnapshotCorrupt(snapshotCorrupt);

        if (task.status != null) taskLog.setStatus(parseStatus(task.status));
        if (task.completedAt != null) taskLog.setCompletedAt(task.completedAt);

        List<TaskStepEntity> stepRows = TaskStepEntity.list("taskId", task.id);

        stepRows = stepRows.stream()
                .sorted((a, b) -> a.createdAt.compareTo(b.createdAt))
                .toList();

        for (var s : stepRows) {
            taskLog.addStep(s.stepName, reconstructStep(s));
        }

        return taskLog;
    }

    private StepLog reconstructStep(TaskStepEntity s) {

        var step = new StepLog(s.id, s.stepName, s.createdAt);

        if (s.status != null) step.setStatus(parseStatus(s.status));
        if (s.output != null) step.setOutput(s.output);
        if (s.completedAt != null) step.setCompletedAt(s.completedAt);
        if (s.aggregateTokenUsageJson != null)
            step.setAggregateTokenUsage(readJson(s.aggregateTokenUsageJson, TokenUsage.class));
        if (s.checkpointJson != null)
            step.setCheckpoint(readJson(s.checkpointJson, HitlCheckpoint.class));
        if (s.branchChosenPath != null)
            step.setBranchChosenPath(s.branchChosenPath);
        if (s.hitlResponseJson != null)
            step.setHitlResponse(readJson(s.hitlResponseJson, HitlResponse.class));

        List<RunEntity> runRows = RunEntity.list("taskStepId", s.id);
        runRows = runRows.stream()
                .sorted((a, b) -> Integer.compare(a.runIndex, b.runIndex))
                .toList();

        for (var r : runRows) step.addRun(reconstructRun(r));

        return step;
    }

    private RunLog reconstructRun(RunEntity r) {

        var run = new RunLog(r.id, r.runIndex, r.agentName);

        List<TurnEntity> turnRows = TurnEntity.list("runId", r.id);
        turnRows = turnRows.stream()
                .sorted((a, b) -> Integer.compare(a.turnIndex, b.turnIndex))
                .toList();

        for (var t : turnRows) run.addTurn(reconstructTurn(t));

        return run;
    }

    private TurnLog reconstructTurn(TurnEntity t) {

        var request = t.requestJson != null ? readJson(t.requestJson, LlmRequest.class) : null;
        var response = t.responseJson != null ? readJson(t.responseJson, LlmResponse.class) : null;

        List<ToolResultEntity> resultRows = ToolResultEntity.list("turnId", t.id);
        List<ToolResult> toolResults = new ArrayList<>();

        for (var tr : resultRows) {
            Throwable cause = tr.isError ? new RuntimeException("Tool call failed (persisted)") : null;
            var trState = parseToolResultState(tr.state, cause);
            toolResults.add(new ToolResult(tr.toolCallId, tr.toolName,
                    tr.content != null ? tr.content : "",
                    cause, trState));
        }

        var turnState = parseTurnState(t.state, t.completedAt);

        return new TurnLog(t.id, t.turnIndex, t.messageId, request, t.responseId, response,
                toolResults, t.createdAt, t.completedAt, turnState);
    }

    private static ToolResult.State parseToolResultState(String raw, Throwable cause) {

        if (raw == null) return cause != null ? ToolResult.State.FAILED : ToolResult.State.COMPLETED;
        try { return ToolResult.State.valueOf(raw); }
        catch (IllegalArgumentException ex) {
            return cause != null ? ToolResult.State.FAILED : ToolResult.State.COMPLETED;
        }
    }

    private static TurnLog.State parseTurnState(String raw, Instant completedAt) {

        if (raw == null) return completedAt != null ? TurnLog.State.COMPLETED : TurnLog.State.STARTED;
        try { return TurnLog.State.valueOf(raw); }
        catch (IllegalArgumentException ex) {
            return completedAt != null ? TurnLog.State.COMPLETED : TurnLog.State.STARTED;
        }
    }

    private static String writeJson(Object value) {

        if (value == null) return null;
        try { return Json.writeValueAsString(value); }
        catch (Exception e) {
            LOG.warn("Failed to serialize {}: {}", value.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static <T> T readJson(String json, Class<T> type) {

        if (json == null || json.isBlank()) return null;
        try { return Json.mapper().readValue(json, type); }
        catch (Exception e) {
            LOG.warn("Failed to deserialize {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static Map<String, String> readJsonParams(String json) {

        if (json == null || json.isBlank()) return Map.of();
        try { return Json.mapper().readValue(json, PARAMS_TYPE); }
        catch (Exception e) {
            LOG.warn("Failed to deserialize task params: {}", e.getMessage());
            return Map.of();
        }
    }

    private static TaskStatus parseStatus(String s) {

        if (s == null) return null;
        try { return TaskStatus.valueOf(s); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
