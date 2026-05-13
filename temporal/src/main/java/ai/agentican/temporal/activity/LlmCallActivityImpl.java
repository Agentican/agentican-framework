package ai.agentican.temporal.activity;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;

public class LlmCallActivityImpl implements LlmCallActivity {

    private final LlmClientResolver resolver;

    public LlmCallActivityImpl(LlmClientResolver resolver) {

        if (resolver == null) throw new IllegalArgumentException("resolver is required");

        this.resolver = resolver;
    }

    public LlmCallActivityImpl(LlmClient singleClient) {

        if (singleClient == null) throw new IllegalArgumentException("LlmClient is required");

        this.resolver = req -> singleClient;
    }

    @Override
    public LlmResponse send(LlmRequest request) {

        var client = resolver.resolve(request);

        if (client == null)
            throw new IllegalArgumentException("No LlmClient resolved for request (llmName=" + request.llmName() + ")");

        return client.send(request);
    }

    @FunctionalInterface
    public interface LlmClientResolver {

        LlmClient resolve(LlmRequest request);
    }
}
