package ai.agentican.framework.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class SchemaGenerator {

    private static final ConcurrentMap<Class<?>, JsonNode> CACHE = new ConcurrentHashMap<>();

    private static final com.github.victools.jsonschema.generator.SchemaGenerator GENERATOR;

    static {

        var configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());

        GENERATOR = new com.github.victools.jsonschema.generator.SchemaGenerator(configBuilder.build());
    }

    static JsonNode schemaFor(Class<?> type) {

        if (type == null || type == Void.class) return null;
        return CACHE.computeIfAbsent(type, GENERATOR::generateSchema);
    }

    private SchemaGenerator() {}
}
