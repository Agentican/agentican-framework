package ai.agentican.temporal.activity;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.store.WorkflowRunStore;

import java.util.Objects;

public final class AgenticanActivityImpl implements AgenticanActivity {

    private final AgenticanEventBus mainBus;
    private final WorkflowRunStore store;

    public AgenticanActivityImpl(AgenticanEventBus mainBus, WorkflowRunStore store) {

        this.mainBus = Objects.requireNonNull(mainBus, "mainBus");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public void publish(AgenticanEvent event) {

        mainBus.publish(event);
    }

    @Override
    public WorkflowRunLog loadRunLog(String taskId) {

        return store.load(taskId);
    }
}
