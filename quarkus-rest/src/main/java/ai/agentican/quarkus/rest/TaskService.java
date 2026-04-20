package ai.agentican.quarkus.rest;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.model.Plan;
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
    AgenticanRuntime agentican;

    private final ConcurrentMap<String, TaskHandle> handles = new ConcurrentHashMap<>();

    public TaskHandle submit(String description) {

        var handle = agentican.run(description);

        track(handle);

        return handle;
    }

    public TaskHandle submit(Plan plan) {

        var handle = agentican.run(plan);

        track(handle);

        return handle;
    }

    public TaskHandle submit(Plan plan, Map<String, String> inputs) {

        var handle = agentican.run(plan, inputs);

        track(handle);

        return handle;
    }

    public TaskHandle submitByPlan(String planId, Map<String, String> inputs) {

        var plan = agentican.registry().plans().getById(planId);

        if (plan == null)
            throw new jakarta.ws.rs.NotFoundException("No plan definition with id: " + planId);

        var handle = agentican.run(plan, inputs);

        track(handle);

        return handle;
    }

    public TaskHandle handleFor(String taskId) {

        return handles.get(taskId);
    }

    public Collection<TaskHandle> activeHandles() {

        return Collections.unmodifiableCollection(handles.values());
    }

    private void track(TaskHandle handle) {

        handles.put(handle.taskId(), handle);
        handle.resultAsync().whenComplete((result, error) -> handles.remove(handle.taskId()));
    }
}
