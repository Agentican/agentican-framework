package ai.agentican.framework.agent;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.Message;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.TokenStreamed;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import ai.agentican.framework.event.WorkflowRunStorePersister;
import ai.agentican.framework.llm.LlmResponse;

public class ReActAgentRunner implements AgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgentRunner.class);

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a ReAct agent. Solve the user's task by reasoning step-by-step.

            On every step:
              • THINK: explain your reasoning in plain text before any tool call.
              • ACT:   call a tool when you need information or to make a change.
              • OBSERVE: the tool's result will be returned in the next turn.
              • REPEAT until you can answer.

            When you have enough information, respond with the final answer in plain text and call no tool.

            ROLE
            ----
            %s
            """;

    private final LlmClient llm;

    private final String llmName;
    private final String llmProvider;
    private final String llmModel;

    private final WorkflowRunStore workflowRunStore;
    private final AgenticanEventBus eventBus;

    private final int maxTurns;
    private final Duration timeout;

    ReActAgentRunner(LlmClient llm, String llmName, String llmProvider, String llmModel,
                    WorkflowRunStore workflowRunStore, AgenticanEventBus eventBus,
                    int maxTurns, Duration timeout) {

        this.llm = llm;
        this.llmName = llmName;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.workflowRunStore = workflowRunStore != null ? workflowRunStore : new WorkflowRunStoreMemory();
        // Defensive default: same reason as SmacAgentRunner — ensure the
        // runner's own store reads see what it publishes.
        this.eventBus = eventBus != null ? eventBus : defaultBusFor(this.workflowRunStore);
        this.maxTurns = maxTurns > 0 ? maxTurns : 10;
        this.timeout = timeout;
    }

    private static AgenticanEventBus defaultBusFor(WorkflowRunStore store) {

        var bus = new AgenticanEventBus();
        bus.subscribeFirst(new WorkflowRunStorePersister(store));
        return bus;
    }

    @Override
    public AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName,
                           Duration timeoutOverride, List<String> skills, Map<String, Toolkit> toolkits,
                           StructuredOutput outputSchema) {

        var cancelled = new AtomicBoolean(false);

        return run(agent, task, taskId, stepId, stepName, timeoutOverride, skills, toolkits, outputSchema,
                new InProcessAgentLoopHost(llm, workflowRunStore, eventBus, null, null, cancelled));
    }

    @Override
    public AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName,
                           Duration timeoutOverride, List<String> skills, Map<String, Toolkit> toolkits,
                           StructuredOutput outputSchema, AgentLoopHost host) {

        LOG.info(Logs.AGENT_RUNNING_STEP, agent.name(), 0, distinctToolkitCount(toolkits));
        LOG.debug(Logs.AGENT_RUNNING_STEP_FULL, task);

        ensureTaskLog(host, taskId, stepId, stepName);

        var runId = host.newId();

        host.runStarted(taskId, stepId, runId, agent.name());

        var startTime = host.now();
        // When the host is managed (e.g. Temporal), the surrounding runtime owns the
        // deadline; running our own wall-clock watchdog alongside would race against it.
        var deadline = host.isManaged() ? null : effectiveDeadline(startTime, timeoutOverride);

        var systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
                agent.role() != null && !agent.role().isBlank() ? agent.role() : "(no specific role)");

        var toolDefs = collectToolDefinitions(toolkits);

        var history = new ArrayList<Message>();

        history.add(Message.user(new Message.TextBlock(task)));

        var runTokens = TokenUsage.ZERO;

        for (int turnIndex = 0; turnIndex < maxTurns; turnIndex++) {

            LOG.info(Logs.AGENT_RUNNING_LOOP, turnIndex);

            if (host.isCancelled()) {
                host.runCompleted(taskId, stepId, runId, AgentStatus.CANCELLED, runTokens);
                return result(host, AgentStatus.CANCELLED, taskId, stepId, runId);
            }

            if (deadline != null && host.now().isAfter(deadline)) {
                host.runCompleted(taskId, stepId, runId, AgentStatus.TIMED_OUT, runTokens);
                return result(host, AgentStatus.TIMED_OUT, taskId, stepId, runId);
            }

            var turnId = host.newId();

            host.turnStarted(taskId, runId, turnId, turnIndex);

            var request = new LlmRequest(systemPrompt, task, "", toolDefs, turnIndex, llmName, llmProvider,
                    llmModel, outputSchema, List.copyOf(history));

            LOG.info(Logs.AGENT_SEND_LLM, turnIndex);

            host.messageSent(taskId, turnId, request);

            var response = host.callLlm(request);
            runTokens = runTokens.plus(response.tokenUsage());

            // Host SPI doesn't carry token-level streaming yet; fire the listener once with the full text.
            if (response.text() != null && !response.text().isEmpty())
                eventBus.publish(new TokenStreamed(taskId, turnId, response.text()));

            host.responseReceived(taskId, turnId, response);

            LOG.info(Logs.AGENT_RECD_LLM, turnIndex, response.stopReason());

            history.add(toAssistantMessage(response));

            if (response.stopReason() != StopReason.TOOL_USE || response.toolCalls().isEmpty()) {

                host.turnCompleted(taskId, turnId, turnIndex, response.tokenUsage());
                host.runCompleted(taskId, stepId, runId, AgentStatus.COMPLETED, runTokens);

                return result(host, AgentStatus.COMPLETED, taskId, stepId, runId);
            }

            var toolResults = executeToolCalls(response.toolCalls(), toolkits, host, turnIndex, taskId, turnId);

            history.add(toToolResultMessage(toolResults));

            host.turnCompleted(taskId, turnId, turnIndex, response.tokenUsage());
        }

        host.runCompleted(taskId, stepId, runId, AgentStatus.MAX_TURNS, runTokens);

        return result(host, AgentStatus.MAX_TURNS, taskId, stepId, runId);
    }

    private static Message toAssistantMessage(LlmResponse response) {

        var blocks = new ArrayList<Message.Block>();

        if (response.text() != null && !response.text().isBlank())
            blocks.add(new Message.TextBlock(response.text()));

        for (var call : response.toolCalls())
            blocks.add(new Message.ToolUseBlock(call.id(), call.name(), call.args()));

        return new Message(Message.Role.ASSISTANT, blocks);
    }

    private static Message toToolResultMessage(List<ToolResult> toolResults) {

        var blocks = new ArrayList<Message.Block>();

        for (var tr : toolResults)
            blocks.add(new Message.ToolResultBlock(tr.toolCallId(), tr.content(), tr.cause() != null));

        return new Message(Message.Role.USER, blocks);
    }

    private List<ToolResult> executeToolCalls(List<ToolCall> toolCalls, Map<String, Toolkit> toolkits,
                                              AgentLoopHost host, int turnIndex, String taskId, String turnId) {

        return Parallel.map(toolCalls, toolCall -> executeOne(toolCall, toolkits, host, turnIndex,
                taskId, turnId));
    }

    private ToolResult executeOne(ToolCall toolCall, Map<String, Toolkit> toolkits,
                                   AgentLoopHost host, int turnIndex,
                                   String taskId, String turnId) {

        host.toolCallStarted(taskId, turnId, toolCall);

        var toolCallId = toolCall.id();
        var toolName = toolCall.name();

        if (host.isCancelled()) {

            var result = new ToolResult(toolCallId, toolName, toolError("Execution cancelled"));

            host.toolCallCompleted(taskId, turnId, result);

            return result;
        }

        var toolkit = toolkits.get(toolName);

        if (toolkit == null) {

            var result = new ToolResult(toolCallId, toolName, toolError("No executor found for tool: " + toolName));

            host.toolCallCompleted(taskId, turnId, result);

            return result;
        }

        LOG.info(Logs.AGENT_TOOL_USE, turnIndex, toolName);

        try {

            var output = host.executeTool(toolName, toolCall.args(), toolkit);
            var result = new ToolResult(toolCallId, toolName, output);

            host.toolCallCompleted(taskId, turnId, result);

            return result;
        }
        catch (Exception e) {

            LOG.error("Turn {}: tool {} failed: {}", turnIndex, toolName, e.getMessage());

            var result = new ToolResult(toolCallId, toolName, toolError(e.getMessage()), e);

            host.toolCallCompleted(taskId, turnId, result);

            return result;
        }
    }

    private static String toolError(String message) {

        return "{\"successful\":false,\"error\":\"" + escape(message != null ? message : "unknown error") + "\"}";
    }

    private static String escape(String s) {

        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int distinctToolkitCount(Map<String, Toolkit> toolkits) {

        return (int) toolkits.values().stream().distinct().count();
    }

    private static List<ToolDefinition> collectToolDefinitions(Map<String, Toolkit> toolkits) {

        var allowedNames = toolkits.keySet();

        return toolkits.values().stream()
                .distinct()
                .flatMap(tk -> tk.toolDefinitions().stream())
                .filter(def -> allowedNames.contains(def.name()))
                .toList();
    }

    private Instant effectiveDeadline(Instant start, Duration override) {

        var d = override != null ? override : timeout;

        return d != null ? start.plus(d) : null;
    }

    private void ensureTaskLog(AgentLoopHost host, String taskId, String stepId, String stepName) {

        var taskLog = host.loadRunLog(taskId);

        if (taskLog == null) {

            host.taskStarted(taskId, stepName, null, Map.of());
            host.stepStarted(taskId, stepId, stepName);
        }
    }

    private AgentResult result(AgentLoopHost host, AgentStatus status, String taskId, String stepId, String runId) {

        var taskLog = host.loadRunLog(taskId);
        var stepLog = taskLog != null ? taskLog.findStepById(stepId) : null;
        var runLog = stepLog != null ? stepLog.lastRun() : null;

        return AgentResult.builder()
                .status(status)
                .run(runLog != null ? runLog : new RunLog(runId, 0, null))
                .build();
    }

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private LlmClient llmClient;
        private String llmName;
        private String llmProvider;
        private String llmModel;

        private WorkflowRunStore workflowRunStore;
        private AgenticanEventBus eventBus;

        private int maxIterations;
        private Duration timeout;

        Builder() {}

        public Builder llmClient(LlmClient llmClient)         { this.llmClient = llmClient; return this; }
        public Builder llmName(String llmName)                { this.llmName = llmName; return this; }
        public Builder llmProvider(String llmProvider)        { this.llmProvider = llmProvider; return this; }
        public Builder llmModel(String llmModel)              { this.llmModel = llmModel; return this; }
        public Builder workflowRunStore(WorkflowRunStore store)   { this.workflowRunStore = store; return this; }
        public Builder eventBus(AgenticanEventBus eventBus)                 { this.eventBus = eventBus; return this; }
        public Builder maxIterations(int maxIterations)       { this.maxIterations = maxIterations; return this; }
        public Builder timeout(Duration timeout)              { this.timeout = timeout; return this; }

        public ReActAgentRunner build() {

            if (llmClient == null)
                throw new IllegalStateException("llmClient is required");

            return new ReActAgentRunner(llmClient, llmName, llmProvider, llmModel, workflowRunStore,
                    eventBus, maxIterations, timeout);
        }
    }
}
