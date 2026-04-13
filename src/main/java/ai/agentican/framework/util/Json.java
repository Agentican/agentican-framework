package ai.agentican.framework.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    public static ObjectMapper mapper() {

        return MAPPER;
    }

    public static String writeValueAsString(Object obj) throws JsonProcessingException {

        return MAPPER.writeValueAsString(obj);
    }

    public static <T> T readValue(String json, Class<T> type) throws JsonProcessingException {

        return MAPPER.readValue(json, type);
    }

    public static String pretty(Object obj) {

        try {
            return PRETTY.writeValueAsString(obj);
        }
        catch (JsonProcessingException _) {
            return obj.toString();
        }
    }

    public static <T> T findObject(String text, Class<T> type) {

        for (int i = 0; i < text.length(); i++) {

            if (text.charAt(i) != '{') continue;

            int depth = 0, end = -1;

            for (int j = i; j < text.length(); j++) {

                if (text.charAt(j) == '{') depth++;
                else if (text.charAt(j) == '}') depth--;

                if (depth == 0) { end = j + 1; break; }
            }

            if (end <= i) continue;

            try {
                return MAPPER.readValue(text.substring(i, end), type);
            }
            catch (Exception _) {}
        }

        throw new IllegalStateException("No valid JSON object found in response");
    }

    @SuppressWarnings("unchecked")
    public static List<String> findArray(String text) {

        if (text.contains("\"loop\"")) {

            var result = findLoopArray(text);

            if (result != null)
                return result;
        }

        return findPlainArray(text);
    }

    @SuppressWarnings("unchecked")
    private static List<String> findLoopArray(String text) {

        for (int i = 0; i < text.length(); i++) {

            if (text.charAt(i) != '{') continue;

            int depth = 0, end = -1;

            for (int j = i; j < text.length(); j++) {

                if (text.charAt(j) == '{') depth++;
                else if (text.charAt(j) == '}') depth--;

                if (depth == 0) { end = j + 1; break; }
            }

            if (end <= i) continue;

            try {

                var tree = MAPPER.readTree(text.substring(i, end));

                if (tree.isObject() && tree.has("loop") && tree.get("loop").isArray()) {

                    Map<String, Object> shared = new LinkedHashMap<>();
                    var fields = tree.fields();

                    while (fields.hasNext()) {

                        var entry = fields.next();

                        if (!"loop".equals(entry.getKey()))
                            shared.put(entry.getKey(), MAPPER.treeToValue(entry.getValue(), Object.class));
                    }

                    var items = new ArrayList<String>();

                    for (var element : tree.get("loop")) {

                        if (!shared.isEmpty() && element.isObject()) {

                            Map<String, Object> merged = new LinkedHashMap<>(MAPPER.convertValue(element, Map.class));
                            shared.forEach(merged::putIfAbsent);
                            items.add(MAPPER.writeValueAsString(merged));
                        }
                        else {
                            items.add(element.isTextual() ? element.asText() : element.toString());
                        }
                    }

                    return items;
                }
            }
            catch (Exception _) {}
        }

        return null;
    }

    private static List<String> findPlainArray(String text) {

        try {

            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');

            if (start >= 0 && end > start) {

                var tree = MAPPER.readTree(text.substring(start, end + 1));

                if (tree.isArray()) {

                    var items = new ArrayList<String>();

                    for (var element : tree)
                        items.add(element.isTextual() ? element.asText() : element.toString());

                    return items;
                }
            }
        }
        catch (Exception _) {}

        return List.of();
    }
}
