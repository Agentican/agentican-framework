package ai.agentican.framework.examples.temporal.generic;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.temporal.activity.AgentStepActivityImpl;
import ai.agentican.temporal.workflow.AgenticanWorkflow;
import ai.agentican.temporal.workflow.AgenticanWorkflowImpl;
import ai.agentican.temporal.workflow.AgenticanWorkflowInput;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class MarketBriefExample {

    private MarketBriefExample() { }

    public static final String TASK_QUEUE = "agentican-market-brief-generic";

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var registry = agentican.registry();
            var plan = registry.workflows().byName("market-brief");

            AgentStepActivityImpl.AgentResolver agentResolver = registry.agents()::byName;
            AgentStepActivityImpl.ToolkitResolver toolkitResolver = registry.toolkits()::get;

            var service = WorkflowServiceStubs.newLocalServiceStubs();
            var client  = WorkflowClient.newInstance(service);
            var factory = WorkerFactory.newInstance(client);
            var worker  = factory.newWorker(TASK_QUEUE);

            worker.registerWorkflowImplementationTypes(AgenticanWorkflowImpl.class);

            worker.registerActivitiesImplementations(
                    new AgentStepActivityImpl(agentResolver, toolkitResolver, new WorkflowRunStoreMemory()));

            factory.start();

            var workflow = client.newWorkflowStub(
                    AgenticanWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId("agentican-" + plan.id() + "-" + System.nanoTime())
                            .build());

            var brief = workflow.run(new AgenticanWorkflowInput(plan, Map.of(
                    "topic", "data observability platforms",
                    "vendor_count", String.valueOf(5))));

            System.out.println("=== Market Brief ===\n" + brief);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(MarketBriefExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(MarketBriefExample.class.getResource("/market-brief.yaml")).toURI());
    }
}
