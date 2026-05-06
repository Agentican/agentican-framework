package ai.agentican.framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class WorkflowSchemaGenerator {

    private static final ConcurrentMap<Class<?>, JsonNode> CACHE = new ConcurrentHashMap<>();

    private static final SchemaGenerator GENERATOR;

    static {

        var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);

        configBuilder.with(new JacksonModule());
        configBuilder.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);
        configBuilder.forFields().withRequiredCheck(field -> true);

        var config = configBuilder.build();

        GENERATOR = new SchemaGenerator(config);
    }

    static JsonNode schemaFor(Class<?> type) {

        if (type == null || type == Void.class) return null;

        return CACHE.computeIfAbsent(type, GENERATOR::generateSchema);
    }

    private WorkflowSchemaGenerator() {}
}
