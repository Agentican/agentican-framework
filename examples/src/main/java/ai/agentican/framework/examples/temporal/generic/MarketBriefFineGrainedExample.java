package ai.agentican.framework.examples.temporal.generic;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.AgentConfigActivityImpl;
import ai.agentican.temporal.activity.AgentStepActivityImpl;
import ai.agentican.temporal.activity.KnowledgeStoreActivityImpl;
import ai.agentican.temporal.activity.LlmCallActivityImpl;
import ai.agentican.temporal.activity.ToolCallActivityImpl;
import ai.agentican.temporal.activity.WorkflowRunStoreActivityImpl;
import ai.agentican.temporal.workflow.AgenticanWorkflow;
import ai.agentican.temporal.workflow.AgenticanWorkflowInput;
import ai.agentican.temporal.workflow.FineGrainedAgenticanWorkflowImpl;
import ai.agentican.temporal.workflow.RunnerBasedAgentWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generic whole-plan workflow at <em>fine-grained</em> activity grain. Same
 * {@code market-brief.yaml} the other examples load, same
 * {@link AgenticanWorkflow} interface as {@link MarketBriefExample} — but each
 * agent step is dispatched as a child {@code RunnerBasedAgentWorkflow}, so
 * every LLM round-trip and every tool call shows up as a discrete Temporal
 * activity inside that child's history.
 *
 * <p>Compare with the four siblings:
 * <ul>
 *   <li>{@link MarketBriefExample} — same workflow class interface, but
 *       {@code AgenticanWorkflowImpl} treats each agent step as one opaque
 *       {@code AgentStepActivity}.</li>
 *   <li>{@link IdentifyVendorsRunnerExample} — same fine grain, but only one
 *       agent invocation (no plan).</li>
 *   <li>{@code custom.MarketBriefHandCodedExample} — coarse grain, custom
 *       workflow class per plan.</li>
 *   <li>{@code custom.IdentifyVendorsExample} — fine grain, custom workflow
 *       class with the LLM/tool loop hand-coded in the workflow body.</li>
 * </ul>
 *
 * <h2>Trade-off</h2>
 *
 * Each agent step in the plan becomes a real Temporal child workflow
 * execution — extra workflow tasks, separate retry policies, separate
 * history per child. For a plan with N agent steps you pay N child-workflow
 * tax. The benefit: every LLM and tool call is observable as a first-class
 * Temporal event, with per-call retry policies, in a complete history per
 * agent step.
 */
public final class MarketBriefFineGrainedExample {

    private MarketBriefFineGrainedExample() { }

    public static final String TASK_QUEUE = "agentican-market-brief-generic-fg";

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var registry = agentican.registry();
            var plan     = registry.workflows().byName("market-brief");

            AgentStepActivityImpl.AgentResolver agentResolver = registry.agents()::byName;

            var llmClient = agentican.llm(LlmConfig.DEFAULT);

            List<Toolkit> toolkits = List.of();   // TODO: register the web-search toolkit

            var service = WorkflowServiceStubs.newLocalServiceStubs();
            var client  = WorkflowClient.newInstance(service);
            var factory = WorkerFactory.newInstance(client);
            var worker  = factory.newWorker(TASK_QUEUE);

            worker.registerWorkflowImplementationTypes(
                    FineGrainedAgenticanWorkflowImpl.class,
                    RunnerBasedAgentWorkflowImpl.class);

            worker.registerActivitiesImplementations(
                    new AgentConfigActivityImpl(agentResolver),
                    new LlmCallActivityImpl(llmClient),
                    new ToolCallActivityImpl(toolkits),
                    new WorkflowRunStoreActivityImpl(new WorkflowRunStoreMemory()),
                    new KnowledgeStoreActivityImpl(new KnowledgeStoreMemory()));

            factory.start();

            var workflow = client.newWorkflowStub(
                    AgenticanWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId("agentican-fg-" + plan.id() + "-" + System.nanoTime())
                            .build());

            var brief = workflow.run(new AgenticanWorkflowInput(plan, Map.of(
                    "topic", "data observability platforms",
                    "vendor_count", String.valueOf(5))));

            System.out.println("=== Market Brief (fine-grained) ===\n" + brief);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(
                MarketBriefFineGrainedExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(
                MarketBriefFineGrainedExample.class.getResource("/market-brief.yaml")).toURI());
    }
}
