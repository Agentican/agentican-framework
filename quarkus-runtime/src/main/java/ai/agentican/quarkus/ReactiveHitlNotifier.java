package ai.agentican.quarkus;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;

import io.smallrye.mutiny.Uni;

@FunctionalInterface
public interface ReactiveHitlNotifier {

    Uni<Void> onCheckpoint(HitlManager manager, HitlCheckpoint checkpoint);

    static HitlNotifier toSync(ReactiveHitlNotifier reactive) {

        return (manager, checkpoint) ->
                reactive.onCheckpoint(manager, checkpoint).await().indefinitely();
    }
}
