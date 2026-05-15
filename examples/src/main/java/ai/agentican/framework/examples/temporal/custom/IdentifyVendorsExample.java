package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.temporal.common.IdentifyVendorsInput;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.LlmCallActivityImpl;
import ai.agentican.temporal.activity.ToolCallActivityImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class IdentifyVendorsExample {

    private IdentifyVendorsExample() { }

    public static final String TASK_QUEUE = "agentican-identify-fine-grained";

    public static void registerOnWorker(Worker worker, LlmCallActivityImpl.LlmClientResolver llmResolver,
                                        List<Toolkit> toolkits) {

        worker.registerWorkflowImplementationTypes(IdentifyVendorsWorkflowImpl.class);

        worker.registerActivitiesImplementations(
                new LlmCallActivityImpl(llmResolver),
                new ToolCallActivityImpl(toolkits));
    }

    public static String run(WorkflowClient client, IdentifyVendorsInput input) {

        var workflow = client.newWorkflowStub(IdentifyVendorsWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("agentican-identify-" + System.nanoTime())
                        .build());

        return workflow.run(input);
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

            LlmCallActivityImpl.LlmClientResolver llmResolver = req -> agentican.llm(req.llmName());

            List<Toolkit> toolkits = List.of();   // TODO: register the web-search toolkit

            var service = WorkflowServiceStubs.newLocalServiceStubs();
            var client  = WorkflowClient.newInstance(service);
            var factory = WorkerFactory.newInstance(client);
            var worker  = factory.newWorker(TASK_QUEUE);

            registerOnWorker(worker, llmResolver, toolkits);

            factory.start();

            var input = new IdentifyVendorsInput("data observability platforms", 5, systemPrompt,
                    toolkits.stream().flatMap(t -> t.toolDefinitions().stream()).toList(),
                    10, "default");

            var vendors = run(client, input);

            System.out.println("=== Identified Vendors (JSON) ===\n" + vendors);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(
                IdentifyVendorsExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(
                IdentifyVendorsExample.class.getResource("/market-brief.yaml")).toURI());
    }
}
