package ai.agentican.framework.tools.scratchpad;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScratchpadToolkitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void storeAndRecall() throws Exception {

        var pad = new ScratchpadToolkit();

        pad.execute("store", Map.of("id", "mykey", "description", "desc", "details", "content"));

        var result = pad.execute("recall", Map.of("id", "mykey"));

        var map = JSON.readValue(result, Map.class);

        assertEquals("mykey", map.get("key"));
        assertEquals("desc", map.get("description"));
        assertEquals("content", map.get("details"));
    }

    @Test
    void recallMissingKey() throws Exception {

        var pad = new ScratchpadToolkit();

        var result = pad.execute("recall", Map.of("id", "nonexistent"));

        var map = JSON.readValue(result, Map.class);

        assertTrue(map.containsKey("error"));
        assertTrue(map.get("error").toString().contains("nonexistent"));
    }

    @Test
    void recallAll() throws Exception {

        var pad = new ScratchpadToolkit();

        pad.execute("store", Map.of("id", "k1", "description", "d1", "details", "v1"));
        pad.execute("store", Map.of("id", "k2", "description", "d2", "details", "v2"));

        var result = pad.execute("recall_all", Map.of());

        var map = JSON.readValue(result, Map.class);

        var entries = (java.util.List<?>) map.get("entries");

        assertEquals(2, entries.size());
    }

    @Test
    void storeOverwritesExisting() throws Exception {

        var pad = new ScratchpadToolkit();

        pad.execute("store", Map.of("id", "x", "description", "d", "details", "v1"));
        pad.execute("store", Map.of("id", "x", "description", "d", "details", "v2"));

        var result = pad.execute("recall", Map.of("id", "x"));

        var map = JSON.readValue(result, Map.class);

        assertEquals("v2", map.get("details"));
    }

    @Test
    void unknownTool() throws Exception {

        var pad = new ScratchpadToolkit();

        var result = pad.execute("unknown_tool", Map.of());

        var map = JSON.readValue(result, Map.class);

        assertTrue(map.containsKey("error"));
        assertTrue(map.get("error").toString().contains("Unknown"));
    }
}
