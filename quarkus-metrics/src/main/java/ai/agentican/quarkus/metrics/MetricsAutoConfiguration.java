package ai.agentican.quarkus.metrics;

import ai.agentican.framework.orchestration.execution.TaskListener;
import ai.agentican.framework.llm.LlmClientDecorator;

import ai.agentican.framework.store.TaskStateStore;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsAutoConfiguration {

    @Inject
    MeterRegistry registry;

    @Inject
    TaskStateStore taskStateStore;

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
