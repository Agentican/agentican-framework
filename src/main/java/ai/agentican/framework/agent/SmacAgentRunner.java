package ai.agentican.framework.agent;

import ai.agentican.framework.TaskListener;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.knowledge.KnowledgeToolkit;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.orchestration.execution.TaskRunner;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.tools.scratchpad.ScratchpadToolkit;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Parallel;
import ai.agentican.framework.util.Templates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SmacAgentRunner implements AgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SmacAgentRunner.class);
    private static final Templates TEMPLATES = new Templates();

    private static final TaskListener NO_OP_LISTENER = new TaskListener() {};

    private final LlmClient llm;

    private final String llmName;
    private final String llmProvider;
    private final String llmModel;

    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;
    private final TaskStateStore taskStateStore;
    private final TaskListener taskListener;
    private final Duration timeout;
    private final int maxTurns;

    SmacAgentRunner(LlmClient llm, String llmName, String llmProvider, String llmModel, HitlManager hitlManager,
                    KnowledgeStore knowledgeStore, TaskStateStore taskStateStore, int maxTurns, Duration timeout,
                    TaskListener taskListener) {

        this.llm = llm;

        // may move these to LlmClient
        this.llmName = llmName;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;

        this.taskStateStore = taskStateStore;
        this.taskListener = taskListener != null ? taskListener : NO_OP_LISTENER;
        this.hitlManager = hitlManager;
        this.knowledgeStore = knowledgeStore;

        this.timeout = timeout;
        this.maxTurns = maxTurns;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public AgentRunner withTimeout(Duration timeout) {

        return new SmacAgentRunner(llm, llmName, llmProvider, llmModel, hitlManager, knowledgeStore, taskStateStore,
                maxTurns, timeout, taskListener);
    }

    @Override
    public AgentResult run(Agent agent, String task, List<String> activeSkills, Map<String, Toolkit> toolkits,
                           String taskId, String stepId, String stepName) {

        var taskCancelled = new AtomicBoolean(false);

        return execute(agent, task, activeSkills, toolkits, taskId, stepId, stepName, taskCancelled);
    }

    @Override
    public AgentResult resume(Agent agent, String task, List<String> activeSkills, RunLog savedRun,
                              List<ToolResult> hitlToolResults, Map<String, Toolkit> toolkits, String taskId,
                              String stepId, String stepName) {

        var taskCancelled = new AtomicBoolean(false);

        return resumeExecution(agent, task, activeSkills, savedRun, hitlToolResults, toolkits, taskId, stepId, stepName,
                taskCancelled);
    }

    private AgentResult execute(Agent agent, String task, List<String> activeSkills, Map<String, Toolkit> toolkits,
                                String taskId, String stepId, String stepName, AtomicBoolean cancelled) {

        LOG.info(Logs.AGENT_RUNNING_STEP, agent.name(), activeSkills.size(), toolkits.size());
        LOG.debug(Logs.AGENT_RUNNING_STEP_FULL, task);

        var ctx = buildContext(agent, activeSkills, toolkits);

        ensureTaskLog(taskId, stepId, stepName);

        var runId = Ids.generate();
        var agentName = agent.name();

        taskStateStore.runStarted(taskId, stepId, runId, agentName);

        var agentResult = loop(task, Instant.now(), List.of(), ctx, cancelled, taskId, stepId, stepName, runId, 0);

        taskStateStore.runCompleted(taskId, runId);

        return agentResult;
    }

    private AgentResult resumeExecution(Agent agent, String task, List<String> activeSkills, RunLog savedRun,
                                        List<ToolResult> approvalToolResults, Map<String, Toolkit> toolkits,
                                        String taskId, String stepId, String stepName, AtomicBoolean cancelled) {

        LOG.info("Resuming agent step: agent={}, savedTurns={}", agent.name(), savedRun.turns().size());

        var ctx = buildContext(agent, activeSkills, toolkits);

        ensureTaskLog(taskId, stepId, stepName);

        for (var turn : savedRun.turns())
            storeToolResultsInScratchpad(ctx.scratchpad(), turn.toolResults());

        var lastTurn = savedRun.turns().getLast();
        var lastTurnId = lastTurn.id();

        var executedCallIds = lastTurn.toolResults().stream().map(ToolResult::toolCallId).collect(Collectors.toSet());

        var pendingToolCalls = lastTurn.response().toolCalls().stream()
                .filter(toolCall -> !executedCallIds.contains(toolCall.id()))
                .toList();

        var toolResults = new ArrayList<ToolResult>();

        if (!approvalToolResults.isEmpty()) {

            toolResults.addAll(approvalToolResults);

            for (var approvalToolResult : approvalToolResults) {
                taskStateStore.toolCallCompleted(taskId, lastTurnId, approvalToolResult);
            }
        }
        else {

            for (var pendingToolCall : pendingToolCalls) {

                var toolkit = ctx.toolkits().get(pendingToolCall.toolName());

                if (toolkit != null) {

                    LOG.info("Executing approved HITL tool: {}", pendingToolCall.toolName());

                    taskStateStore.toolCallStarted(taskId, lastTurnId, pendingToolCall);

                    try {

                        var toolOutput = toolkit.execute(pendingToolCall.toolName(), pendingToolCall.args());
                        var toolResult = new ToolResult(pendingToolCall.id(), pendingToolCall.toolName(), toolOutput);

                        toolResults.add(toolResult);

                        taskStateStore.toolCallCompleted(taskId, lastTurnId, toolResult);
                    }
                    catch (Exception e) {

                        LOG.error("Approved HITL tool {} failed: {}", pendingToolCall.toolName(), e.getMessage());

                        var toolResult = new ToolResult(pendingToolCall.id(), pendingToolCall.toolName(), toolErrorAsJson(e.getMessage()), e);

                        toolResults.add(toolResult);

                        taskStateStore.toolCallCompleted(taskId, lastTurnId, toolResult);
                    }
                }
            }
        }

        storeToolResultsInScratchpad(ctx.scratchpad(), toolResults);

        taskStateStore.turnCompleted(taskId, lastTurnId);

        var runId = Ids.generate();

        taskStateStore.runStarted(taskId, stepId, runId, agent.name());

        var agentResult = loop(task, Instant.now(), toolResults, ctx, cancelled, taskId, stepId, stepName, runId,
                savedRun.turns().size());

        taskStateStore.runCompleted(taskId, runId);

        return agentResult;
    }

    private AgentResult loop(String task, Instant startTime, List<ToolResult> toolResults, AgentContext ctx,
                             AtomicBoolean cancelled, String taskId, String stepId, String stepName, String runId,
                             int turnIndex) {

        var deadline = timeout != null ? startTime.plus(timeout) : null;

        while (true) {

            LOG.info(Logs.AGENT_RUNNING_LOOP, turnIndex);

            if (turnIndex >= maxTurns)
                return new AgentResult(AgentStatus.MAX_TURNS, getOrCreateRunLog(taskId, stepId));

            if (cancelled.get())
                return new AgentResult(AgentStatus.CANCELLED, getOrCreateRunLog(taskId, stepId));

            if (deadline != null && Instant.now().isAfter(deadline))
                return new AgentResult(AgentStatus.TIMED_OUT, getOrCreateRunLog(taskId, stepId));

            var turnId = Ids.generate();

            taskStateStore.turnStarted(taskId, runId, turnId);

            var recalledKnowledge = List.copyOf(ctx.recalledKnowledge().values());

            var userMessage = TEMPLATES.renderUserMessage(task, turnIndex, ctx.scratchpad().entries(), toolResults,
                    ctx.knowledgeIndex(), recalledKnowledge);

            var llmRequest = new LlmRequest(ctx.systemPrompt(), userMessage, ctx.toolDefs(), turnIndex, llmName,
                    llmProvider, llmModel);

            LOG.info(Logs.AGENT_SEND_LLM, turnIndex);

            taskStateStore.messageSent(taskId, turnId, llmRequest);

            var llmResponse = llm.sendStreaming(llmRequest, token -> taskListener.onToken(taskId, turnId, token));

            taskStateStore.responseReceived(taskId, turnId, llmResponse);

            LOG.info(Logs.AGENT_RECD_LLM, turnIndex, llmResponse.stopReason());

            if (llmResponse.stopReason() != StopReason.TOOL_USE || llmResponse.toolCalls().isEmpty()) {

                taskStateStore.turnCompleted(taskId, turnId);

                return new AgentResult(AgentStatus.COMPLETED, getOrCreateRunLog(taskId, stepId));
            }

            var approvalToolCalls = new ArrayList<ToolCall>();
            var questionToolCalls = new ArrayList<ToolCall>();
            var normalToolCalls = new ArrayList<ToolCall>();

            for (var toolCall : llmResponse.toolCalls()) {

                var toolkit = ctx.toolkits().get(toolCall.toolName());

                if (hitlManager != null && toolkit != null) {

                    switch (toolkit.hitlType(toolCall.toolName())) {

                        case APPROVAL -> approvalToolCalls.add(toolCall);
                        case QUESTION -> questionToolCalls.add(toolCall);
                        default -> normalToolCalls.add(toolCall);
                    }
                }
                else {

                    normalToolCalls.add(toolCall);
                }
            }

            var currentToolResults = normalToolCalls.isEmpty()
                    ? new ArrayList<ToolResult>()
                    : new ArrayList<>(executeToolCalls(normalToolCalls, ctx.toolkits(), cancelled, turnIndex,
                    taskId, turnId));

            storeToolResultsInScratchpad(ctx.scratchpad(), currentToolResults);

            if (knowledgeStore != null) {

                for (var toolCall : normalToolCalls) {

                    if (KnowledgeToolkit.TOOL_NAME.equals(toolCall.toolName())) {

                        var entryIds = toolCall.args().get("entry_ids");

                        if (entryIds instanceof List<?> list) {

                            for (var entryId : list) {

                                var entry = knowledgeStore.get(entryId.toString());

                                if (entry != null)
                                    ctx.recalledKnowledge().put(entry.id(), entry);
                            }
                        }
                    }
                }
            }

            if (!questionToolCalls.isEmpty()) {

                var questionCall = questionToolCalls.getFirst();
                var question = questionCall.args().getOrDefault("question", "").toString();
                var context = questionCall.args().containsKey("context")
                        ? questionCall.args().get("context").toString() : null;

                LOG.info("Turn {}: tool '{}' asking question, suspending after executing {} normal tool(s)",
                        turnIndex, questionCall.toolName(), currentToolResults.size());

                var checkpoint = hitlManager.createQuestionCheckpoint(question, context, stepName);

                taskStateStore.hitlNotified(taskId, stepId, checkpoint);

                return new AgentResult(AgentStatus.SUSPENDED, getOrCreateRunLog(taskId, stepId), checkpoint);
            }

            if (!approvalToolCalls.isEmpty()) {

                var pendingToolCall = approvalToolCalls.getFirst();

                LOG.info("Turn {}: tool '{}' requires approval, suspending after executing {} normal tool(s)",
                        turnIndex, pendingToolCall.toolName(), currentToolResults.size());

                var checkpoint = hitlManager.createToolApprovalCheckpoint(pendingToolCall, stepName);

                taskStateStore.hitlNotified(taskId, stepId, checkpoint);

                return new AgentResult(AgentStatus.SUSPENDED, getOrCreateRunLog(taskId, stepId), checkpoint);
            }

            taskStateStore.turnCompleted(taskId, turnId);

            toolResults = currentToolResults;

            turnIndex++;
        }
    }

    private void ensureTaskLog(String taskId, String stepId, String stepName) {

        var taskLog = taskStateStore.load(taskId);

        if (taskLog == null) {

            taskStateStore.taskStarted(taskId, stepName, null, Map.of());
            taskStateStore.stepStarted(taskId, stepId, stepName);
        }
    }

    private RunLog getOrCreateRunLog(String taskId, String stepId) {

        var taskLog = taskStateStore.load(taskId);

        if (taskLog == null)
            return new RunLog(Ids.generate(), 0, (String) null);

        var stepLog = taskLog.findStepById(stepId);
        var runLog = stepLog != null ? stepLog.lastRun() : null;

        return runLog != null ? runLog : new RunLog(Ids.generate(), 0, (String) null);
    }

    private List<ToolResult> executeToolCalls(List<ToolCall> toolCalls, Map<String, Toolkit> taskToolkits,
                                              AtomicBoolean cancelled, int iteration, String taskId, String turnId) {

        return Parallel.map(toolCalls, toolCall -> {

            try {

                return executeToolCall(toolCall, taskToolkits, cancelled, iteration, taskId, turnId);
            }
            catch (Exception ex) {

                var toolCallId = toolCall.id();
                var toolName = toolCall.toolName();

                LOG.error("Turn {}: tool {} failed unexpectedly: {}", iteration, toolName, ex.getMessage());

                var toolResult = new ToolResult(toolCallId, toolName,
                        toolErrorAsJson(ex.getClass().getSimpleName() + ": " + ex.getMessage()), ex);

                taskStateStore.toolCallCompleted(taskId, turnId, toolResult);

                return toolResult;
            }
        });
    }

    private ToolResult executeToolCall(ToolCall toolCall, Map<String, Toolkit> taskToolkits,
                                       AtomicBoolean taskCancelled, int iteration, String taskId, String turnId) {

        taskStateStore.toolCallStarted(taskId, turnId, toolCall);

        var toolCallId = toolCall.id();
        var toolCallArgs = toolCall.args();
        var toolName = toolCall.toolName();

        if (taskCancelled.get()) {

            var toolResult = new ToolResult(toolCallId, toolName, toolErrorAsJson("Execution cancelled"));

            taskStateStore.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }

        var toolkit = taskToolkits.get(toolName);

        if (toolkit == null) {

            var toolResult = new ToolResult(toolCallId, toolName,
                    toolErrorAsJson("No executor found for tool: " + toolName));

            taskStateStore.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }

        LOG.info(Logs.AGENT_TOOL_USE, iteration, toolName);

        try {

            var toolOutput = toolkit.execute(toolName, toolCallArgs);
            var toolResult = new ToolResult(toolCallId, toolName, toolOutput);

            taskStateStore.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }
        catch (Exception e) {

            LOG.error("Turn {}: tool {} failed: {}", iteration, toolName, e.getMessage());

            var toolResult = new ToolResult(toolCallId, toolName, toolErrorAsJson(e.getMessage()), e);

            taskStateStore.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }
    }

    private AgentContext buildContext(Agent agent, List<String> activeSkills, Map<String, Toolkit> toolkits) {

        var skillMap = agent.skills().stream()
                .collect(Collectors.toMap(SkillConfig::name, skillConfig -> skillConfig));

        var activeSkillConfigs = activeSkills.stream().map(skillMap::get).filter(Objects::nonNull).toList();

        var agentName = agent.name();
        var agentRole = agent.role();

        var systemPrompt = TEMPLATES.renderSystemPrompt(agentName, agentRole, activeSkillConfigs);

        var taskToolkits = new LinkedHashMap<>(toolkits);

        var scratchpad = TaskRunner.taskScratchpad();

        var taskScratchpad = scratchpad != null ? scratchpad : new ScratchpadToolkit();

        ScratchpadToolkit.TOOL_NAMES.forEach(toolName -> taskToolkits.put(toolName, taskScratchpad));

        List<KnowledgeEntry> knowledgeIndex = List.of();

        if (knowledgeStore != null) {

            taskToolkits.put(KnowledgeToolkit.TOOL_NAME, new KnowledgeToolkit(knowledgeStore));

            knowledgeIndex = knowledgeStore.indexed();
        }

        var taskToolDefs = taskToolkits.values().stream()
                .distinct()
                .flatMap(toolkit -> toolkit.toolDefinitions().stream())
                .toList();

        return new AgentContext(systemPrompt, taskToolkits, taskToolDefs, taskScratchpad, knowledgeIndex);
    }

    private static void storeToolResultsInScratchpad(ScratchpadToolkit scratchpad, List<ToolResult> toolResults) {

        for (var toolResult : toolResults) {

            if (toolResult.content() != null && !toolResult.content().isBlank())
                scratchpad.store("tool-result-" + toolResult.toolName(),
                        "Result from " + toolResult.toolName(), toolResult.content());
        }
    }

    private static String toolErrorAsJson(String errorMessage) {

        try {
            return Json.writeValueAsString(Map.of("error", errorMessage != null ? errorMessage : "Unknown error"));
        }
        catch (Exception _) {
            return "{\"error\":\"Tool execution failed\"}";
        }
    }

    public static class Builder {

        private LlmClient llm;

        private String llmName;
        private String llmProvider;
        private String llmModel;

        private TaskStateStore taskStateStore;
        private TaskListener stepListener;
        private HitlManager hitlManager;
        private KnowledgeStore knowledgeStore;

        private Duration timeout = Duration.ofMinutes(30);
        private int maxTurns = 10;

        Builder() {}

        public Builder llmClient(LlmClient llmClient) { this.llm = llmClient; return this; }
        public Builder llmName(String llmName) { this.llmName = llmName; return this; }
        public Builder llmProvider(String llmProvider) { this.llmProvider = llmProvider; return this; }
        public Builder llmModel(String llmModel) { this.llmModel = llmModel; return this; }

        public Builder taskStateStore(TaskStateStore taskStateStore) { this.taskStateStore = taskStateStore; return this; }
        public Builder taskListener(TaskListener taskListener) { this.stepListener = taskListener; return this; }
        public Builder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public Builder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }

        public Builder maxIterations(int max) { this.maxTurns = max; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        public SmacAgentRunner build() {

            if (llm == null) throw new IllegalStateException("llmClient is required");

            var finalTaskStateStore = taskStateStore != null ? taskStateStore : new MemTaskStateStore();

            return new SmacAgentRunner(llm, llmName, llmProvider, llmModel, hitlManager, knowledgeStore,
                    finalTaskStateStore, maxTurns, timeout, stepListener);
        }
    }
}
