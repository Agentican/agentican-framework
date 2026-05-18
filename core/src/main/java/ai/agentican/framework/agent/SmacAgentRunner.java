package ai.agentican.framework.agent;

import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.tools.hitl.AskQuestionToolkit;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.tools.knowledge.KnowledgeToolkit;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.resume.ResumePlan;
import ai.agentican.framework.orchestration.execution.resume.TurnResumeState;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.orchestration.execution.WorkflowRunner;
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
import ai.agentican.framework.event.TokenStreamed;
import ai.agentican.framework.event.WorkflowRunStorePersister;

public class SmacAgentRunner implements AgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SmacAgentRunner.class);

    private static final Templates TEMPLATES = new Templates();

    private final LlmClient llm;

    private final String llmName;
    private final String llmProvider;
    private final String llmModel;

    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;
    private final WorkflowRunStore workflowRunStore;
    private final AgenticanEventBus eventBus;
    private final SkillRegistry skillRegistry;
    private final Duration timeout;
    private final int maxTurns;

    SmacAgentRunner(LlmClient llm, String llmName, String llmProvider, String llmModel, HitlManager hitlManager,
                    KnowledgeStore knowledgeStore, WorkflowRunStore workflowRunStore, SkillRegistry skillRegistry,
                    int maxTurns, Duration timeout, AgenticanEventBus eventBus) {

        this.llm = llm;

        this.llmName = llmName;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;

        this.workflowRunStore = workflowRunStore;
        this.eventBus = eventBus != null ? eventBus : defaultBusFor(workflowRunStore);
        this.hitlManager = hitlManager;
        this.knowledgeStore = knowledgeStore;
        this.skillRegistry = skillRegistry != null ? skillRegistry : new SkillRegistryMemory();

        this.timeout = timeout;
        this.maxTurns = maxTurns;
    }

    private static AgenticanEventBus defaultBusFor(WorkflowRunStore store) {

        var bus = new AgenticanEventBus();
        bus.subscribeFirst(new WorkflowRunStorePersister(store));
        return bus;
    }

    @Override
    public AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                           List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema) {

        var taskCancelled = new AtomicBoolean(false);

        return run(agent, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema,
                new InProcessAgentLoopHost(llm, workflowRunStore, eventBus, hitlManager, knowledgeStore, taskCancelled));
    }

    @Override
    public AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                           List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                           AgentLoopHost host) {

        return execute(agent, task, taskId, stepId, stepName, host, timeout, skills, toolkits, outputSchema);
    }

    @Override
    public AgentResult resume(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                              List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                              RunLog savedRun, List<ToolResult> hitlToolResults) {

        var taskCancelled = new AtomicBoolean(false);

        return resume(agent, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema, savedRun,
                hitlToolResults,
                new InProcessAgentLoopHost(llm, workflowRunStore, eventBus, hitlManager, knowledgeStore, taskCancelled));
    }

    @Override
    public AgentResult resume(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                              List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                              RunLog savedRun, List<ToolResult> hitlToolResults, AgentLoopHost host) {

        return resumeExecution(agent, task, taskId, stepId, stepName, host, timeout, skills, toolkits,
                savedRun, hitlToolResults, outputSchema);
    }

    private AgentResult execute(Agent agent, String task, String taskId, String stepId, String stepName,
                                AgentLoopHost host, Duration timeout, List<String> skills,
                                Map<String, Toolkit> toolkits, StructuredOutput outputSchema) {

        LOG.info(Logs.AGENT_RUNNING_STEP, agent.name(), skills.size(), toolkits.size());
        LOG.debug(Logs.AGENT_RUNNING_STEP_FULL, task);

        var ctx = buildContext(agent, skills, toolkits, outputSchema);

        ensureTaskLog(host, taskId, stepId, stepName);

        var runId = host.newId();
        var agentName = agent.name();

        host.runStarted(taskId, stepId, runId, agentName);

        var agentResult = loop(task, host.now(), List.of(), ctx, host, taskId, stepId, stepName, runId, 0,
                timeout, outputSchema);

        host.runCompleted(taskId, stepId, runId, agentResult.status(), agentResult.tokenUsage());

        return agentResult;
    }

    @Override
    public AgentResult resumeAfterCrash(Agent agent, String task, String taskId, String stepId, String stepName,
                                        List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                                        RunLog savedRun, AtomicBoolean cancelled, ResumePlan resumePlan) {

        var host = new InProcessAgentLoopHost(llm, workflowRunStore, eventBus, hitlManager, knowledgeStore, cancelled);

        LOG.info("Resuming agent step after crash: agent={}, savedTurns={}, turnState={}",
                agent.name(), savedRun != null ? savedRun.turns().size() : 0,
                resumePlan != null ? resumePlan.turnState() : "<no definition>");

        var ctx = buildContext(agent, skills, toolkits, outputSchema);

        ensureTaskLog(host, taskId, stepId, stepName);

        if (savedRun != null) rehydrateExplicitStores(ctx.localScratchpad(), savedRun);

        if (savedRun != null && !savedRun.turns().isEmpty()) {

            var lastTurn = savedRun.turns().getLast();
            var lastTurnId = lastTurn.id();
            var state = resumePlan != null ? resumePlan.turnState() : TurnResumeState.CLOSED;

            if (state == TurnResumeState.CLOSED && lastTurn.response() != null
                    && lastTurn.response().stopReason() != StopReason.TOOL_USE) {

                LOG.info("Step '{}' was logically complete (last turn stopReason={}); short-circuiting resume",
                        stepName, lastTurn.response().stopReason());

                host.runCompleted(taskId, stepId, savedRun.id(), AgentStatus.COMPLETED, savedRun.tokenUsage());

                return AgentResult.builder().status(AgentStatus.COMPLETED).run(savedRun).build();
            }

            switch (state) {

                case CLOSED, NONE -> {
                }

                case STARTED_NO_MESSAGE, MESSAGE_SENT -> {

                    LOG.info("Abandoning in-flight turn {} in state {}; starting fresh turn", lastTurnId, state);

                    host.turnAbandoned(taskId, lastTurnId);
                }

                case RESPONSE_RECEIVED, TOOLS_PARTIAL, TOOLS_COMPLETE -> {

                    var pending = resumePlan != null ? resumePlan.toolsToExecute() : List.<ToolCall>of();

                    if (!pending.isEmpty()) {
                        LOG.info("Replaying response and executing {} pending tool call(s) for turn {}",
                                pending.size(), lastTurnId);

                        executeToolCalls(pending, ctx.toolkits(), host, lastTurn.index(), taskId, lastTurnId);
                    }

                    host.turnCompleted(taskId, lastTurnId, lastTurn.index(),
                            lastTurn.response() != null ? lastTurn.response().tokenUsage() : TokenUsage.ZERO);
                }
            }
        }

        if (savedRun != null)
            host.runCompleted(taskId, stepId, savedRun.id(), AgentStatus.COMPLETED, savedRun.tokenUsage());

        var runId = host.newId();

        host.runStarted(taskId, stepId, runId, agent.name());

        var agentResult = loop(task, host.now(), List.of(), ctx, host, taskId, stepId, stepName, runId, 0,
                null, outputSchema);

        host.runCompleted(taskId, stepId, runId, agentResult.status(), agentResult.tokenUsage());

        return agentResult;
    }

    private AgentResult resumeExecution(Agent agent, String task, String taskId, String stepId, String stepName,
                                        AgentLoopHost host, Duration timeout, List<String> skills,
                                        Map<String, Toolkit> toolkits, RunLog savedRun,
                                        List<ToolResult> approvalToolResults, StructuredOutput outputSchema) {

        LOG.info("Resuming agent step: agent={}, savedTurns={}", agent.name(), savedRun.turns().size());

        var ctx = buildContext(agent, skills, toolkits, outputSchema);

        ensureTaskLog(host, taskId, stepId, stepName);

        rehydrateExplicitStores(ctx.localScratchpad(), savedRun);

        var lastTurn = savedRun.turns().getLast();
        var lastTurnId = lastTurn.id();

        var executedCallIds = lastTurn.toolResults().stream().map(ToolResult::toolCallId).collect(Collectors.toSet());

        var pendingToolCalls = lastTurn.response().toolCalls().stream()
                .filter(toolCall -> !executedCallIds.contains(toolCall.id()))
                .toList();

        var toolResults = new ArrayList<ToolResult>();

        if (!approvalToolResults.isEmpty()) {

            toolResults.addAll(approvalToolResults);

            for (var approvalToolResult : approvalToolResults)
                host.toolCallCompleted(taskId, lastTurnId, approvalToolResult);
        }
        else {

            for (var pendingToolCall : pendingToolCalls) {

                var toolkit = ctx.toolkits().get(pendingToolCall.name());

                if (toolkit != null) {

                    LOG.info("Executing approved HITL tool: {}", pendingToolCall.name());

                    host.toolCallStarted(taskId, lastTurnId, pendingToolCall);

                    try {

                        var toolOutput = host.executeTool(pendingToolCall.name(), pendingToolCall.args(), toolkit);
                        var toolResult = new ToolResult(pendingToolCall.id(), pendingToolCall.name(), toolOutput);

                        toolResults.add(toolResult);

                        host.toolCallCompleted(taskId, lastTurnId, toolResult);
                    }
                    catch (Exception e) {

                        LOG.error("Approved HITL tool {} failed: {}", pendingToolCall.name(), e.getMessage());

                        var toolResult = new ToolResult(pendingToolCall.id(), pendingToolCall.name(), toolErrorAsJson(e.getMessage()), e);

                        toolResults.add(toolResult);

                        host.toolCallCompleted(taskId, lastTurnId, toolResult);
                    }
                }
            }
        }

        host.turnCompleted(taskId, lastTurnId, lastTurn.index(),
                lastTurn.response() != null ? lastTurn.response().tokenUsage() : TokenUsage.ZERO);

        var runId = host.newId();

        host.runStarted(taskId, stepId, runId, agent.name());

        var agentResult = loop(task, host.now(), toolResults, ctx, host, taskId, stepId, stepName, runId,
                savedRun.turns().size(), timeout, outputSchema);

        host.runCompleted(taskId, stepId, runId, agentResult.status(), agentResult.tokenUsage());

        return agentResult;
    }

    private AgentResult loop(String task, Instant startTime, List<ToolResult> toolResults, SmacAgentContext ctx,
                             AgentLoopHost host, String taskId, String stepId, String stepName, String runId,
                             int turnIndex, Duration timeoutOverride, StructuredOutput outputSchema) {

        var effectiveTimeout = timeoutOverride != null ? timeoutOverride : timeout;
        // When the host is managed (e.g. Temporal), the surrounding runtime owns the
        // deadline (activity / workflow execution timeouts). Running our own watchdog
        // alongside would race against it — defer entirely.
        var deadline = (effectiveTimeout != null && !host.isManaged()) ? startTime.plus(effectiveTimeout) : null;

        while (true) {

            LOG.info(Logs.AGENT_RUNNING_LOOP, turnIndex);

            if (turnIndex >= maxTurns)
                return AgentResult.builder().status(AgentStatus.MAX_TURNS).run(getOrCreateRunLog(host, taskId, stepId)).build();

            if (host.isCancelled())
                return AgentResult.builder().status(AgentStatus.CANCELLED).run(getOrCreateRunLog(host, taskId, stepId)).build();

            if (deadline != null && host.now().isAfter(deadline))
                return AgentResult.builder().status(AgentStatus.TIMED_OUT).run(getOrCreateRunLog(host, taskId, stepId)).build();

            var turnId = host.newId();

            host.turnStarted(taskId, runId, turnId, turnIndex);

            var recalledKnowledge = List.copyOf(ctx.recalledKnowledge().values());

            var progress = buildProgress(host, taskId, stepId);

            var userTask = TEMPLATES.renderTaskBlock(task);
            var userMessage = TEMPLATES.renderUserMessage(turnIndex,
                    ctx.localScratchpad().entries(), ctx.sharedScratchpad().entries(),
                    progress, ctx.knowledgeIndex(), recalledKnowledge);

            var llmRequest = new LlmRequest(ctx.systemPrompt(), userTask, userMessage, ctx.toolDefs(), turnIndex,
                    llmName, llmProvider, llmModel, outputSchema, java.util.List.of());

            LOG.info(Logs.AGENT_SEND_LLM, turnIndex);

            host.messageSent(taskId, turnId, llmRequest);

            var llmResponse = host.callLlm(llmRequest);

            // Host SPI doesn't carry token-level streaming yet; fire the listener once
            // with the full text so non-streaming consumers still see a notification.
            if (llmResponse.text() != null && !llmResponse.text().isEmpty())
                eventBus.publish(new TokenStreamed(taskId, turnId, llmResponse.text()));

            host.responseReceived(taskId, turnId, llmResponse);

            LOG.info(Logs.AGENT_RECD_LLM, turnIndex, llmResponse.stopReason());

            if (llmResponse.stopReason() != StopReason.TOOL_USE || llmResponse.toolCalls().isEmpty()) {

                host.turnCompleted(taskId, turnId, turnIndex, llmResponse.tokenUsage());

                return AgentResult.builder().status(AgentStatus.COMPLETED).run(getOrCreateRunLog(host, taskId, stepId)).build();
            }

            var approvalToolCalls = new ArrayList<ToolCall>();
            var questionToolCalls = new ArrayList<ToolCall>();
            var normalToolCalls = new ArrayList<ToolCall>();

            for (var toolCall : llmResponse.toolCalls()) {

                var toolkit = ctx.toolkits().get(toolCall.name());

                if (hitlManager != null && toolkit != null) {

                    switch (toolkit.hitlType(toolCall.name())) {

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
                    : new ArrayList<>(executeToolCalls(normalToolCalls,
                    ctx.toolkits(), host, turnIndex, taskId, turnId));

            // Knowledge recall — if any tool call was a recall, hydrate the entries into context.
            for (var toolCall : normalToolCalls) {

                if (KnowledgeToolkit.TOOL_NAME.equals(toolCall.name())) {

                    var entryIds = toolCall.args().get("entry_ids");

                    if (entryIds instanceof List<?> list) {

                        for (var entryId : list) {

                            var entry = host.knowledgeEntry(entryId.toString());

                            if (entry != null)
                                ctx.recalledKnowledge().put(entry.id(), entry);
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
                        turnIndex, questionCall.name(), currentToolResults.size());

                var checkpoint = host.createQuestionCheckpoint(question, context, stepName);

                return AgentResult.builder().status(AgentStatus.SUSPENDED).run(getOrCreateRunLog(host, taskId, stepId)).checkpoint(checkpoint).build();
            }

            if (!approvalToolCalls.isEmpty()) {

                var pendingToolCall = approvalToolCalls.getFirst();

                LOG.info("Turn {}: tool '{}' requires approval, suspending after executing {} normal tool(s)",
                        turnIndex, pendingToolCall.name(), currentToolResults.size());

                var checkpoint = host.createToolApprovalCheckpoint(pendingToolCall, stepName);

                return AgentResult.builder().status(AgentStatus.SUSPENDED).run(getOrCreateRunLog(host, taskId, stepId)).checkpoint(checkpoint).build();
            }

            host.turnCompleted(taskId, turnId, turnIndex, llmResponse.tokenUsage());

            toolResults = currentToolResults;

            turnIndex++;
        }
    }

    private void ensureTaskLog(AgentLoopHost host, String taskId, String stepId, String stepName) {

        var taskLog = host.loadRunLog(taskId);

        if (taskLog == null) {

            host.taskStarted(taskId, stepName, null, Map.of());
            host.stepStarted(taskId, stepId, stepName);
        }
    }

    private RunLog getOrCreateRunLog(AgentLoopHost host, String taskId, String stepId) {

        var taskLog = host.loadRunLog(taskId);

        if (taskLog == null)
            return new RunLog(host.newId(), 0, (String) null);

        var stepLog = taskLog.findStepById(stepId);
        var runLog = stepLog != null ? stepLog.lastRun() : null;

        return runLog != null ? runLog : new RunLog(host.newId(), 0, (String) null);
    }

    private List<ToolResult> executeToolCalls(List<ToolCall> toolCalls, Map<String, Toolkit> taskToolkits,
                                              AgentLoopHost host, int iteration, String taskId, String turnId) {

        return Parallel.map(toolCalls, toolCall -> {

            try {

                return executeToolCall(toolCall, taskToolkits, host, iteration, taskId, turnId);
            }
            catch (Exception ex) {

                var toolCallId = toolCall.id();
                var toolName = toolCall.name();

                LOG.error("Turn {}: tool {} failed unexpectedly: {}", iteration, toolName, ex.getMessage());

                var toolResult = new ToolResult(toolCallId, toolName,
                        toolErrorAsJson(ex.getClass().getSimpleName() + ": " + ex.getMessage()), ex);

                host.toolCallCompleted(taskId, turnId, toolResult);

                return toolResult;
            }
        });
    }

    private ToolResult executeToolCall(ToolCall toolCall, Map<String, Toolkit> taskToolkits,
                                       AgentLoopHost host, int iteration, String taskId, String turnId) {

        host.toolCallStarted(taskId, turnId, toolCall);

        var toolCallId = toolCall.id();
        var toolCallArgs = toolCall.args();
        var toolName = toolCall.name();

        if (host.isCancelled()) {

            var toolResult = new ToolResult(toolCallId, toolName, toolErrorAsJson("Execution cancelled"));

            host.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }

        var toolkit = taskToolkits.get(toolName);

        if (toolkit == null) {

            var toolResult = new ToolResult(toolCallId, toolName,
                    toolErrorAsJson("No executor found for tool: " + toolName));

            host.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }

        LOG.info(Logs.AGENT_TOOL_USE, iteration, toolName);

        try {

            var toolOutput = host.executeTool(toolName, toolCallArgs, toolkit);
            var toolResult = new ToolResult(toolCallId, toolName, toolOutput);

            host.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }
        catch (Exception e) {

            LOG.error("Turn {}: tool {} failed: {}", iteration, toolName, e.getMessage());

            var toolResult = new ToolResult(toolCallId, toolName, toolErrorAsJson(e.getMessage()), e);

            host.toolCallCompleted(taskId, turnId, toolResult);

            return toolResult;
        }
    }

    private SmacAgentContext buildContext(Agent agent, List<String> activeSkills, Map<String, Toolkit> toolkits) {

        return buildContext(agent, activeSkills, toolkits, null);
    }

    private SmacAgentContext buildContext(Agent agent, List<String> activeSkills, Map<String, Toolkit> toolkits,
                                          StructuredOutput structuredOutput) {

        var activeSkillConfigs = new ArrayList<SkillConfig>();

        for (var skillId : activeSkills) {

            var skillConfig = skillRegistry.byId(skillId);

            if (skillConfig == null) skillConfig = skillRegistry.byName(skillId);

            if (skillConfig != null)
                activeSkillConfigs.add(skillConfig);
            else
                LOG.warn("Step for agent {} references unknown skill '{}' — dropping", agent.name(), skillId);
        }

        var agentName = agent.name();
        var agentRole = agent.role();

        var systemPrompt = TEMPLATES.renderSystemPrompt(agentName, agentRole, activeSkillConfigs,
                structuredOutput != null);

        var taskToolkits = new LinkedHashMap<>(toolkits);

        var localScratchpad = new ScratchpadToolkit(ScratchpadToolkit.Scope.LOCAL);

        ScratchpadToolkit.LOCAL_TOOL_NAMES.forEach(toolName -> taskToolkits.put(toolName, localScratchpad));

        var sharedFromRunner = WorkflowRunner.sharedScratchpad();

        var sharedScratchpad = sharedFromRunner != null
                ? sharedFromRunner
                : new ScratchpadToolkit(ScratchpadToolkit.Scope.SHARED);

        ScratchpadToolkit.SHARED_TOOL_NAMES.forEach(toolName -> taskToolkits.put(toolName, sharedScratchpad));

        var askQuestionToolkit = new AskQuestionToolkit();

        taskToolkits.put(AskQuestionToolkit.TOOL_NAME, askQuestionToolkit);

        List<KnowledgeEntry> knowledgeIndex = List.of();

        if (knowledgeStore != null) {

            var idx = knowledgeStore.indexed();

            if (!idx.isEmpty()) {

                taskToolkits.put(KnowledgeToolkit.TOOL_NAME, new KnowledgeToolkit(knowledgeStore));
                knowledgeIndex = idx;
            }
        }

        var taskToolDefs = taskToolkits.entrySet().stream()
                .flatMap(e -> e.getValue().toolDefinitions().stream()
                        .filter(td -> td.name().equals(e.getKey())))
                .toList();

        return SmacAgentContext.builder()
                .systemPrompt(systemPrompt)
                .toolkits(taskToolkits)
                .toolDefs(taskToolDefs)
                .localScratchpad(localScratchpad)
                .sharedScratchpad(sharedScratchpad)
                .knowledgeIndex(knowledgeIndex)
                .build();
    }

    private List<AgentToolUse> buildProgress(AgentLoopHost host, String taskId, String stepId) {

        var taskLog = host.loadRunLog(taskId);

        if (taskLog == null) return List.of();

        var stepLog = taskLog.findStepById(stepId);

        if (stepLog == null) return List.of();

        var progress = new ArrayList<AgentToolUse>();

        for (var run : stepLog.runs()) {

            for (var turn : run.turns()) {

                var response = turn.response();

                if (response == null) continue;

                var callsById = new HashMap<String, ToolCall>();

                for (var call : response.toolCalls())
                    callsById.putIfAbsent(call.id(), call);

                for (var result : turn.toolResults()) {

                    var call = callsById.get(result.toolCallId());
                    var input = renderArgs(call != null ? call.args() : Map.of());
                    var output = result.content() != null ? result.content() : "";

                    progress.add(new AgentToolUse(result.toolName(), input, output));
                }
            }
        }

        return progress;
    }

    private static String renderArgs(Map<String, Object> args) {

        try {
            return Json.writeValueAsString(args != null ? args : Map.of());
        }
        catch (Exception _) {
            return "{}";
        }
    }

    private static void rehydrateExplicitStores(ScratchpadToolkit localScratchpad, RunLog savedRun) {

        for (var turn : savedRun.turns()) {

            var response = turn.response();

            if (response == null) continue;

            for (var call : response.toolCalls()) {

                if (!ScratchpadToolkit.STORE.equals(call.name())) continue;

                var args = call.args();
                var id = String.valueOf(args.get("id"));
                var description = String.valueOf(args.get("description"));
                var details = String.valueOf(args.get("details"));

                if (id == null || id.isBlank() || "null".equals(id)) continue;

                localScratchpad.store(id, description, details);
            }
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

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private LlmClient llm;

        private String llmName;
        private String llmProvider;
        private String llmModel;

        private WorkflowRunStore workflowRunStore;
        private AgenticanEventBus eventBus;
        private HitlManager hitlManager;
        private KnowledgeStore knowledgeStore;
        private SkillRegistry skillRegistry;

        private Duration timeout = Duration.ofMinutes(30);
        private int maxTurns = 10;

        Builder() {}

        public Builder llmClient(LlmClient llmClient) { this.llm = llmClient; return this; }
        public Builder llmName(String llmName) { this.llmName = llmName; return this; }
        public Builder llmProvider(String llmProvider) { this.llmProvider = llmProvider; return this; }
        public Builder llmModel(String llmModel) { this.llmModel = llmModel; return this; }

        public Builder workflowRunStore(WorkflowRunStore workflowRunStore) { this.workflowRunStore = workflowRunStore; return this; }
        public Builder eventBus(AgenticanEventBus eventBus) { this.eventBus = eventBus; return this; }
        public Builder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public Builder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public Builder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }

        public Builder maxIterations(int max) { this.maxTurns = max; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        public SmacAgentRunner build() {

            if (llm == null) throw new IllegalStateException("llmClient is required");

            var finalTaskStateStore = workflowRunStore != null ? workflowRunStore : new WorkflowRunStoreMemory();

            return new SmacAgentRunner(llm, llmName, llmProvider, llmModel, hitlManager, knowledgeStore,
                    finalTaskStateStore, skillRegistry, maxTurns, timeout, eventBus);
        }
    }
}
