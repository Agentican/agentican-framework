package ai.agentican.framework.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AgenticanEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanEventBus.class);

    private final List<AgenticanEventListener> firstTier = new CopyOnWriteArrayList<>();
    private final List<AgenticanEventListener> observers = new CopyOnWriteArrayList<>();

    public void subscribeFirst(AgenticanEventListener listener) {

        if (listener == null) throw new IllegalArgumentException("listener is required");

        firstTier.add(listener);
    }

    public void subscribe(AgenticanEventListener listener) {

        if (listener == null) throw new IllegalArgumentException("listener is required");

        observers.add(listener);
    }

    public void publish(AgenticanEvent event) {

        if (event == null) throw new IllegalArgumentException("event is required");

        for (var listener : firstTier) listener.on(event);   // exceptions propagate

        for (var listener : observers) {

            try {
                listener.on(event);
            }
            catch (RuntimeException ex) {

                LOG.error("Observer listener {} threw on {} — continuing with remaining observers",
                        listener.getClass().getName(), event.getClass().getSimpleName(), ex);
            }
        }
    }

    public int subscriberCount() {

        return firstTier.size() + observers.size();
    }

    public List<AgenticanEventListener> listeners() {

        var snapshot = new ArrayList<AgenticanEventListener>(firstTier.size() + observers.size());

        snapshot.addAll(firstTier);
        snapshot.addAll(observers);

        return List.copyOf(snapshot);
    }
}
