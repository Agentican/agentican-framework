package ai.agentican.quarkus;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.agent.Agent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgentProducer {

    @Inject
    AgenticanRuntime agentican;

    @Produces
    @Dependent
    @AgenticanAgent("")
    public Agent produceAgent(InjectionPoint injectionPoint) {

        var ref = injectionPoint.getQualifiers().stream()
                .filter(AgenticanAgent.class::isInstance)
                .map(AgenticanAgent.class::cast)
                .map(AgenticanAgent::value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "@AgenticanAgent qualifier missing on injection point: " + injectionPoint));

        var agent = agentican.registry().agents().get(ref);

        if (agent == null) agent = agentican.registry().agents().getByName(ref);

        if (agent == null)
            throw new IllegalStateException(
                    "No agent registered with id or name '" + ref + "'. "
                            + "Declare it via agentican.agents[*] in application.properties.");

        return agent;
    }
}
