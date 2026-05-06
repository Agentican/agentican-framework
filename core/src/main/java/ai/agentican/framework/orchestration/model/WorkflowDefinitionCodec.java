package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class WorkflowDefinitionCodec {

    private final ObjectMapper mapper;
    private final ObjectReader reader;

    public WorkflowDefinitionCodec(CodeStepRegistry registry) {

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
