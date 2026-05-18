package ai.agentican.framework.event;

@FunctionalInterface
public interface AgenticanEventListener {

    void on(AgenticanEvent event);
}
