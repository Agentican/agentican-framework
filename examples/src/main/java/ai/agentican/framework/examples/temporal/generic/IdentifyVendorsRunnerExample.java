package ai.agentican.framework.examples.temporal.generic;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.examples.temporal.common.IdentifyVendorsInput;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.KnowledgeStoreActivityImpl;
import ai.agentican.temporal.activity.LlmCallActivityImpl;
import ai.agentican.temporal.activity.ToolCallActivityImpl;
import ai.agentican.temporal.activity.WorkflowRunStoreActivityImpl;
import ai.agentican.temporal.workflow.RunnerBasedAgentInput;
import ai.agentican.temporal.workflow.RunnerBasedAgentWorkflow;
import ai.agentican.temporal.workflow.RunnerBasedAgentWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdentifyVendorsRunnerExample {

    private IdentifyVendorsRunnerExample() { }

    public static final String TASK_QUEUE = "agentican-identify-runner";

    public static void registerOnWorker(Worker worker, LlmClient llmClient, List<Toolkit> toolkits) {

        worker.registerWorkflowImplementationTypes(RunnerBasedAgentWorkflowImpl.class);

        worker.registerActivitiesImplementations(
                new LlmCallActivityImpl(llmClient),
                new ToolCallActivityImpl(toolkits),
                new WorkflowRunStoreActivityImpl(new WorkflowRunStoreMemory()),
                new KnowledgeStoreActivityImpl(new KnowledgeStoreMemory()));
    }

    public static String run(WorkflowClient client, IdentifyVendorsInput input) {

        var task = input.systemPrompt() + "\n\n" + input.userTask();

        var workflow = client.newWorkflowStub(
                RunnerBasedAgentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("agentican-identify-runner-" + System.nanoTime())
                        .build());

        return workflow.run(new RunnerBasedAgentInput(
                AgentConfig.builder()
                        .id("researcher")
                        .name("researcher")
                        .role(input.systemPrompt())
                        .llm(input.llmName())
                        .maxTurns(input.maxTurns())
                        .build(),
                task, "identify-" + System.nanoTime(),  "step-identify", "identify", List.of(),                            // skills baked into the role text
                input.tools(), Map.of(), null, input.maxTurns()));
    }

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var registry  = agentican.registry();
            var researcher = registry.agents().byName("researcher");
            var webSearch  = registry.skills().byName("web-search");

            var systemPrompt = researcher.role() + "\n\n" + webSearch.instructions();

            LlmClient llmClient = agentican.llm(LlmConfig.DEFAULT);

            List<Toolkit> toolkits = List.of();   // TODO: register the web-search toolkit

            var service = WorkflowServiceStubs.newLocalServiceStubs();
            var client  = WorkflowClient.newInstance(service);
            var factory = WorkerFactory.newInstance(client);
            var worker  = factory.newWorker(TASK_QUEUE);

            registerOnWorker(worker, llmClient, toolkits);

            factory.start();

            var input = new IdentifyVendorsInput(
                    "data observability platforms",
                    5,
                    systemPrompt,
                    toolkits.stream().flatMap(t -> t.toolDefinitions().stream()).toList(),
                    10,
                    "default");

            var vendors = run(client, input);

            System.out.println("=== Identified Vendors (JSON) ===\n" + vendors);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(
                IdentifyVendorsRunnerExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(
                IdentifyVendorsRunnerExample.class.getResource("/market-brief.yaml")).toURI());
    }
}
