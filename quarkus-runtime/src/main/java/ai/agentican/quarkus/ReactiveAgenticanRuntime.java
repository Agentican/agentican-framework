package ai.agentican.quarkus;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.model.Plan;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class ReactiveAgenticanRuntime {

    @Inject
    AgenticanRuntime agentican;

    public Uni<TaskHandle> run(String description) {

        return Uni.createFrom().item(() -> agentican.run(description));
    }

    public Uni<TaskHandle> run(Plan plan) {

        return Uni.createFrom().item(() -> agentican.run(plan));
    }

    public Uni<TaskHandle> run(Plan plan, Map<String, String> inputs) {

        return Uni.createFrom().item(() -> agentican.run(plan, inputs));
    }

    public Uni<TaskResult> runAndAwait(String description) {

        return Uni.createFrom().completionStage(() -> agentican.run(description).resultAsync());
    }

    public Uni<TaskResult> runAndAwait(Plan plan) {

        return Uni.createFrom().completionStage(() -> agentican.run(plan).resultAsync());
    }

    public Uni<TaskResult> runAndAwait(Plan plan, Map<String, String> inputs) {

        return Uni.createFrom().completionStage(() -> agentican.run(plan, inputs).resultAsync());
    }
}
