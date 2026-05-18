package ai.agentican.temporal;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.temporal.activity.AgentConfigActivityImpl;
import ai.agentican.temporal.activity.AgentStepActivityImpl;
import ai.agentican.temporal.activity.AgenticanActivityImpl;
import ai.agentican.temporal.activity.KnowledgeStoreActivityImpl;
import ai.agentican.temporal.activity.LlmCallActivityImpl;
import ai.agentican.temporal.activity.ToolCallActivityImpl;
import ai.agentican.temporal.workflow.AgenticanWorkflowInput;

import java.util.Map;

public final class TemporalAgentican {

    private final Agentican agentican;
    private final WorkflowRunStore workflowRunStore;
    private final KnowledgeStore knowledgeStore;

    public static TemporalAgentican of(Agentican agentican) {

        return new TemporalAgentican(agentican, new WorkflowRunStoreMemory(), new KnowledgeStoreMemory());
    }

    public TemporalAgentican(Agentican agentican,
                             WorkflowRunStore workflowRunStore,
                             KnowledgeStore knowledgeStore) {

        if (agentican == null) throw new IllegalArgumentException("agentican is required");
        if (workflowRunStore == null) throw new IllegalArgumentException("workflowRunStore is required");
        if (knowledgeStore == null) throw new IllegalArgumentException("knowledgeStore is required");

        this.agentican = agentican;
        this.workflowRunStore = workflowRunStore;
        this.knowledgeStore = knowledgeStore;
    }

    public AgentStepActivityImpl agentStepActivity() {

        return new AgentStepActivityImpl(
                agentican.registry().agents()::byName,
                agentican.registry().toolkits()::get,
                workflowRunStore);
    }

    public AgentConfigActivityImpl agentConfigActivity() {

        return new AgentConfigActivityImpl(agentican.registry().agents()::byName);
    }

    public LlmCallActivityImpl llmCallActivity() {

        // Cast disambiguates between LlmCallActivityImpl(LlmClientResolver) and
        // LlmCallActivityImpl(LlmClient) — both have SAMs taking an LlmRequest.
        LlmCallActivityImpl.LlmClientResolver resolver = req -> agentican.llm(req.llmName());

        return new LlmCallActivityImpl(resolver);
    }

    public ToolCallActivityImpl toolCallActivity() {

        var toolkits = agentican.registry().toolkits();

        var registered = toolkits.slugs().stream().map(toolkits::get).toList();

        return new ToolCallActivityImpl(registered);
    }

    public AgenticanActivityImpl agenticanActivity() {

        return new AgenticanActivityImpl(agentican.eventBus(), workflowRunStore);
    }

    public KnowledgeStoreActivityImpl knowledgeStoreActivity() {

        return new KnowledgeStoreActivityImpl(knowledgeStore);
    }

    public AgenticanWorkflowInput agenticanWorkflowInput(String planName, Map<String, String> params) {

        var plan = agentican.registry().workflows().byName(planName);

        if (plan == null) throw new IllegalArgumentException("Unknown workflow plan: " + planName);

        return new AgenticanWorkflowInput(plan, params);
    }
}
