package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Plan-aware JSON codec that wires the {@link CodeStepRegistry} into
 * Jackson's {@code InjectableValues} so the {@link PlanStepCodeDeserializer}
 * can resolve {@code codeSlug} to the registered {@code Class<I>}.
 *
 * <p>Serialization is plain Jackson — no codec needed — but a {@code toJson}
 * helper is provided for symmetry. Use this codec from any caller that
 * deserializes a {@link Plan} (or any structure containing
 * {@link PlanStepCode}) so the typed {@code I inputs} field is reconstructed
 * correctly.
 */
public class PlanCodec {

    private final ObjectMapper mapper;
    private final ObjectReader reader;

    public PlanCodec(CodeStepRegistry registry) {

        if (registry == null)
            throw new IllegalArgumentException("CodeStepRegistry is required");

        this.mapper = Json.mapper();

        var values = new InjectableValues.Std()
                .addValue(CodeStepRegistry.class.getName(), registry);

        this.reader = mapper.reader(values);
    }

    public <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {

        return reader.forType(type).readValue(json);
    }

    public String toJson(Object value) throws JsonProcessingException {

        return mapper.writeValueAsString(value);
    }
}
