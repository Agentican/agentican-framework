package ai.agentican.framework.agent;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.Message;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.WorkflowRunListener;
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

public class ReActAgentRunner implements AgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgentRunner.class);
    private static final WorkflowRunListener NO_OP_LISTENER = new WorkflowRunListener() {};

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
    private final WorkflowRunListener workflowRunListener;

    private final int maxTurns;
    private final Duration timeout;

    ReActAgentRunner(LlmClient llm, String llmName, String llmProvider, String llmModel,
                    WorkflowRunStore workflowRunStore, WorkflowRunListener workflowRunListener,
                    int maxTurns, Duration timeout) {

        this.llm = llm;
        this.llmName = llmName;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.workflowRunStore = workflowRunStore != null ? workflowRunStore : new WorkflowRunStoreMemory();
        this.workflowRunListener = workflowRunListener != null ? workflowRunListener : NO_OP_LISTENER;
        this.maxTurns = maxTurns > 0 ? maxTurns : 10;
        this.timeout = timeout;
    }

    @Override
    public AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName,
                           Duration timeoutOverride, List<String> skills, Map<String, Toolkit> toolkits,
                           StructuredOutput outputSchema) {

        LOG.info(Logs.AGENT_RUNNING_STEP, agent.name(), 0, distinctToolkitCount(toolkits));
        LOG.debug(Logs.AGENT_RUNNING_STEP_FULL, task);

        ensureTaskLog(taskId, stepId, stepName);

        var runId = Ids.generate();

        workflowRunStore.runStarted(taskId, stepId, runId, agent.name());

        var startTime = Instant.now();
        var deadline = effectiveDeadline(startTime, timeoutOverride);

        var cancelled = new AtomicBoolean(false);

        var systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
                agent.role() != null && !agent.role().isBlank() ? agent.role() : "(no specific role)");

        var toolDefs = collectToolDefinitions(toolkits);

        var history = new ArrayList<Message>();

        history.add(Message.user(new Message.TextBlock(task)));

        for (int turnIndex = 0; turnIndex < maxTurns; turnIndex++) {

            LOG.info(Logs.AGENT_RUNNING_LOOP, turnIndex);

            if (cancelled.get())
                return result(AgentStatus.CANCELLED, taskId, stepId, runId);

            if (deadline != null && Instant.now().isAfter(deadline))
                return result(AgentStatus.TIMED_OUT, taskId, stepId, runId);

            var turnId = Ids.generate();

            workflowRunStore.turnStarted(taskId, runId, turnId);

            var request = new LlmRequest(systemPrompt, task, "", toolDefs, turnIndex, llmName, llmProvider,
                    llmModel, outputSchema, List.copyOf(history));

            LOG.info(Logs.AGENT_SEND_LLM, turnIndex);

            workflowRunStore.messageSent(taskId, turnId, request);

            var response = llm.sendStreaming(request, token -> workflowRunListener.onToken(taskId, turnId, token));

            workflowRunStore.responseReceived(taskId, turnId, response);

            LOG.info(Logs.AGENT_RECD_LLM, turnIndex, response.stopReason());

            history.add(toAssistantMessage(response));

            if (response.stopReason() != StopReason.TOOL_USE || response.toolCalls().isEmpty()) {

                workflowRunStore.turnCompleted(taskId, turnId);
                workflowRunStore.runCompleted(taskId, runId);

                return result(AgentStatus.COMPLETED, taskId, stepId, runId);
            }

            var toolResults = executeToolCalls(response.toolCalls(), toolkits, cancelled, turnIndex, taskId, turnId);

            history.add(toToolResultMessage(toolResults));

            workflowRunStore.turnCompleted(taskId, turnId);
        }

        workflowRunStore.runCompleted(taskId, runId);

        return result(AgentStatus.MAX_TURNS, taskId, stepId, runId);
    }

    private static Message toAssistantMessage(ai.agentican.framework.llm.LlmResponse response) {

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
                                              AtomicBoolean cancelled, int turnIndex, String taskId, String turnId) {

        return Parallel.map(toolCalls, toolCall -> executeOne(toolCall, toolkits, cancelled, turnIndex,
                taskId, turnId));
    }

    private ToolResult executeOne(ToolCall toolCall, Map<String, Toolkit> toolkits,
                                   AtomicBoolean cancelled, int turnIndex,
                                   String taskId, String turnId) {

        workflowRunStore.toolCallStarted(taskId, turnId, toolCall);

        var toolCallId = toolCall.id();
        var toolName = toolCall.name();

        if (cancelled.get()) {

            var result = new ToolResult(toolCallId, toolName, toolError("Execution cancelled"));

            workflowRunStore.toolCallCompleted(taskId, turnId, result);

            return result;
        }

        var toolkit = toolkits.get(toolName);

        if (toolkit == null) {

            var result = new ToolResult(toolCallId, toolName, toolError("No executor found for tool: " + toolName));

            workflowRunStore.toolCallCompleted(taskId, turnId, result);

            return result;
        }

        LOG.info(Logs.AGENT_TOOL_USE, turnIndex, toolName);

        try {

            var output = toolkit.execute(toolName, toolCall.args());
            var result = new ToolResult(toolCallId, toolName, output);

            workflowRunStore.toolCallCompleted(taskId, turnId, result);

            return result;
        }
        catch (Exception e) {

            LOG.error("Turn {}: tool {} failed: {}", turnIndex, toolName, e.getMessage());

            var result = new ToolResult(toolCallId, toolName, toolError(e.getMessage()), e);

            workflowRunStore.toolCallCompleted(taskId, turnId, result);

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

    private void ensureTaskLog(String taskId, String stepId, String stepName) {

        var taskLog = workflowRunStore.load(taskId);

        if (taskLog == null) {

            workflowRunStore.taskStarted(taskId, stepName, null, Map.of());
            workflowRunStore.stepStarted(taskId, stepId, stepName);
        }
    }

    private AgentResult result(AgentStatus status, String taskId, String stepId, String runId) {

        var taskLog = workflowRunStore.load(taskId);
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
        private WorkflowRunListener workflowRunListener;

        private int maxIterations;
        private Duration timeout;

        Builder() {}

        public Builder llmClient(LlmClient llmClient)         { this.llmClient = llmClient; return this; }
        public Builder llmName(String llmName)                { this.llmName = llmName; return this; }
        public Builder llmProvider(String llmProvider)        { this.llmProvider = llmProvider; return this; }
        public Builder llmModel(String llmModel)              { this.llmModel = llmModel; return this; }
        public Builder workflowRunStore(WorkflowRunStore store)   { this.workflowRunStore = store; return this; }
        public Builder workflowRunListener(WorkflowRunListener listener)    { this.workflowRunListener = listener; return this; }
        public Builder maxIterations(int maxIterations)       { this.maxIterations = maxIterations; return this; }
        public Builder timeout(Duration timeout)              { this.timeout = timeout; return this; }

        public ReActAgentRunner build() {

            if (llmClient == null)
                throw new IllegalStateException("llmClient is required");

            return new ReActAgentRunner(llmClient, llmName, llmProvider, llmModel, workflowRunStore,
                    workflowRunListener, maxIterations, timeout);
        }
    }
}
