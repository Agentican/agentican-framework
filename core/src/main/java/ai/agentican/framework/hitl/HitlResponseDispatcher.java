package ai.agentican.framework.hitl;

public interface HitlResponseDispatcher {

    void respond(String checkpointId, HitlResponse response);

    void cancel(String checkpointId);
}
