package ai.agentican.quarkus.metrics;

import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.llm.LlmClientDecorator;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsAutoConfiguration {

    @Inject
    MeterRegistry registry;

    @Produces
    @ApplicationScoped
    public LlmClientDecorator meteredLlmDecorator() {

        return (config, client) ->
                new MeteredLlmClient(config.name(), config.model(), client, registry);
    }

    @Produces
    @ApplicationScoped
    public AgenticanEventListener meteredTurnListener() {

        return new MeteredTurnListener(registry);
    }
}
