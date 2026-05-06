package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRun;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class ReactiveAgentican {

    @Inject
    Agentican agentican;

    public Uni<WorkflowRun<String>> run(String description) {

        return Uni.createFrom().item(() -> agentican.run(description));
    }

    public Uni<WorkflowRun<WorkflowRunResult>> run(WorkflowDefinition plan) {

        return run(plan, Map.of());
    }

    public Uni<WorkflowRun<WorkflowRunResult>> run(WorkflowDefinition plan, Map<String, String> inputs) {

        return Uni.createFrom().item(() -> agentican.workflow(plan).raw().start(inputs));
    }

    public Uni<WorkflowRunResult> runAndAwait(String description) {

        return Uni.createFrom().completionStage(() -> agentican.run(description).untypedFuture());
    }

    public Uni<WorkflowRunResult> runAndAwait(WorkflowDefinition plan) {

        return runAndAwait(plan, Map.of());
    }

    public Uni<WorkflowRunResult> runAndAwait(WorkflowDefinition plan, Map<String, String> inputs) {

        return Uni.createFrom().completionStage(
                () -> agentican.workflow(plan).raw().start(inputs).future());
    }
}
