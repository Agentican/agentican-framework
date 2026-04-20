package ai.agentican.framework.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.LinkedHashMap;
import java.util.Map;

final class AgenticanParamMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    static Map<String, String> toStringMap(Object params) {

        if (params == null) return Map.of();

        if (params instanceof Map<?, ?> m) {

            var out = new LinkedHashMap<String, String>(m.size());

            m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));

            return out;
        }

        var tree = MAPPER.valueToTree(params);

        if (!tree.isObject())
            throw new IllegalArgumentException(
                    "Params must be a record, POJO, or Map — got " + params.getClass().getName());

        var out = new LinkedHashMap<String, String>();

        tree.properties().forEach(entry -> {

            var node = (JsonNode) entry.getValue();
            out.put(entry.getKey(), stringify(node));
        });

        return out;
    }

    private static String stringify(JsonNode node) {

        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber() || node.isBoolean()) return node.asText();

        return node.toString();
    }

    private AgenticanParamMapper() {}
}
