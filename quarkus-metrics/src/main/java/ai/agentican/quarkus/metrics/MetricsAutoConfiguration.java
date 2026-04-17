package ai.agentican.quarkus.metrics;

import ai.agentican.framework.TaskListener;
import ai.agentican.framework.llm.LlmClientDecorator;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsAutoConfiguration {

    @Inject
    MeterRegistry registry;

    @Inject
    ai.agentican.framework.state.TaskStateStore taskStateStore;

    @Produces
    @ApplicationScoped
    public LlmClientDecorator meteredLlmDecorator() {

        return (config, client) ->
                new MeteredLlmClient(config.name(), config.model(), client, registry);
    }

    @Produces
    @ApplicationScoped
    public TaskListener meteredTurnListener() {

        return new MeteredTurnListener(registry, taskStateStore);
    }
}
