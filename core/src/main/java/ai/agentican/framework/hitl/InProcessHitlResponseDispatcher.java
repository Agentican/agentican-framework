package ai.agentican.framework.hitl;

import java.util.Objects;

public final class InProcessHitlResponseDispatcher implements HitlResponseDispatcher {

    private final HitlManager hitlManager;

    public InProcessHitlResponseDispatcher(HitlManager hitlManager) {

        this.hitlManager = Objects.requireNonNull(hitlManager, "hitlManager");
    }

    @Override
    public void respond(String checkpointId, HitlResponse response) {

        hitlManager.respond(checkpointId, response);
    }

    @Override
    public void cancel(String checkpointId) {

        hitlManager.cancel(checkpointId);
    }
}
