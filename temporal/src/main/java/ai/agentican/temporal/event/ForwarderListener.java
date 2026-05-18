package ai.agentican.temporal.event;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.temporal.activity.AgenticanActivity;

import java.util.Objects;

public final class ForwarderListener implements AgenticanEventListener {

    private final AgenticanActivity activity;

    public ForwarderListener(AgenticanActivity activity) {

        this.activity = Objects.requireNonNull(activity, "activity");
    }

    @Override
    public void on(AgenticanEvent event) {

        activity.publish(event);
    }
}
