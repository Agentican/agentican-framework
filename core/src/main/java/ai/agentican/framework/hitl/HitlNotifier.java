package ai.agentican.framework.hitl;

import org.slf4j.LoggerFactory;

@FunctionalInterface
public interface HitlNotifier {

    void onCheckpoint(HitlManager manager, HitlCheckpoint checkpoint);

    static HitlNotifier logging() {

        var log = LoggerFactory.getLogger(HitlNotifier.class);

        return (manager, checkpoint) ->
                log.info("HITL checkpoint '{}': {}", checkpoint.id(), checkpoint.description());
    }
}
