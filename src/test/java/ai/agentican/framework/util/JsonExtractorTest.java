package ai.agentican.framework.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonExtractorTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestObj(String name, int value) {}

    @Test
    void findObjectFromCleanJson() {

        var result = Json.findObject("{\"name\": \"test\", \"value\": 42}", TestObj.class);

        assertEquals("test", result.name());
        assertEquals(42, result.value());
    }

    @Test
    void findObjectFromTextWithJson() {

        var result =
                Json.findObject("Here is the plan:\n\n{\"name\": \"test\", \"value\": 99}\n\nDone.",
                        TestObj.class);

        assertEquals("test", result.name());
        assertEquals(99, result.value());
    }

    @Test
    void findObjectNoJson() {

        assertThrows(IllegalStateException.class,
                () -> Json.findObject("No JSON here", TestObj.class));
    }

    @Test
    void findArrayPlain() {

        var items = Json.findArray("[{\"name\": \"a\"}, {\"name\": \"b\"}]");

        assertEquals(2, items.size());
        assertTrue(items.get(0).contains("a"));
        assertTrue(items.get(1).contains("b"));
    }

    @Test
    void findArrayWithLoop() {

        var items = Json.findArray(
                "{\"loop\": [{\"name\": \"a\"}, {\"name\": \"b\"}], \"shared\": \"val\"}");

        assertEquals(2, items.size());
        assertTrue(items.get(0).contains("shared"));
        assertTrue(items.get(1).contains("shared"));
    }

    @Test
    void findArrayNoArray() {

        var items = Json.findArray("No array here");

        assertTrue(items.isEmpty());
    }

    @Test
    void findObjectEmptyObject() {

        var result = Json.findObject("{}", Map.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
