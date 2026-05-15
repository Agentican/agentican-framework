package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.temporal.common.MarketBriefParams;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.temporal.activity.AgentStepActivityImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.Objects;

public final class MarketBriefHandCodedExample {

    private MarketBriefHandCodedExample() { }

    public static final String TASK_QUEUE = "agentican-market-brief-hand-coded";

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var registry = agentican.registry();

            AgentStepActivityImpl.AgentResolver   agentResolver   = registry.agents()::byName;
            AgentStepActivityImpl.ToolkitResolver toolkitResolver = registry.toolkits()::get;

            var service = WorkflowServiceStubs.newLocalServiceStubs();
            var client  = WorkflowClient.newInstance(service);
            var factory = WorkerFactory.newInstance(client);
            var worker  = factory.newWorker(TASK_QUEUE);

            worker.registerWorkflowImplementationTypes(MarketBriefWorkflowImpl.class);

            worker.registerActivitiesImplementations(
                    new AgentStepActivityImpl(agentResolver, toolkitResolver, new WorkflowRunStoreMemory()));

            factory.start();

            var workflow = client.newWorkflowStub(
                    MarketBriefWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId("agentican-market-brief-hc-" + System.nanoTime())
                            .build());

            var brief = workflow.run(new MarketBriefParams("data observability platforms", 5));

            System.out.println("=== Market Brief (hand-coded) ===\n" + brief);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(
                MarketBriefHandCodedExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(
                MarketBriefHandCodedExample.class.getResource("/market-brief.yaml")).toURI());
    }
}
