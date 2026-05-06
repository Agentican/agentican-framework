package ai.agentican.quarkus.rest;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRun;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class TaskService {

    @Inject
    Agentican agentican;

    private final ConcurrentMap<String, WorkflowRun<WorkflowRunResult>> handles = new ConcurrentHashMap<>();

    public WorkflowRun<WorkflowRunResult> submit(String description) {

        var planning = agentican.plan(description);
        return submit(planning.definition(), planning.inputs());
    }

    public WorkflowRun<WorkflowRunResult> submit(WorkflowDefinition plan) {

        return submit(plan, Map.of());
    }

    public WorkflowRun<WorkflowRunResult> submit(WorkflowDefinition plan, Map<String, String> inputs) {

        var handle = agentican.workflow(plan).raw().start(inputs);

        track(handle);

        return handle;
    }

    public WorkflowRun<WorkflowRunResult> submitByPlan(String planId, Map<String, String> inputs) {

        var plan = agentican.registry().workflows().byId(planId);

        if (plan == null)
            throw new jakarta.ws.rs.NotFoundException("No definition definition with id: " + planId);

        return submit(plan, inputs);
    }

    public WorkflowRun<WorkflowRunResult> handleFor(String taskId) {

        return handles.get(taskId);
    }

    public Collection<WorkflowRun<WorkflowRunResult>> activeHandles() {

        return Collections.unmodifiableCollection(handles.values());
    }

    private void track(WorkflowRun<WorkflowRunResult> handle) {

        handles.put(handle.id(), handle);
        handle.future().whenComplete((result, error) -> handles.remove(handle.id()));
    }
}
