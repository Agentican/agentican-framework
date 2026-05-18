package ai.agentican.framework.examples.temporal;

import ai.agentican.framework.Agentican;
import ai.agentican.temporal.TemporalAgentican;
import ai.agentican.temporal.workflow.AgenticanWorkflow;
import ai.agentican.temporal.workflow.AgenticanWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

import java.nio.file.Path;
import java.util.Objects;

public final class MarketBriefExample {

    private MarketBriefExample() { }

    public static final String TASK_QUEUE = "agentican-market-brief";

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var temporalAgentican = TemporalAgentican.of(agentican);

            var workflow = workflow(temporalAgentican);

            var workflowParams = new MarketBriefParams("data observability platforms", 5);

            var workflowInput = temporalAgentican.agenticanWorkflowInput("market-brief", workflowParams.asMap());

            var workflowOutput = workflow.run(workflowInput);

            System.out.println("=== Market Brief ===\n" + workflowOutput);
        }
    }

    static Path enginePath() throws Exception {

        return Path.of(Objects.requireNonNull(MarketBriefExample.class.getResource("/engine.yaml")).toURI());
    }

    static Path catalogPath() throws Exception {

        return Path.of(Objects.requireNonNull(MarketBriefExample.class.getResource("/market-brief.yaml")).toURI());
    }

    static AgenticanWorkflow workflow(TemporalAgentican temporal) {

        var service = WorkflowServiceStubs.newLocalServiceStubs();
        var client  = WorkflowClient.newInstance(service);
        var factory = WorkerFactory.newInstance(client);
        var worker  = factory.newWorker(TASK_QUEUE);

        worker.registerWorkflowImplementationTypes(AgenticanWorkflowImpl.class);
        worker.registerActivitiesImplementations(temporal.agentStepActivity());

        factory.start();

        return client.newWorkflowStub(
                AgenticanWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("agentican-market-brief-" + System.nanoTime())
                        .build());
    }
}
