package ai.agentican.framework.llm;

import ai.agentican.framework.util.Json;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

@FunctionalInterface
public interface LlmClient {

    LlmResponse send(LlmRequest request);

    default LlmResponse sendStreaming(LlmRequest request, Consumer<String> onToken) {

        var response = send(request);

        if (onToken != null && response.text() != null && !response.text().isEmpty())
            onToken.accept(response.text());

        return response;
    }

    static LlmClient withLogging(LlmClient client) {

        var log = LoggerFactory.getLogger(LlmClient.class);

        return request -> {

            log.debug("LLM request: {}", Json.pretty(request));

            var response = client.send(request);

            log.debug("LLM response: {}", Json.pretty(response));

            return response;
        };
    }
}
