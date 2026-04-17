package ai.agentican.quarkus.rest.sse;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventTimeline {

    private final int capacity;
    private final Deque<SequencedEvent> buffer;
    private final List<MultiEmitter<? super SequencedEvent>> emitters = new CopyOnWriteArrayList<>();

    private long nextId = 0;
    private boolean completed = false;

    public EventTimeline(int capacity) {

        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(Math.min(capacity, 128));
    }

    public synchronized void emit(Object payload) {

        if (completed) return;

        var event = new SequencedEvent(nextId++, payload);

        buffer.add(event);

        if (buffer.size() > capacity) buffer.removeFirst();

        emitters.forEach(e -> e.emit(event));
    }

    public Multi<SequencedEvent> stream(long lastEventId) {

        return Multi.createFrom().emitter(emitter -> {

            synchronized (this) {

                for (var event : buffer) {
                    if (event.id() > lastEventId) {
                        emitter.emit(event);
                    }
                }

                if (completed) {
                    emitter.complete();
                    return;
                }

                emitters.add(emitter);
                emitter.onTermination(() -> emitters.remove(emitter));
            }
        });
    }

    public synchronized void complete(Object finalPayload) {

        if (completed) return;

        if (finalPayload != null) {

            var event = new SequencedEvent(nextId++, finalPayload);

            buffer.add(event);
            if (buffer.size() > capacity) buffer.removeFirst();

            emitters.forEach(e -> e.emit(event));
        }

        completed = true;
        emitters.forEach(MultiEmitter::complete);
        emitters.clear();
    }

    public synchronized void complete() {

        complete(null);
    }
}
